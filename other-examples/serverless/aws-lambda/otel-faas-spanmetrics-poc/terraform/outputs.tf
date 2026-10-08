output "api_url" {
  value = aws_apigatewayv2_stage.default.invoke_url
}

output "collector_endpoint" {
  value = "http://${aws_instance.collector.private_ip}:4318"
}

output "collector_instance_id" {
  value = aws_instance.collector.id
}

output "function_name" {
  value = aws_lambda_function.this.function_name
}

output "firehose_delivery_stream_name" {
  value = aws_kinesis_firehose_delivery_stream.newrelic.name
}
