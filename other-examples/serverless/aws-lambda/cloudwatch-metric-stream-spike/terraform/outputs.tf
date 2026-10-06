output "linked_account_id" {
  value = newrelic_cloud_aws_link_account.newrelic_cloud_integration_push.id
}

output "metric_stream_arn" {
  value = aws_cloudwatch_metric_stream.newrelic_metric_stream.arn
}

output "firehose_delivery_stream_arn" {
  value = aws_kinesis_firehose_delivery_stream.newrelic_firehose_stream.arn
}
