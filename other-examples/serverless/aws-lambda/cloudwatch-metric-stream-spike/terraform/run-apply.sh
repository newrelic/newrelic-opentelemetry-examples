#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${AWS_PROFILE:?Set AWS_PROFILE to the AWS CLI profile for the account you're deploying into.}"
: "${TF_VAR_newrelic_account_id:?Set TF_VAR_newrelic_account_id to your New Relic staging account ID.}"
: "${NEW_RELIC_USER_API_KEY:?Set NEW_RELIC_USER_API_KEY (NerdGraph user API key).}"
: "${NEW_RELIC_LICENSE_KEY:?Set NEW_RELIC_LICENSE_KEY (ingest license key).}"
TF_VAR_newrelic_user_api_key="$NEW_RELIC_USER_API_KEY" \
TF_VAR_newrelic_license_key="$NEW_RELIC_LICENSE_KEY" \
terraform apply -auto-approve -no-color
