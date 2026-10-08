#!/usr/bin/env bash
# One-command deploy: the plain Lambda function (via SAM) and the CloudWatch
# Metric Stream -> Kinesis Firehose -> New Relic pipeline (via Terraform),
# then real requests so there's something to query. Actively probes for the
# Metric Stream to start forwarding data before sending the real traffic
# batch -- see the warm-up section below for why a fixed sleep isn't enough.
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
REQUEST_COUNT="${REQUEST_COUNT:-20}"
# How long to wait for the Metric Stream to prove it's forwarding data
# before giving up. There's no documented AWS SLA for Metric Stream warm-up
# time; ~10 minutes was observed once during development.
WARMUP_TIMEOUT_SECONDS="${WARMUP_TIMEOUT_SECONDS:-900}"
WARMUP_PROBE_INTERVAL_SECONDS=60

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
FIREHOSE_NAME="$(cd terraform && terraform output -raw firehose_delivery_stream_name)"

# A freshly-created CloudWatch Metric Stream doesn't start forwarding data
# immediately, and -- unlike a backlog -- it never backfills what it missed
# while warming up. Sending real traffic right after `terraform apply`
# returns races that warm-up: traffic sent too early is silently never seen
# by New Relic, with no error anywhere. This was observed directly: a first
# batch of requests landed in exactly that dead window and never showed up.
# Rather than guess a fixed delay, probe for it: send one throwaway request
# per minute and watch the Firehose stream's own IncomingRecords metric --
# once that's non-zero, the pipeline is demonstrably forwarding data.
echo "==> Waiting for the Metric Stream to start forwarding data (probing every ${WARMUP_PROBE_INTERVAL_SECONDS}s,"
echo "    up to ${WARMUP_TIMEOUT_SECONDS}s) before sending the real traffic batch"
elapsed=0
warm=""
while [ "$elapsed" -lt "$WARMUP_TIMEOUT_SECONDS" ]; do
  curl -s -o /dev/null "${API_ENDPOINT}?sleepMs=10" || true
  sleep "$WARMUP_PROBE_INTERVAL_SECONDS"
  elapsed=$((elapsed + WARMUP_PROBE_INTERVAL_SECONDS))
  end_time="$(date -u +%Y-%m-%dT%H:%M:%S)"
  start_time="$(date -u -v-130S +%Y-%m-%dT%H:%M:%S 2>/dev/null || date -u -d '130 seconds ago' +%Y-%m-%dT%H:%M:%S)"
  nonzero_datapoints="$(aws cloudwatch get-metric-statistics \
    --namespace AWS/Firehose --metric-name IncomingRecords \
    --dimensions "Name=DeliveryStreamName,Value=${FIREHOSE_NAME}" \
    --start-time "$start_time" --end-time "$end_time" --period 60 --statistics Sum \
    --query 'length(Datapoints[?Sum>`0`])' --output text 2>/dev/null || echo 0)"
  if [ "$nonzero_datapoints" != "0" ]; then
    warm=1
    break
  fi
  echo "    still warming up (${elapsed}s elapsed, no records forwarded yet)..."
done
if [ -z "$warm" ]; then
  echo "The Metric Stream never forwarded any data within ${WARMUP_TIMEOUT_SECONDS}s." >&2
  echo "That's past the slowest warm-up observed during development -- this is likely" >&2
  echo "an actual misconfiguration, not just slow warm-up. Check terraform/ and the" >&2
  echo "Firehose destination in the AWS console before retrying." >&2
  exit 1
fi
echo "==> Confirmed: the Metric Stream is forwarding data"

echo "==> Sending $REQUEST_COUNT requests so there's something to see"
echo "elapsed_ms,sleep_ms,status_code" > traffic-results.csv
for i in $(seq 1 "$REQUEST_COUNT"); do
  sleep_ms=$(( (RANDOM % 791) + 10 ))
  result="$(curl -s -o /dev/null -w '%{time_total},%{http_code}' "${API_ENDPOINT}?sleepMs=${sleep_ms}" || echo "0,000")"
  elapsed_ms="$(awk -v s="${result%,*}" 'BEGIN { printf "%.0f", s * 1000 }')"
  status_code="${result#*,}"
  echo "${elapsed_ms},${sleep_ms},${status_code}" >> traffic-results.csv
  sleep 2
done

failures="$(awk -F, 'NR>1 && $3!=200' traffic-results.csv | wc -l | tr -d ' ')"
if [ "$failures" != "0" ]; then
  echo "WARNING: $failures of $REQUEST_COUNT requests did not return 200 - see traffic-results.csv" >&2
fi

echo ""
echo "==> Deployed."
echo "    API endpoint:  $API_ENDPOINT"
echo "    Requests sent: $REQUEST_COUNT ($failures did not return 200 - see traffic-results.csv)"
echo ""
echo "CloudWatch only publishes a metric for an invocation after it happens, so"
echo "wait a few more minutes (CloudWatch + Metric Streams latency), then check"
echo "New Relic:"
echo ""
echo "    FROM Metric SELECT count(*) WHERE metricName = 'aws.lambda.Invocations.byFunction' SINCE 30 minutes ago"
echo "    FROM ServerlessSample SELECT sum(provider.invocations.Sum) WHERE provider = 'LambdaFunction' SINCE 30 minutes ago"
echo ""
echo "NOTE on that second query: this setup is Metric Streams only (no API"
echo "Polling), so no real ServerlessSample events exist -- 'count(*)' on this"
echo "event type will always be 0 here. New Relic's server-side query-rewrite"
echo "layer still answers *this* query correctly by transparently mapping it"
echo "onto the equivalent Metric data, which is why it still returns a real"
echo "value. See README.md's Findings section."
echo ""
echo "When you're done, run ./teardown.sh"
