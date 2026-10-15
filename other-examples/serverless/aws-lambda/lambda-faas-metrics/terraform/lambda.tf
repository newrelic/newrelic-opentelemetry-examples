# Two copies of the same Java function, instrumented with the ADOT Java layer,
# behind one HTTP API. They differ only in how their FaaS metrics are made:
#
#   telemetry-api  A second layer, the OpenTelemetry Lambda collector, runs as
#                  an extension and records faas.* metrics from Lambda's
#                  Telemetry API. See lambda-collector.yaml.
#   spanmetrics    No second layer. The EC2 collector derives faas.* metrics
#                  from this function's spans with the spanmetrics connector.
#                  See collector.yaml.
#
# Both export every span (OTEL_TRACES_SAMPLER=always_on). The spanmetrics
# connector needs every invocation span to count invocations, and sampling for
# trace storage happens later, in the EC2 collector.
#
# They run in the default VPC only so they can reach the collector's private
# IP. They need no other network access (no NAT), since they call nothing else.

data "aws_caller_identity" "current" {}

locals {
  collector_url = "http://${aws_instance.collector.private_ip}:4318"

  functions = {
    telemetry-api = {
      layers = [var.otel_layer_arn, var.collector_layer_arn]
      # Read by the collector layer. The env: provider loads the whole config
      # from OTEL_LAMBDA_COLLECTOR_CONFIG, and the config itself reads the
      # other two variables.
      environment = {
        OPENTELEMETRY_COLLECTOR_CONFIG_URI = "env:OTEL_LAMBDA_COLLECTOR_CONFIG"
        OTEL_LAMBDA_COLLECTOR_CONFIG       = file("${path.module}/lambda-collector.yaml")
        OTEL_COLLECTOR_GATEWAY_ENDPOINT    = local.collector_url
        # Built rather than read from the function's own arn attribute, which
        # its environment can't reference.
        FUNCTION_ARN = "arn:aws:lambda:${var.aws_region}:${data.aws_caller_identity.current.account_id}:function:${var.name}-telemetry-api"
      }
    }
    spanmetrics = {
      layers      = [var.otel_layer_arn]
      environment = {}
    }
  }
}

resource "aws_iam_role" "lambda" {
  name = "${var.name}-lambda"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })
}

# CloudWatch Logs plus the ENI permissions a VPC-attached function needs.
resource "aws_iam_role_policy_attachment" "lambda_vpc" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

resource "aws_security_group" "lambda" {
  name        = "${var.name}-lambda"
  description = "${var.name} Lambda functions"
  vpc_id      = data.aws_vpc.default.id

  egress {
    description     = "OTLP/HTTP to the collector"
    from_port       = 4318
    to_port         = 4318
    protocol        = "tcp"
    security_groups = [aws_security_group.collector.id]
  }
}

# Managed here (rather than left for Lambda to create on first invocation) so
# teardown removes them too.
resource "aws_cloudwatch_log_group" "lambda" {
  for_each          = local.functions
  name              = "/aws/lambda/${var.name}-${each.key}"
  retention_in_days = 1
}

resource "aws_lambda_function" "this" {
  for_each = local.functions

  function_name = "${var.name}-${each.key}"
  role          = aws_iam_role.lambda.arn
  handler       = "example.App::handleRequest"
  runtime       = "java21"
  memory_size   = 512
  timeout       = 30
  # Order doesn't matter: the ADOT layer supplies the Java agent and the
  # /opt/otel-instrument wrapper, the collector layer supplies an extension.
  layers   = each.value.layers
  filename = var.function_zip
  # Guarded so teardown.sh doesn't need a built zip just to destroy.
  source_code_hash = fileexists(var.function_zip) ? filebase64sha256(var.function_zip) : null

  vpc_config {
    subnet_ids         = data.aws_subnets.default.ids
    security_group_ids = [aws_security_group.lambda.id]
  }

  environment {
    variables = merge({
      AWS_LAMBDA_EXEC_WRAPPER              = "/opt/otel-instrument"
      OTEL_AWS_APPLICATION_SIGNALS_ENABLED = "false"
      OTEL_SERVICE_NAME                    = "${var.name}-${each.key}"
      # Ignore Lambda's X-Ray sampling decision and export every span. With
      # the default parent-based sampler, invocations X-Ray doesn't sample
      # produce no spans at all.
      OTEL_TRACES_SAMPLER         = "always_on"
      OTEL_EXPORTER_OTLP_ENDPOINT = local.collector_url
      # The ADOT layer ignores the generic endpoint above for traces and
      # silently falls back to X-Ray without this one.
      OTEL_EXPORTER_OTLP_TRACES_ENDPOINT = "${local.collector_url}/v1/traces"
    }, each.value.environment)
  }

  depends_on = [
    aws_iam_role_policy_attachment.lambda_vpc,
    aws_cloudwatch_log_group.lambda,
  ]
}

# One API, one route per function. Payload format 1.0 matches the
# APIGatewayProxyRequestEvent the handler takes.
resource "aws_apigatewayv2_api" "this" {
  name          = var.name
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_integration" "lambda" {
  for_each               = local.functions
  api_id                 = aws_apigatewayv2_api.this.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.this[each.key].invoke_arn
  payload_format_version = "1.0"
}

resource "aws_apigatewayv2_route" "this" {
  for_each  = local.functions
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "GET /${each.key}"
  target    = "integrations/${aws_apigatewayv2_integration.lambda[each.key].id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.this.id
  name        = "$default"
  auto_deploy = true
}

resource "aws_lambda_permission" "api_gateway" {
  for_each      = local.functions
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.this[each.key].function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.this.execution_arn}/*/*"
}
