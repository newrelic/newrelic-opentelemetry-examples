# CloudWatch Metric Stream -> Kinesis Firehose -> New Relic for AWS/Lambda
# metrics, with the AWS account linked to New Relic in PUSH mode only (no API
# polling). Carried over from cloudwatch-metric-stream-spike/terraform,
# which documents where each endpoint and the trust principal come from.

locals {
  default_metrics_ingest_urls = {
    US      = "https://aws-api.newrelic.com/cloudwatch-metrics/v1"
    EU      = "https://aws-api.eu01.nr-data.net/cloudwatch-metrics/v1"
    JP      = "https://aws-api.jp.nr-data.net/cloudwatch-metrics/v1"
    Staging = "https://staging-aws-api.newrelic.com/cloudwatch-metrics/v1"
  }
  metrics_ingest_url = (
    var.newrelic_metrics_ingest_url != "" ?
    var.newrelic_metrics_ingest_url :
    lookup(local.default_metrics_ingest_urls, local.newrelic_region, "")
  )
}

data "aws_iam_policy_document" "newrelic_assume_policy" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type = "AWS"
      # New Relic's AWS account for assuming this role. Confirmed for
      # production; only implied (not confirmed) for staging -- re-check this
      # first if the account link fails on a trust/assume-role error.
      identifiers = ["754728514883"]
    }

    condition {
      test     = "StringEquals"
      variable = "sts:ExternalId"
      values   = [var.newrelic_account_id]
    }
  }
}

resource "aws_iam_role" "newrelic_aws_role" {
  name               = "NewRelicInfrastructure-Integrations-${var.name}"
  description        = "New Relic Cloud integration role"
  assume_role_policy = data.aws_iam_policy_document.newrelic_assume_policy.json
}

resource "aws_iam_role_policy" "newrelic_aws_permissions" {
  name = "NewRelicCloudStreamReadPermissions"
  role = aws_iam_role.newrelic_aws_role.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["budgets:ViewBudget", "tag:GetResources"]
      Resource = "*"
    }]
  })
}

resource "newrelic_cloud_aws_link_account" "push" {
  account_id             = var.newrelic_account_id
  arn                    = aws_iam_role.newrelic_aws_role.arn
  metric_collection_mode = "PUSH"
  name                   = "${var.name} metric stream"
  depends_on             = [aws_iam_role_policy.newrelic_aws_permissions]
}

resource "random_string" "s3_bucket_suffix" {
  length  = 8
  special = false
  upper   = false
}

resource "aws_s3_bucket" "firehose_backup" {
  bucket        = "${var.name}-firehose-backup-${random_string.s3_bucket_suffix.id}"
  force_destroy = true
}

resource "aws_s3_bucket_ownership_controls" "firehose_backup" {
  bucket = aws_s3_bucket.firehose_backup.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_iam_role" "firehose" {
  name = "${var.name}-firehose"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "firehose.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "firehose_s3_backup" {
  name = "s3-backup"
  role = aws_iam_role.firehose.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "s3:AbortMultipartUpload",
        "s3:GetBucketLocation",
        "s3:GetObject",
        "s3:ListBucket",
        "s3:ListBucketMultipartUploads",
        "s3:PutObject",
      ]
      Resource = [aws_s3_bucket.firehose_backup.arn, "${aws_s3_bucket.firehose_backup.arn}/*"]
    }]
  })
}

resource "aws_kinesis_firehose_delivery_stream" "newrelic" {
  name        = "${var.name}-newrelic"
  destination = "http_endpoint"

  http_endpoint_configuration {
    url                = local.metrics_ingest_url
    name               = "New Relic (${local.newrelic_region}) - ${var.name}"
    access_key         = var.newrelic_license_key
    buffering_size     = 1
    buffering_interval = 60
    role_arn           = aws_iam_role.firehose.arn
    s3_backup_mode     = "FailedDataOnly"

    s3_configuration {
      role_arn           = aws_iam_role.firehose.arn
      bucket_arn         = aws_s3_bucket.firehose_backup.arn
      buffering_size     = 10
      buffering_interval = 400
      compression_format = "GZIP"
    }

    request_configuration {
      content_encoding = "GZIP"
    }
  }

  lifecycle {
    precondition {
      condition     = local.metrics_ingest_url != ""
      error_message = "No default CloudWatch-metrics ingest URL for newrelic_region ${local.newrelic_region}; set NEW_RELIC_METRICS_INGEST_URL."
    }
  }
}

resource "aws_iam_role" "metric_stream" {
  name = "${var.name}-metric-stream"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "streams.metrics.cloudwatch.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "metric_stream_to_firehose" {
  name = "firehose-put"
  role = aws_iam_role.metric_stream.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["firehose:PutRecord", "firehose:PutRecordBatch"]
      Resource = aws_kinesis_firehose_delivery_stream.newrelic.arn
    }]
  })
}

# Streams every AWS/Lambda metric in the region, not just this function's --
# Metric Stream filters select by namespace and metric name, not dimension.
resource "aws_cloudwatch_metric_stream" "newrelic" {
  name          = "${var.name}-newrelic"
  role_arn      = aws_iam_role.metric_stream.arn
  firehose_arn  = aws_kinesis_firehose_delivery_stream.newrelic.arn
  output_format = "opentelemetry1.0"

  include_filter {
    namespace    = "AWS/Lambda"
    metric_names = []
  }
}
