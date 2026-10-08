#!/usr/bin/env bash
# Destroys everything deploy.sh created, which also stops the load.
set -euo pipefail
cd "$(dirname "$0")"
source scripts/env.sh

# Required by terraform/variables.tf but not used to destroy anything.
export TF_VAR_otel_layer_arn="${TF_VAR_otel_layer_arn:-unused-during-destroy}"

echo "==> Destroying all resources (load generation stops with the collector instance)"
terraform -chdir=terraform destroy -auto-approve -input=false

echo "==> Done."
