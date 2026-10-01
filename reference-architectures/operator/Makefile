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

CLUSTER_NAME := nr-operator-demo
KIND_CONTEXT := kind-$(CLUSTER_NAME)
KUBECTL := kubectl --context $(KIND_CONTEXT)

.PHONY: help cluster install build deploy clean

help: ## List available targets
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-10s %s\n", $$1, $$2}'

cluster: ## Create the kind cluster
	kind get clusters 2>/dev/null | grep -qx "$(CLUSTER_NAME)" || kind create cluster --name $(CLUSTER_NAME)

install: ## Install Metrics Server, the Operator, and the platform's namespaces/secret/RBAC/CRs
	@test -f manifests/01-secrets.yaml || { \
	  echo "manifests/01-secrets.yaml not found."; \
	  echo "Copy manifests/01-secrets.yaml.template to manifests/01-secrets.yaml and fill in your New Relic license key first (see README), then re-run 'make install'."; \
	  exit 1; \
	}
	$(KUBECTL) apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
	@$(KUBECTL) get deployment metrics-server -n kube-system -o jsonpath='{.spec.template.spec.containers[0].args}' 2>/dev/null | grep -q kubelet-insecure-tls || \
	  $(KUBECTL) patch deployment metrics-server -n kube-system --type=json \
	    -p '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
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
	  -f manifests/01-secrets.yaml \
	  -f manifests/02-collector-rbac.yaml
	# The Operator Deployment being Available doesn't guarantee its
	# self-signed webhook cert has finished rotating into place yet, so the
	# apiserver can briefly reject it as unknown-authority right after
	# startup -- retry instead of failing on that one-off race. Applies to
	# every CR below (OpenTelemetryCollector, then Instrumentation), since
	# each goes through its own admission webhook.
	for i in 1 2 3 4 5 6 7 8 9 10; do \
	  $(KUBECTL) apply -f manifests/03-collector.yaml && break; \
	  if [ "$$i" -eq 10 ]; then echo "Giving up after 10 attempts."; exit 1; fi; \
	  echo "Retrying in 5s (webhook cert may still be rotating)..."; \
	  sleep 5; \
	done
	$(KUBECTL) wait --for=condition=Available deployment/otel-gateway-collector \
	  --namespace otel-platform --timeout=120s
	for i in 1 2 3 4 5 6 7 8 9 10; do \
	  $(KUBECTL) apply -f manifests/04-instrumentation.yaml && break; \
	  if [ "$$i" -eq 10 ]; then echo "Giving up after 10 attempts."; exit 1; fi; \
	  echo "Retrying in 5s (webhook cert may still be rotating)..."; \
	  sleep 5; \
	done

build: ## Build the app images and load them into the kind cluster
	./scripts/build-and-load-images.sh

deploy: ## Deploy the Java and Python apps
	$(KUBECTL) apply -f manifests/apps/

clean: ## Delete the whole kind cluster
	kind delete cluster --name $(CLUSTER_NAME)
