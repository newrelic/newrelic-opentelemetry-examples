variable "aws_profile" {
  description = "AWS CLI profile to deploy into."
  type        = string
}

variable "aws_region" {
  description = "AWS region to deploy the metric stream resources into. Must match the region the monitored Lambda actually runs in -- CloudWatch Metric Streams are regional and can't see metrics from other regions."
  type        = string
  default     = "us-east-1"
}

variable "newrelic_account_id" {
  description = "New Relic staging account ID to link the AWS account to."
  type        = number
}

variable "newrelic_user_api_key" {
  description = "New Relic staging User API key (NerdGraph auth, x-api-key). From NEW_RELIC_USER_API_KEY."
  type        = string
  sensitive   = true
}

variable "newrelic_license_key" {
  description = "New Relic staging Ingest license key, used as the Firehose HTTP destination's access key. From NEW_RELIC_LICENSE_KEY."
  type        = string
  sensitive   = true
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
