variable "aws_profile" {
  description = "AWS CLI profile to deploy into."
  type        = string
}

variable "aws_region" {
  description = "AWS region to deploy the metric stream resources into. Must match the region the monitored Lambda actually runs in -- CloudWatch Metric Streams are regional and can't see metrics from other regions."
  type        = string
  default     = "us-east-1"
}

variable "newrelic_region" {
  description = "New Relic environment to link the AWS account to and send metrics to. Passed straight through to the newrelic provider's own `region` argument -- valid values are its exact set, confirmed via newrelic-client-go's pkg/region/region_constants.go: \"US\", \"EU\", \"JP\", \"GOV\", \"FEDRAMP\", or \"Staging\" (New Relic-internal; what this directory was originally built against). Defaults to \"Staging\" to match that original, still-primary use case -- set to \"US\"/\"EU\"/\"JP\" to point at a real production account instead."
  type        = string
  default     = "Staging"
  validation {
    condition     = contains(["US", "EU", "JP", "GOV", "FEDRAMP", "Staging"], var.newrelic_region)
    error_message = "newrelic_region must be one of: US, EU, JP, GOV, FEDRAMP, Staging (the newrelic provider's own valid `region` values)."
  }
}

variable "newrelic_account_id" {
  description = "New Relic account ID (in whichever environment newrelic_region selects) to link the AWS account to."
  type        = number
}

variable "newrelic_user_api_key" {
  description = "New Relic User API key (NerdGraph auth, x-api-key) for that account. From NEW_RELIC_USER_API_KEY."
  type        = string
  sensitive   = true
}

variable "newrelic_license_key" {
  description = "New Relic Ingest license key for that account, used as the Firehose HTTP destination's access key. From NEW_RELIC_API_KEY."
  type        = string
  sensitive   = true
}

variable "newrelic_metrics_ingest_url" {
  description = "Override for the Firehose HTTP destination's New Relic CloudWatch-metrics ingest URL. Leave unset to use the built-in default for newrelic_region: US/EU/JP defaults are confirmed via New Relic's manual AWS-integration docs (docs.newrelic.com/.../aws-integration-for-metrics/manual), Staging's via an internal Slack thread (see ../README.md). Required if newrelic_region is GOV or FEDRAMP -- no default is confirmed for those here."
  type        = string
  default     = ""
}

variable "name" {
  description = "Suffix applied to created resource names, to keep this spike's resources identifiable. Kept short -- some generated IAM role names have a 64-char limit."
  type        = string
  default     = "cw-stream-spike"
}

variable "output_format" {
  description = "CloudWatch Metric Stream output format."
  type        = string
  default     = "opentelemetry1.0"
}
