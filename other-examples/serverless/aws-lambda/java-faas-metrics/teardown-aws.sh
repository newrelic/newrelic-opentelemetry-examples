#!/usr/bin/env bash
# Tears down everything deploy-aws.sh created, reading back the IDs it saved
# to .deploy-state so you don't have to track them yourself.
set -euo pipefail
cd "$(dirname "$0")"

: "${AWS_PROFILE:?Set AWS_PROFILE to the AWS CLI profile that deployed this.}"
STATE_FILE=".deploy-state"

if [ ! -f "$STATE_FILE" ]; then
  echo "No $STATE_FILE found - nothing to tear down (or it's already been removed)." >&2
  exit 1
fi

REGION="$(python3 -c "import json; print(json.load(open('$STATE_FILE'))['region'])")"
STACK_NAME="$(python3 -c "import json; print(json.load(open('$STATE_FILE'))['stackName'])")"
INSTANCE_ID="$(python3 -c "import json; print(json.load(open('$STATE_FILE'))['instanceId'])")"
SG_ID="$(python3 -c "import json; print(json.load(open('$STATE_FILE'))['securityGroupId'])")"

aws() { command aws --profile "$AWS_PROFILE" --region "$REGION" "$@"; }

echo "==> Deleting stack $STACK_NAME"
aws cloudformation delete-stack --stack-name "$STACK_NAME"
aws cloudformation wait stack-delete-complete --stack-name "$STACK_NAME"

echo "==> Terminating instance $INSTANCE_ID"
aws ec2 terminate-instances --instance-ids "$INSTANCE_ID" >/dev/null
aws ec2 wait instance-terminated --instance-ids "$INSTANCE_ID"

echo "==> Deleting security group $SG_ID"
aws ec2 delete-security-group --group-id "$SG_ID"

rm -f "$STATE_FILE"
echo "==> Done. Everything deploy-aws.sh created has been removed."
