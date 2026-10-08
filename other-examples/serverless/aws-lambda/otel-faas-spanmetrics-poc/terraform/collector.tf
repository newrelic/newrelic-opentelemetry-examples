# One EC2 instance runs both the OTel Collector (the ADOT Lambda layer has no
# bundled collector, so the function needs a reachable one) and the load
# generator that calls the function until teardown.
#
# The collector accepts OTLP only from the function's security group: the
# function runs in the same default VPC and exports to the instance's private
# IP, so nothing is exposed to the internet.

locals {
  default_otlp_endpoints = {
    US      = "https://otlp.nr-data.net"
    EU      = "https://otlp.eu01.nr-data.net"
    Staging = "https://staging-otlp.nr-data.net:4318"
  }
  otlp_endpoint = (
    var.newrelic_otlp_endpoint != "" ?
    var.newrelic_otlp_endpoint :
    lookup(local.default_otlp_endpoints, local.newrelic_region, "")
  )
  # A plain string, not a reference to the parameter resource, so the
  # instance doesn't depend on the API (and through it, the function).
  api_url_parameter = "/${var.name}/api-url"
}

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
  filter {
    name   = "default-for-az"
    values = ["true"]
  }
}

data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

# Outbound only: dnf, image pull, New Relic export, and the load generator's
# calls to the public API URL.
resource "aws_security_group" "collector" {
  name        = "${var.name}-collector"
  description = "${var.name} collector and load generator"
  vpc_id      = data.aws_vpc.default.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# A separate rule resource rather than an inline ingress block, so the two
# security groups don't reference each other inline.
resource "aws_vpc_security_group_ingress_rule" "collector_otlp_from_lambda" {
  security_group_id            = aws_security_group.collector.id
  referenced_security_group_id = aws_security_group.lambda.id
  ip_protocol                  = "tcp"
  from_port                    = 4318
  to_port                      = 4318
  description                  = "OTLP/HTTP from the ${var.name} Lambda function"
}

# The function needs the collector's private IP, so the instance can't also
# take the function's API URL in its user data without a dependency cycle.
# Instead the load generator reads it from here at boot.
resource "aws_ssm_parameter" "api_url" {
  name  = local.api_url_parameter
  type  = "String"
  value = aws_apigatewayv2_stage.default.invoke_url

  # Published only once the API can actually invoke the function.
  depends_on = [aws_lambda_permission.api_gateway, aws_apigatewayv2_route.get_root]
}

# Lets you shell in with `aws ssm start-session` to read collector/load
# generator logs -- no SSH key or open port 22 needed.
resource "aws_iam_role" "collector" {
  name = "${var.name}-collector"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "collector_ssm" {
  role       = aws_iam_role.collector.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "collector_read_api_url" {
  name = "read-api-url"
  role = aws_iam_role.collector.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "ssm:GetParameter"
      Resource = aws_ssm_parameter.api_url.arn
    }]
  })
}

resource "aws_iam_instance_profile" "collector" {
  name = "${var.name}-collector"
  role = aws_iam_role.collector.name
}

resource "aws_instance" "collector" {
  ami                         = data.aws_ssm_parameter.al2023_ami.value
  instance_type               = "t3.small"
  subnet_id                   = data.aws_subnets.default.ids[0]
  vpc_security_group_ids      = [aws_security_group.collector.id]
  iam_instance_profile        = aws_iam_instance_profile.collector.name
  associate_public_ip_address = true

  # The license key ends up in user data, readable by anyone with
  # ec2:DescribeInstanceAttribute in the account. Acceptable for a
  # short-lived POC; use SSM Parameter Store or Secrets Manager otherwise.
  user_data = templatefile("${path.module}/user-data.sh.tftpl", {
    collector_config          = file("${path.module}/collector.yaml")
    newrelic_license_key      = var.newrelic_license_key
    newrelic_otlp_endpoint    = local.otlp_endpoint
    trace_sampling_percentage = var.trace_sampling_percentage
    api_url_parameter         = local.api_url_parameter
    aws_region                = var.aws_region
    load_interval_seconds     = var.load_interval_seconds
  })
  user_data_replace_on_change = true

  tags = {
    Name = "${var.name}-collector"
  }

  lifecycle {
    precondition {
      condition     = local.otlp_endpoint != ""
      error_message = "No default OTLP endpoint for newrelic_region ${local.newrelic_region}; set NEW_RELIC_OTLP_ENDPOINT."
    }
    precondition {
      condition     = length(data.aws_subnets.default.ids) > 0
      error_message = "No default VPC/subnet found in ${var.aws_region}; this example assumes one exists."
    }
  }
}
