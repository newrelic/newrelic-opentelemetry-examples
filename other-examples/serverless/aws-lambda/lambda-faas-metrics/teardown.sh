#!/usr/bin/env bash
# Destroys everything deploy.sh created, which also stops the load.
set -euo pipefail
cd "$(dirname "$0")"
source scripts/env.sh

# Required by terraform/variables.tf but not used to destroy anything. They
# must still look like layer ARNs: the AWS provider validates the function's
# layers list even while planning a destroy.
UNUSED_LAYER_ARN="arn:aws:lambda:${AWS_REGION}:000000000000:layer:unused-during-destroy:1"
export TF_VAR_otel_layer_arn="${TF_VAR_otel_layer_arn:-$UNUSED_LAYER_ARN}"
export TF_VAR_collector_layer_arn="${TF_VAR_collector_layer_arn:-$UNUSED_LAYER_ARN}"

echo "==> Destroying all resources (load generation stops with the collector instance)"
terraform -chdir=terraform destroy -auto-approve -input=false

echo "==> Done."
