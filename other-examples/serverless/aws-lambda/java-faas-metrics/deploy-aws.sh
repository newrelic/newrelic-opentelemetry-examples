#!/usr/bin/env bash
# One-command real AWS deployment: stands up a throwaway EC2 instance running
# the collector, deploys the Lambda function + API Gateway pointed at it, and
# sends real traffic. Prints the NRQL to validate the result yourself.
#
# This mirrors exactly what was manually proven to work during development:
# a real reachable collector (ADOT's Lambda layer has no bundled collector
# and needs one), Active Tracing (Lambda's default PassThrough tracing hands
# the OTel SDK an already-unsampled parent context, so the default sampler
# drops everything), and the trace-specific OTLP endpoint variable (the
# generic one is not honored for traces by this layer). See README.md's
# "Real-deployment findings" section for why each of these is necessary.
#
# Requires: AWS CLI configured and authenticated, SAM CLI, and
# NEW_RELIC_LICENSE_KEY exported. Writes ./.deploy-state (gitignored) so
# teardown-aws.sh can find everything this created.
set -euo pipefail
cd "$(dirname "$0")"

: "${AWS_PROFILE:?Set AWS_PROFILE to the AWS CLI profile to deploy into.}"
: "${NEW_RELIC_LICENSE_KEY:?Set NEW_RELIC_LICENSE_KEY to your license key.}"
REGION="${AWS_REGION:-us-east-1}"
# Production OTLP endpoint by default; override for a different NR region or
# a non-production environment (e.g. https://staging-otlp.nr-data.net:4318).
NEW_RELIC_OPENTELEMETRY_ENDPOINT="${NEW_RELIC_OPENTELEMETRY_ENDPOINT:-https://otlp.nr-data.net}"
REQUEST_COUNT="${REQUEST_COUNT:-100}"
RUN_ID="$(date +%s)-$RANDOM"
STACK_NAME="nr-java-faas-metrics-${RUN_ID}"
SG_NAME="nr-faas-metrics-collector-${RUN_ID}"
STATE_FILE=".deploy-state"

if [ -f "$STATE_FILE" ]; then
  echo "$STATE_FILE already exists - run teardown-aws.sh first, or remove it" >&2
  echo "if you're sure nothing from a previous deploy is still live." >&2
  exit 1
fi

aws() { command aws --profile "$AWS_PROFILE" --region "$REGION" "$@"; }

echo "==> Looking up the current ADOT Java layer ARN for $REGION"
OTEL_LAYER_ARN="$(./scripts/get-otel-layer-arn.sh "$REGION")"
echo "    $OTEL_LAYER_ARN"

echo "==> Finding the default VPC/subnet"
VPC_ID="$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)"
SUBNET_ID="$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=$VPC_ID" "Name=default-for-az,Values=true" --query 'Subnets[0].SubnetId' --output text)"
if [ "$VPC_ID" = "None" ] || [ "$SUBNET_ID" = "None" ]; then
  echo "No default VPC/subnet found in $REGION. This script assumes one exists." >&2
  exit 1
fi

echo "==> Creating a security group for the collector (open on 4318 - see README security note)"
SG_ID="$(aws ec2 create-security-group --group-name "$SG_NAME" \
  --description "Temporary - OTel collector for java-faas-metrics. Safe to delete; see teardown-aws.sh." \
  --vpc-id "$VPC_ID" --query 'GroupId' --output text)"
aws ec2 authorize-security-group-ingress --group-id "$SG_ID" --protocol tcp --port 4318 --cidr 0.0.0.0/0 >/dev/null

echo "==> Looking up the latest Amazon Linux 2023 AMI"
AMI_ID="$(aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 --query 'Parameter.Value' --output text)"

echo "==> Launching the collector instance"
USER_DATA_FILE="$(mktemp)"
{
  echo '#!/bin/bash'
  echo 'set -euo pipefail'
  echo 'dnf install -y docker'
  echo 'systemctl enable --now docker'
  echo 'mkdir -p /etc/otelcol'
  echo "cat > /etc/otelcol/config.yaml << 'COLLECTOR_EOF'"
  cat collector/collector.yaml
  echo 'COLLECTOR_EOF'
  echo "docker run -d --name collector --restart always -p 4317:4317 -p 4318:4318 \\"
  echo "  -e NEW_RELIC_LICENSE_KEY='${NEW_RELIC_LICENSE_KEY}' \\"
  echo "  -e NEW_RELIC_OPENTELEMETRY_ENDPOINT='${NEW_RELIC_OPENTELEMETRY_ENDPOINT}' \\"
  echo "  -v /etc/otelcol/config.yaml:/etc/otelcol-contrib/config.yaml:ro \\"
  echo "  otel/opentelemetry-collector-contrib:0.146.0 --config=/etc/otelcol-contrib/config.yaml"
} > "$USER_DATA_FILE"

