#!/usr/bin/env bash
# One-command deploy: the plain Lambda function (via SAM) and the CloudWatch
# Metric Stream -> Kinesis Firehose -> New Relic pipeline (via Terraform),
# then a handful of real requests so there's immediately something to query.
#
# See README.md's Configuration section for what each required variable is.
set -euo pipefail
cd "$(dirname "$0")"

: "${AWS_PROFILE:?Set AWS_PROFILE to the AWS CLI profile for the account you are deploying into.}"
: "${TF_VAR_newrelic_account_id:?Set TF_VAR_newrelic_account_id to your New Relic staging account ID.}"
: "${NEW_RELIC_USER_API_KEY:?Set NEW_RELIC_USER_API_KEY (NerdGraph user API key).}"
: "${NEW_RELIC_API_KEY:?Set NEW_RELIC_API_KEY (ingest license key).}"
REGION="${AWS_REGION:-us-east-1}"
STACK_NAME="${STACK_NAME:-nr-cloudwatch-metrics-spike}"

aws() { command aws --profile "$AWS_PROFILE" --region "$REGION" "$@"; }

echo "==> Deploying the Lambda function + API Gateway (stack: $STACK_NAME)"
sam build
sam deploy --profile "$AWS_PROFILE" --region "$REGION" --stack-name "$STACK_NAME" \
  --resolve-s3 --capabilities CAPABILITY_IAM \
  --no-confirm-changeset --no-fail-on-empty-changeset

API_ENDPOINT="$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" \
  --query 'Stacks[0].Outputs[?OutputKey==`apiEndpoint`].OutputValue' --output text)"

echo "==> Standing up the CloudWatch Metric Stream -> Firehose -> New Relic pipeline"
(cd terraform && terraform init -input=false >/dev/null && ./run-plan.sh && ./run-apply.sh)

echo "==> Sending a few requests so there's something to see immediately"
for i in 1 2 3 4 5; do
  curl -s -o /dev/null "${API_ENDPOINT}?sleepMs=$(( (RANDOM % 791) + 10 ))"
done

echo ""
echo "==> Deployed."
echo "    API endpoint: $API_ENDPOINT"
echo ""
echo "CloudWatch only publishes a metric for an invocation after it happens, so"
echo "wait a few minutes (CloudWatch + Metric Streams latency), then check New"
echo "Relic with the fingerprint queries from README.md's Findings section -"
echo "e.g.:"
echo ""
echo "    FROM Metric SELECT count(*) WHERE metricName = 'aws.lambda.Invocations.byFunction' SINCE 30 minutes ago"
echo "    FROM ServerlessSample SELECT count(*) WHERE provider = 'LambdaFunction' SINCE 30 minutes ago"
echo ""
echo "When you're done, run ./teardown.sh"
