#!/usr/bin/env bash
# One-command deploy: builds the function, then a single `terraform apply`
# stands up the Lambda function + HTTP API, the collector/load-generator EC2
# instance, and the CloudWatch Metric Stream -> Firehose -> New Relic
# pipeline. Load runs continuously until ./teardown.sh.
#
# See README.md's Configuration section for the environment variables.
set -euo pipefail
cd "$(dirname "$0")"
source scripts/env.sh

# JDK 25 breaks Gradle 8.10 itself, so an inherited JAVA_HOME is only trusted
# once its actual version is checked.
is_jdk_21() {
  [ -x "$1/bin/java" ] && "$1/bin/java" -version 2>&1 | grep -q '"21\.'
}
if [ -n "${JAVA_HOME:-}" ] && ! is_jdk_21 "$JAVA_HOME"; then
  echo "JAVA_HOME is set but isn't a JDK 21 - looking for one instead." >&2
  JAVA_HOME=""
fi
if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in "$HOME"/.sdkman/candidates/java/21*; do
    if is_jdk_21 "$candidate"; then
      JAVA_HOME="$candidate"
      break
    fi
  done
fi
if [ -z "${JAVA_HOME:-}" ] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  candidate="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  if [ -n "$candidate" ] && is_jdk_21 "$candidate"; then
    JAVA_HOME="$candidate"
  fi
fi
if [ -z "${JAVA_HOME:-}" ]; then
  echo "Could not find a JDK 21 (checked JAVA_HOME, ~/.sdkman/candidates/java/21*, and" >&2
  echo "'/usr/libexec/java_home -v 21'). Install one or export JAVA_HOME yourself." >&2
  exit 1
fi
export JAVA_HOME

echo "==> Building the function (JAVA_HOME=$JAVA_HOME)"
(cd ExampleFunction && ./gradlew --quiet test buildZip)

echo "==> Looking up the current ADOT Java layer ARN for $AWS_REGION"
TF_VAR_otel_layer_arn="$(./scripts/get-otel-layer-arn.sh "$AWS_REGION")"
export TF_VAR_otel_layer_arn
echo "    $TF_VAR_otel_layer_arn"

echo "==> Applying Terraform"
terraform -chdir=terraform init -input=false >/dev/null
terraform -chdir=terraform apply -auto-approve -input=false

API_URL="$(terraform -chdir=terraform output -raw api_url)"
COLLECTOR_ENDPOINT="$(terraform -chdir=terraform output -raw collector_endpoint)"
INSTANCE_ID="$(terraform -chdir=terraform output -raw collector_instance_id)"
FUNCTION_NAME="$(terraform -chdir=terraform output -raw function_name)"

# Until the collector answers, the function's spans are silently dropped. It
# only accepts traffic from inside the VPC, so check from the instance itself
# via SSM Run Command (which also fails until the SSM agent has registered).
echo "==> Waiting for the collector and load generator on $INSTANCE_ID (docker install + image pull takes a minute or two)"
ready=""
for _ in $(seq 1 40); do
  command_id="$(aws ssm send-command --instance-ids "$INSTANCE_ID" --document-name AWS-RunShellScript \
    --parameters 'commands=["curl -s -o /dev/null http://localhost:4318/ && systemctl is-active --quiet loadgen"]' \
    --query Command.CommandId --output text 2>/dev/null || true)"
  if [ -n "$command_id" ]; then
    aws ssm wait command-executed --command-id "$command_id" --instance-id "$INSTANCE_ID" 2>/dev/null || true
    result="$(aws ssm get-command-invocation --command-id "$command_id" --instance-id "$INSTANCE_ID" \
      --query Status --output text 2>/dev/null || true)"
    if [ "$result" = "Success" ]; then
      ready=1
      break
    fi
  fi
  sleep 15
done
if [ -z "$ready" ]; then
  echo "The collector or load generator never came up. Inspect the instance with:" >&2
  echo "    aws ssm start-session --target $INSTANCE_ID   (then: sudo docker logs collector)" >&2
  echo "or run ./teardown.sh and try again." >&2
  exit 1
fi

echo "==> Checking the function responds through the API (first call is a Java cold start)"
status=""
for _ in $(seq 1 5); do
  status="$(curl -s -o /dev/null --max-time 35 -w '%{http_code}' "${API_URL}?sleepMs=10" || true)"
  [ "$status" = "200" ] && break
  sleep 5
done
if [ "$status" != "200" ]; then
  echo "The function returned HTTP $status instead of 200. Check its logs:" >&2
  echo "    aws logs tail /aws/lambda/$FUNCTION_NAME --follow" >&2
  exit 1
fi

cat <<EOF

==> Deployed. The load generator on $INSTANCE_ID is now calling the
    function continuously and will keep going until ./teardown.sh.

    Function:   $FUNCTION_NAME
    API:        $API_URL
    Collector:  $COLLECTOR_ENDPOINT (VPC-internal)

OTel data (spans and spanmetrics) should appear in New Relic within a couple
of minutes. CloudWatch Metric Stream data takes longer -- a new stream can
take ~10 minutes to start forwarding. See README.md's "Validate in New Relic"
section for queries.

Watch the load:      aws ssm start-session --target $INSTANCE_ID   (then: journalctl -u loadgen -f)
Watch the function:  aws logs tail /aws/lambda/$FUNCTION_NAME --follow
EOF
