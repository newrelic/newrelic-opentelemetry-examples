# Set by deploy.sh / teardown.sh from the environment variables documented in
# ../README.md -- you shouldn't normally need to set any TF_VAR_* yourself.

variable "aws_profile" {
  description = "AWS CLI profile to deploy into."
  type        = string
}

variable "aws_region" {
  description = "AWS region for everything. The Lambda function and the CloudWatch Metric Stream must share a region -- Metric Streams can't see metrics from other regions."
  type        = string
  default     = "us-east-1"
}

variable "name" {
  description = "Lambda function name, OTel service.name, and prefix/suffix for every other resource created here."
  type        = string
  default     = "otel-faas-spanmetrics-poc"
}

variable "newrelic_region" {
  description = "New Relic environment for both data paths: US, EU, JP, GOV, FEDRAMP or Staging, in any case. Normalized to the newrelic provider's exact `region` spelling in providers.tf."
  type        = string
  default     = "US"
  validation {
    condition     = contains(["us", "eu", "jp", "gov", "fedramp", "staging"], lower(var.newrelic_region))
    error_message = "newrelic_region must be one of: US, EU, JP, GOV, FEDRAMP, Staging (case-insensitive)."
  }
}

variable "newrelic_account_id" {
  description = "New Relic account ID to link the AWS account to."
  type        = number
}

variable "newrelic_user_api_key" {
  description = "New Relic User API key (NerdGraph), used to link the AWS account."
  type        = string
  sensitive   = true
}

variable "newrelic_license_key" {
  description = "New Relic ingest license key, used by both the collector's OTLP exporter and the Firehose HTTP destination."
  type        = string
  sensitive   = true
}

variable "newrelic_otlp_endpoint" {
  description = "Override for the collector's New Relic OTLP endpoint. Leave empty to use the default for newrelic_region (US, EU and Staging only)."
  type        = string
  default     = ""
}

variable "newrelic_metrics_ingest_url" {
  description = "Override for the Firehose destination's New Relic CloudWatch-metrics ingest URL. Leave empty to use the default for newrelic_region (US, EU, JP and Staging only)."
  type        = string
  default     = ""
}

variable "otel_layer_arn" {
  description = "AWSOpenTelemetryDistroJava Lambda layer ARN for aws_region. deploy.sh looks this up via ../scripts/get-otel-layer-arn.sh."
  type        = string
}

variable "function_zip" {
  description = "Path to the built Lambda deployment package (gradle buildZip)."
  type        = string
  default     = "../ExampleFunction/build/distributions/function.zip"
}

variable "trace_sampling_percentage" {
  description = "Percentage of spans the collector keeps as trace data. Does not affect faas.invocations/faas.invoke_duration -- see collector.yaml."
  type        = number
  default     = 10
}

variable "load_interval_seconds" {
  description = "Pause between load generator requests. Keep the resulting rate under ~1 request/second: above that, Active Tracing's default X-Ray sampling drops spans before they ever reach the collector, and faas.invocations undercounts."
  type        = number
  default     = 1
}
