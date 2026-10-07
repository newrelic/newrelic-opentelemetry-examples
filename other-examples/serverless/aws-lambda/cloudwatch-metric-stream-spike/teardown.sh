#!/usr/bin/env bash
# Tears down everything deploy.sh created: the Terraform-managed integration
# pipeline, then the Lambda/API Gateway stack.
set -euo pipefail
cd "$(dirname "$0")"

: "${AWS_PROFILE:?Set AWS_PROFILE to the AWS CLI profile that deployed this.}"
: "${TF_VAR_newrelic_account_id:?Set TF_VAR_newrelic_account_id to the account you deployed to.}"
: "${NEW_RELIC_USER_API_KEY:?Set NEW_RELIC_USER_API_KEY (NerdGraph user API key).}"
: "${NEW_RELIC_LICENSE_KEY:?Set NEW_RELIC_LICENSE_KEY (ingest license key).}"
REGION="${AWS_REGION:-us-east-1}"
STACK_NAME="${STACK_NAME:-nr-cloudwatch-metrics-spike}"

echo "==> Destroying the Terraform-managed integration pipeline"
(cd terraform && \
  TF_VAR_aws_profile="$AWS_PROFILE" \
  TF_VAR_newrelic_account_id="$TF_VAR_newrelic_account_id" \
  TF_VAR_newrelic_user_api_key="$NEW_RELIC_USER_API_KEY" \
  TF_VAR_newrelic_license_key="$NEW_RELIC_LICENSE_KEY" \
  terraform destroy -auto-approve -no-color)

echo "==> Deleting the Lambda/API Gateway stack ($STACK_NAME)"
aws --profile "$AWS_PROFILE" --region "$REGION" cloudformation delete-stack --stack-name "$STACK_NAME"
aws --profile "$AWS_PROFILE" --region "$REGION" cloudformation wait stack-delete-complete --stack-name "$STACK_NAME"

echo "==> Done."
