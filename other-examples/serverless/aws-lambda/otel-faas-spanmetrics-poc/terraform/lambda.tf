# The OTel-instrumented Java function (ADOT Java layer) behind an HTTP API.
# Its environment carries the fixes from java-faas-metrics's
# "Real-deployment findings": Active tracing, and the traces-specific OTLP
# endpoint variable.
#
# It runs in the default VPC only so it can reach the collector's private IP.
# It needs no other network access (no NAT), since it calls nothing else.

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

# Required by `tracing_config { mode = "Active" }`.
resource "aws_iam_role_policy_attachment" "lambda_xray" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/AWSXRayDaemonWriteAccess"
}

resource "aws_security_group" "lambda" {
  name        = "${var.name}-lambda"
  description = "${var.name} Lambda function"
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
# teardown removes it too.
resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${var.name}"
  retention_in_days = 1
}

resource "aws_lambda_function" "this" {
  function_name = var.name
  role          = aws_iam_role.lambda.arn
  handler       = "example.App::handleRequest"
  runtime       = "java21"
  memory_size   = 512
  timeout       = 30
  layers        = [var.otel_layer_arn]
  filename      = var.function_zip
  # Guarded so teardown.sh doesn't need a built zip just to destroy.
  source_code_hash = fileexists(var.function_zip) ? filebase64sha256(var.function_zip) : null

  vpc_config {
    subnet_ids         = data.aws_subnets.default.ids
    security_group_ids = [aws_security_group.lambda.id]
  }

  # Without Active tracing, Lambda hands the OTel SDK an X-Ray parent context
  # with Sampled=0 and the default ParentBased sampler drops every span.
  tracing_config {
    mode = "Active"
  }

  environment {
    variables = {
      AWS_LAMBDA_EXEC_WRAPPER              = "/opt/otel-instrument"
      OTEL_AWS_APPLICATION_SIGNALS_ENABLED = "false"
      OTEL_SERVICE_NAME                    = var.name
      OTEL_EXPORTER_OTLP_ENDPOINT          = "http://${aws_instance.collector.private_ip}:4318"
      # The ADOT layer ignores the generic endpoint above for traces and
      # silently falls back to X-Ray without this one.
      OTEL_EXPORTER_OTLP_TRACES_ENDPOINT = "http://${aws_instance.collector.private_ip}:4318/v1/traces"
    }
  }

  depends_on = [
    aws_iam_role_policy_attachment.lambda_vpc,
    aws_iam_role_policy_attachment.lambda_xray,
    aws_cloudwatch_log_group.lambda,
  ]
}

# Payload format 1.0 matches the APIGatewayProxyRequestEvent the handler takes.
resource "aws_apigatewayv2_api" "this" {
  name          = var.name
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_integration" "lambda" {
  api_id                 = aws_apigatewayv2_api.this.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.this.invoke_arn
  payload_format_version = "1.0"
}

resource "aws_apigatewayv2_route" "get_root" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "GET /"
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.this.id
  name        = "$default"
  auto_deploy = true
}

resource "aws_lambda_permission" "api_gateway" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.this.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.this.execution_arn}/*/*"
}