INSTANCE_ID="$(aws ec2 run-instances \
  --image-id "$AMI_ID" --instance-type t3.small \
  --subnet-id "$SUBNET_ID" --security-group-ids "$SG_ID" \
  --associate-public-ip-address \
  --user-data "file://$USER_DATA_FILE" \
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${SG_NAME}}]" \
  --query 'Instances[0].InstanceId' --output text)"
rm -f "$USER_DATA_FILE"

echo "==> Waiting for it to reach 'running'"
aws ec2 wait instance-running --instance-ids "$INSTANCE_ID"
PUBLIC_IP="$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)"

cat > "$STATE_FILE" <<EOF
{
  "region": "$REGION",
  "stackName": "$STACK_NAME",
  "instanceId": "$INSTANCE_ID",
  "securityGroupId": "$SG_ID"
}
EOF

echo "==> Waiting for the collector to actually respond on $PUBLIC_IP:4318 (install + image pull takes a minute or two)"
ready=""
for i in $(seq 1 40); do
  if curl -s -o /dev/null --max-time 5 "http://${PUBLIC_IP}:4318/"; then
    ready=1
    break
  fi
  sleep 15
done
if [ -z "$ready" ]; then
  echo "Collector never became reachable. It's still running - inspect it (e.g. via SSM) or" >&2
  echo "run teardown-aws.sh to clean up, then try again." >&2
  exit 1
fi

echo "==> Deploying the Lambda function + API Gateway"
sam build
sam deploy \
  --profile "$AWS_PROFILE" --region "$REGION" \
  --stack-name "$STACK_NAME" \
  --resolve-s3 \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides "otelLambdaLayerArn=${OTEL_LAYER_ARN} otelExporterOtlpEndpoint=http://${PUBLIC_IP}:4318 otelExporterOtlpTracesEndpoint=http://${PUBLIC_IP}:4318/v1/traces" \
  --no-confirm-changeset --no-fail-on-empty-changeset

API_ENDPOINT="$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" --query 'Stacks[0].Outputs[?OutputKey==`apiEndpoint`].OutputValue' --output text)"

echo "==> Generating $REQUEST_COUNT requests of traffic (spaced ~1.1s apart so Active Tracing's"
echo "    default X-Ray sampling reservoir - 1/sec - captures all of them)"
: > full-validation-results.csv
echo "elapsed_ms,sleep_ms,status_code" > full-validation-results.csv
for i in $(seq 1 "$REQUEST_COUNT"); do
  sleep_ms=$(( (RANDOM % 791) + 10 ))
  start=$(date +%s%N)
  status=$(curl -s -o /dev/null -w "%{http_code}" "${API_ENDPOINT}?sleepMs=${sleep_ms}")
  end=$(date +%s%N)
  echo "$(((end - start) / 1000000)),${sleep_ms},${status}" >> full-validation-results.csv
  sleep 1.1
done

failures="$(awk -F, 'NR>1 && $3!=200' full-validation-results.csv | wc -l | tr -d ' ')"

echo ""
echo "==> Deployed. State saved to $STATE_FILE for teardown-aws.sh."
echo "    API endpoint:    $API_ENDPOINT"
echo "    Collector:       http://${PUBLIC_IP}:4318 (instance $INSTANCE_ID)"
echo "    Requests sent:   $REQUEST_COUNT ($failures did not return 200 - see full-validation-results.csv)"
echo ""
echo "Wait about a minute for the collector's metrics flush, then validate in New Relic:"
echo ""
echo "    FROM Metric SELECT sum(faas.invocations), count(faas.invoke_duration) WHERE service.name = 'java-faas-metrics-example' SINCE 10 minutes ago"
echo "    FROM Metric SELECT percentile(faas.invoke_duration, 50, 95, 99) WHERE service.name = 'java-faas-metrics-example' SINCE 10 minutes ago"
echo "    FROM Span SELECT percentile(duration.ms, 50, 95, 99) WHERE service.name = 'java-faas-metrics-example' AND span.kind = 'server' SINCE 10 minutes ago"
echo ""
echo "When you're done, run ./teardown-aws.sh to remove everything this created."
