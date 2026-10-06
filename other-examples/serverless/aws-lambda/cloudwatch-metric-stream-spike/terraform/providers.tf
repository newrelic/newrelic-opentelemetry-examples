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

# New Relic staging has no customer-facing "region" of its own -- `region`
# below is just a required placeholder for provider schema validation.
# `nerdgraph_api_url` is what actually redirects every NerdGraph-backed
# resource in this config (newrelic_cloud_aws_link_account,
# newrelic_api_access_key, etc.) to New Relic's staging environment instead
# of production. Confirmed against an internal NerdGraph auth doc -- this is
# not a guess. See ../README.md for sourcing.
provider "newrelic" {
  account_id        = var.newrelic_account_id
  api_key           = var.newrelic_user_api_key
  region            = "US"
  nerdgraph_api_url = "https://staging-api.newrelic.com/graphql"
}
