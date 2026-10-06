# Adapted from newrelic/terraform-provider-newrelic's
# examples/modules/cloud-integrations/aws module, trimmed to PUSH/metric-streams
# only (no API polling, no auto-discovery/config-recorder -- matching the
# choices made in the NR wizard before it failed on the staging key), and
# retargeted at New Relic staging via providers.tf's nerdgraph_api_url plus
# the staging metrics ingest URL below.

data "aws_iam_policy_document" "newrelic_assume_policy" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type = "AWS"
      # New Relic's AWS account for assuming this role. Confirmed for
      # production via the public terraform-provider-newrelic example
      # module. NOT independently confirmed for staging -- an internal
      # Slack thread implied staging reuses the same AWS-side trust
      # relationship as prod (only the NerdGraph linking *method* is
      # unsupported for staging, not a different trust principal), but
      # if the link-account step fails on a trust/assume-role error
      # specifically, this is the first thing to re-check.
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
  description        = "New Relic Cloud integration role (staging spike)"
  assume_role_policy = data.aws_iam_policy_document.newrelic_assume_policy.json
}

resource "aws_iam_policy" "newrelic_aws_permissions" {
  name        = "NewRelicCloudStreamReadPermissions-${var.name}"
  description = "Read-only permissions for New Relic AWS integration metadata/health checks"
  policy      = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "budgets:ViewBudget",
        "tag:GetResources"
      ],
      "Effect": "Allow",
      "Resource": "*"
    }
  ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "newrelic_aws_policy_attach" {
  role       = aws_iam_role.newrelic_aws_role.name
  policy_arn = aws_iam_policy.newrelic_aws_permissions.arn
}

# PULL/API-Polling was intentionally run here for a while (ReadOnlyAccess
# attachment + a PULL link_account + `lambda {}` integration block) purely
# to compare, empirically, which NRDB data model each integration method
# populates for the same Lambda function. That comparison is done -- see
# README's "Metric Streams alone" finding -- so polling is removed below
# to confirm the legacy Lambda UI lights up from Metric Streams alone, via
# NR's own ServerlessSample->Metric data-mapping shim (dirac-nrql), with no
# polling-sourced ServerlessSample events in the picture at all.

# The account-link call itself -- this is the step the CloudFormation
# template's GraphqlAPICallFunction Lambda could not do against staging
# (hardcoded to prod/EU/JP). Same resource type as the standard prod module;
# only the provider's nerdgraph_api_url makes this target staging.
resource "newrelic_cloud_aws_link_account" "newrelic_cloud_integration_push" {
  account_id             = var.newrelic_account_id
  arn                    = aws_iam_role.newrelic_aws_role.arn
  metric_collection_mode = "PUSH"
  name                   = "${var.name} metric stream"
  depends_on             = [aws_iam_role_policy_attachment.newrelic_aws_policy_attach]
}

resource "random_string" "s3_bucket_suffix" {
  length  = 8
  special = false
  upper   = false
}

resource "aws_s3_bucket" "newrelic_firehose_backup" {
  bucket        = "newrelic-firehose-backup-${random_string.s3_bucket_suffix.id}"
  force_destroy = true
}

resource "aws_s3_bucket_ownership_controls" "newrelic_firehose_backup" {
  bucket = aws_s3_bucket.newrelic_firehose_backup.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_iam_role" "firehose_newrelic_role" {
  name = "firehose_newrelic_role_${var.name}"

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": "firehose.amazonaws.com"
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

# Confirmed via an internal Slack thread showing another engineer's real,
# working staging Firehose destination config pointed at this exact URL.
locals {
  newrelic_staging_metrics_url = "https://staging-aws-api.newrelic.com/cloudwatch-metrics/v1"
}

resource "aws_kinesis_firehose_delivery_stream" "newrelic_firehose_stream" {
  name        = "newrelic_firehose_stream_${var.name}"
  destination = "http_endpoint"
  http_endpoint_configuration {
    url    = local.newrelic_staging_metrics_url
    name   = "New Relic Staging - ${var.name}"
    # Reuses the staging Ingest license key already in this environment
    # (NEW_RELIC_LICENSE_KEY), rather than minting a new one via the
    # newrelic_api_access_key resource -- one fewer NerdGraph-dependent
    # resource to worry about getting right against staging.
    access_key         = var.newrelic_license_key
    buffering_size     = 1
    buffering_interval = 60
    role_arn           = aws_iam_role.firehose_newrelic_role.arn
    s3_backup_mode     = "FailedDataOnly"
    s3_configuration {
      role_arn           = aws_iam_role.firehose_newrelic_role.arn
      bucket_arn         = aws_s3_bucket.newrelic_firehose_backup.arn
      buffering_size     = 10
      buffering_interval = 400
      compression_format = "GZIP"
    }
    request_configuration {
      content_encoding = "GZIP"
    }
  }
}

resource "aws_iam_role" "metric_stream_to_firehose" {
  name = "newrelic_metric_stream_to_firehose_role_${var.name}"

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": "streams.metrics.cloudwatch.amazonaws.com"
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

resource "aws_iam_role_policy" "metric_stream_to_firehose" {
  name = "default"
  role = aws_iam_role.metric_stream_to_firehose.id

  policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "firehose:PutRecord",
                "firehose:PutRecordBatch"
            ],
            "Resource": "${aws_kinesis_firehose_delivery_stream.newrelic_firehose_stream.arn}"
        }
    ]
}
EOF
}

resource "aws_cloudwatch_metric_stream" "newrelic_metric_stream" {
  name          = "newrelic-metric-stream-${var.name}"
  role_arn      = aws_iam_role.metric_stream_to_firehose.arn
  firehose_arn  = aws_kinesis_firehose_delivery_stream.newrelic_firehose_stream.arn
  output_format = var.output_format

  include_filter {
    namespace    = "AWS/Lambda"
    metric_names = []
  }
}
