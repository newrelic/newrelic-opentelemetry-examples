# Convenience wrapper around the steps in README.md's "Running the example".
# See the README for the rationale behind each command.
#
# `make install` requires manifests/01-secrets.yaml to already exist with
# your New Relic license key filled in -- copy it from
# manifests/01-secrets.yaml.template first (see README).
#
# Every kubectl/helm command targets this cluster explicitly via
# --context/--kube-context, the same way the README does, so this never
# touches your default kubeconfig context and can't land on another cluster.

CLUSTER_NAME := nr-sampling-nr-head
KIND_CONTEXT := kind-$(CLUSTER_NAME)
KUBECTL := kubectl --context $(KIND_CONTEXT)

# Optional: put NEW_RELIC_LICENSE_KEY (and NEW_RELIC_OTLP_ENDPOINT for EU
# accounts) in a gitignored .env file next to this Makefile (see .env.example)
# or export them in your shell; `make secrets` then generates
# manifests/01-secrets.yaml for you.
-include .env
export NEW_RELIC_LICENSE_KEY NEW_RELIC_OTLP_ENDPOINT

.PHONY: help cluster secrets install build deploy clean

help: ## List available targets
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-10s %s\n", $$1, $$2}'

cluster: ## Create the kind cluster
	kind get clusters 2>/dev/null | grep -qx "$(CLUSTER_NAME)" || kind create cluster --name $(CLUSTER_NAME)

secrets: ## Generate manifests/01-secrets.yaml from .env or the environment (skipped if it exists)
	@test -f manifests/01-secrets.yaml || { \
	  test -n "$$NEW_RELIC_LICENSE_KEY" || { \
	    echo "NEW_RELIC_LICENSE_KEY is not set."; \
	    echo "Set it in .env (see .env.example) or your shell, or copy manifests/01-secrets.yaml.template to manifests/01-secrets.yaml and fill it in."; \
	    exit 1; \
	  }; \
	  sed -e "s|<INSERT_LICENSE_KEY>|$$NEW_RELIC_LICENSE_KEY|" \
	      -e "s|https://otlp.nr-data.net|$${NEW_RELIC_OTLP_ENDPOINT:-https://otlp.nr-data.net}|" \
	      manifests/01-secrets.yaml.template > manifests/01-secrets.yaml; \
	  echo "Wrote manifests/01-secrets.yaml"; \
	}

install: secrets ## Install the Operator, and the platform's namespaces/secret/Instrumentation CR
	helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts
	helm repo update
	helm upgrade --install opentelemetry-operator open-telemetry/opentelemetry-operator \
	  --kube-context $(KIND_CONTEXT) \
	  --namespace otel-platform --create-namespace \
	  --set admissionWebhooks.certManager.enabled=false \
	  --set admissionWebhooks.autoGenerateCert.enabled=true
	$(KUBECTL) wait --for=condition=Available deployment/opentelemetry-operator \
	  --namespace otel-platform --timeout=120s
	$(KUBECTL) apply -f manifests/00-namespace.yaml \
	  -f manifests/01-secrets.yaml
	# The Operator Deployment being Available doesn't guarantee its
	# self-signed webhook cert has finished rotating into place yet, so the
	# apiserver can briefly reject the Instrumentation CR as unknown-authority
	# right after startup -- retry instead of failing on that one-off race.
	for i in 1 2 3 4 5 6 7 8 9 10; do \
	  $(KUBECTL) apply -f manifests/04-instrumentation.yaml && break; \
	  if [ "$$i" -eq 10 ]; then echo "Giving up after 10 attempts."; exit 1; fi; \
	  echo "Retrying in 5s (webhook cert may still be rotating)..."; \
	  sleep 5; \
	done

build: ## Build the app images and load them into the kind cluster
	./scripts/build-and-load-images.sh

deploy: ## Deploy the JavaScript (New Relic agent) and Python (Operator) apps
	$(KUBECTL) apply -f manifests/apps/

clean: ## Delete the whole kind cluster
	kind delete cluster --name $(CLUSTER_NAME)
