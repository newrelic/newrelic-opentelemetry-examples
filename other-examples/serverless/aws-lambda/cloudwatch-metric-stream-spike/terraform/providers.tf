terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    newrelic = {
      source  = "newrelic/newrelic"
      version = "~> 3.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }
}

provider "aws" {
  profile = var.aws_profile
  region  = var.aws_region
}

# `region` is what redirects every NerdGraph-backed resource in this config
# (newrelic_cloud_aws_link_account, newrelic_api_access_key, etc.) to the
# right New Relic environment -- confirmed via newrelic-client-go's
# pkg/region/region_constants.go, which maps "Staging" to
# https://staging-api.newrelic.com/graphql (and "US"/"EU"/"JP"/"GOV"/
# "FEDRAMP" to their respective production endpoints). This is the same,
# officially-supported mechanism the provider itself validates against
# (see provider_newrelic.go's `region` schema field) -- not a hand-rolled
# override. See variables.tf's newrelic_region for how to point this at a
# different environment.
provider "newrelic" {
  account_id = var.newrelic_account_id
  api_key    = var.newrelic_user_api_key
  region     = var.newrelic_region
}
