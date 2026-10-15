terraform {
  required_version = ">= 1.3"
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

  default_tags {
    tags = {
      Example = var.name
    }
  }
}

locals {
  # The provider's `region` argument only accepts these exact spellings.
  newrelic_region = {
    us      = "US"
    eu      = "EU"
    jp      = "JP"
    gov     = "GOV"
    fedramp = "FEDRAMP"
    staging = "Staging"
  }[lower(var.newrelic_region)]
}

# `region` redirects every NerdGraph-backed resource (here, the AWS account
# link) to the selected New Relic environment, including "Staging" -- see
# newrelic-client-go's pkg/region/region_constants.go.
provider "newrelic" {
  account_id = var.newrelic_account_id
  api_key    = var.newrelic_user_api_key
  region     = local.newrelic_region
}
