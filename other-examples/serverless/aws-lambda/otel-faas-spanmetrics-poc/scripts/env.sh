# Sourced by deploy.sh and teardown.sh: checks the environment variables
# documented in README.md's Configuration section and maps them onto the
# Terraform variables in terraform/variables.tf.

: "${AWS_PROFILE:?Set AWS_PROFILE to the AWS CLI profile for the account to deploy into.}"
: "${NEW_RELIC_ACCOUNT_ID:?Set NEW_RELIC_ACCOUNT_ID to your New Relic account ID.}"
: "${NEW_RELIC_USER_API_KEY:?Set NEW_RELIC_USER_API_KEY (NerdGraph user API key).}"
: "${NEW_RELIC_API_KEY:?Set NEW_RELIC_API_KEY (ingest license key).}"

export AWS_REGION="${AWS_REGION:-us-east-1}"
export TF_VAR_aws_profile="$AWS_PROFILE"
export TF_VAR_aws_region="$AWS_REGION"
export TF_VAR_newrelic_account_id="$NEW_RELIC_ACCOUNT_ID"
export TF_VAR_newrelic_user_api_key="$NEW_RELIC_USER_API_KEY"
export TF_VAR_newrelic_license_key="$NEW_RELIC_API_KEY"

# Optional: left unset, Terraform's own defaults apply.
if [ -n "${NEW_RELIC_REGION:-}" ]; then export TF_VAR_newrelic_region="$NEW_RELIC_REGION"; fi
if [ -n "${NEW_RELIC_OTLP_ENDPOINT:-}" ]; then export TF_VAR_newrelic_otlp_endpoint="$NEW_RELIC_OTLP_ENDPOINT"; fi
if [ -n "${NEW_RELIC_METRICS_INGEST_URL:-}" ]; then export TF_VAR_newrelic_metrics_ingest_url="$NEW_RELIC_METRICS_INGEST_URL"; fi
if [ -n "${TRACE_SAMPLING_PERCENTAGE:-}" ]; then export TF_VAR_trace_sampling_percentage="$TRACE_SAMPLING_PERCENTAGE"; fi
if [ -n "${LOAD_INTERVAL_SECONDS:-}" ]; then export TF_VAR_load_interval_seconds="$LOAD_INTERVAL_SECONDS"; fi

aws() { command aws --profile "$AWS_PROFILE" --region "$AWS_REGION" "$@"; }
