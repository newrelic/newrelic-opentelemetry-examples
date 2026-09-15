# Linkerd + New Relic OTel Observability — Setup Guide

This guide walks through instrumenting a Linkerd service mesh to send metrics, traces,
and logs to New Relic using OpenTelemetry. Two paths are covered:

| Path | What you get | Code changes required |
|---|---|---|
| **Basic** | Mesh-level metrics (request rate, latency, error rate, mTLS, topology) for every service | None |
| **APM** | Everything in Basic + distributed traces, per-endpoint breakdown, stack traces | Add OTel Java agent to app pods |

---

## Already have Linkerd installed?

Skip Steps 1 and 3 — go directly to Step 2 then Step 4. The OTel Collector is
fully additive: it scrapes the Linkerd proxy admin ports (`:4191`) that are already
present on every meshed pod. No Linkerd configuration changes, no pod restarts.

**Already have kube-state-metrics?** Many clusters (especially those with Prometheus,
DataDog, or the NR Kubernetes integration) already have it. Check first:

```bash
kubectl get deployment -A | grep kube-state
kubectl get svc -A | grep kube-state
```

If it exists, skip Step 2 and update the `kube-state-metrics` scrape target in the
collector config to point at the correct namespace.

---

## Prerequisites

- Kubernetes cluster (EKS, GKE, AKS, self-managed, etc.)
- `kubectl` configured against the target cluster
- `linkerd` CLI ([install](https://linkerd.io/2/getting-started/))
- New Relic **ingest license key** (format: `...NRAL`)
- The OTel Collector runs as **root** (`runAsUser: 0`) to read `/var/log/pods` (owned by root). Most clusters allow this; if your cluster has PodSecurityAdmission restrictions, add `hostPath` volume access to the allowed policy.
- Nodes must expose `/var/log/pods` and `/var/lib/docker/containers` (standard on all major managed K8s providers)

---

## Step 1 — Install Linkerd

### Via Helm (recommended)

Helm requires explicit certificates. Generate them with the [`step` CLI](https://smallstep.com/docs/step-cli/installation/):

```bash
helm repo add linkerd https://helm.linkerd.io/stable
helm repo update

# Gateway API CRDs (required by Linkerd)
kubectl apply --server-side \
  -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.2.1/standard-install.yaml

# Generate trust anchor + issuer certs
step certificate create root.linkerd.cluster.local ca.crt ca.key \
  --profile root-ca --no-password --insecure
step certificate create identity.linkerd.cluster.local issuer.crt issuer.key \
  --profile intermediate-ca --not-after 8760h --no-password --insecure \
  --ca ca.crt --ca-key ca.key

# Install Linkerd CRDs, then control plane
helm install linkerd-crds linkerd/linkerd-crds \
  --namespace linkerd --create-namespace

helm install linkerd-control-plane linkerd/linkerd-control-plane \
  --namespace linkerd \
  --set-file identityTrustAnchorsPEM=ca.crt \
  --set identity.issuer.tls.crtPEM="$(cat issuer.crt)" \
  --set identity.issuer.tls.keyPEM="$(cat issuer.key)"
  # Docker-based runtimes — add: --set proxyInit.runAsRoot=true

linkerd check
```

### Alternative — Linkerd CLI (auto-generates certificates)

```bash
kubectl apply --server-side \
  -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.2.1/standard-install.yaml

linkerd install --crds | kubectl apply -f -
linkerd install | kubectl apply -f -
# Docker-based runtimes: linkerd install --set proxyInit.runAsRoot=true | kubectl apply -f -

linkerd check
```

---

## Step 2 — Install kube-state-metrics

kube-state-metrics provides Kubernetes object metrics that NR uses to synthesise
`KUBERNETES_DEPLOYMENT`, `KUBERNETES_POD`, and `KUBERNETES_NAMESPACE` entities.

### Via Helm (recommended)

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install kube-state-metrics prometheus-community/kube-state-metrics \
  --namespace kube-system --create-namespace
```

The Helm chart creates the Service on port 8080 automatically — no extra step needed.

### Alternative — kubectl

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/kube-state-metrics/main/examples/standard/service-account.yaml
kubectl apply -f https://raw.githubusercontent.com/kubernetes/kube-state-metrics/main/examples/standard/cluster-role.yaml
kubectl apply -f https://raw.githubusercontent.com/kubernetes/kube-state-metrics/main/examples/standard/cluster-role-binding.yaml
kubectl apply -f https://raw.githubusercontent.com/kubernetes/kube-state-metrics/main/examples/standard/deployment.yaml
```

Then create the Service so the OTel Collector can scrape it:

```bash
kubectl apply -f - <<'EOF'
apiVersion: v1
kind: Service
metadata:
  name: kube-state-metrics
  namespace: kube-system
  labels:
    app.kubernetes.io/name: kube-state-metrics
spec:
  selector:
    app.kubernetes.io/name: kube-state-metrics
  ports:
    - { name: http-metrics, port: 8080, targetPort: 8080 }
EOF
```

---

## Step 3 — Inject Linkerd into your namespaces

```bash
# Enable automatic sidecar injection for each application namespace
kubectl annotate namespace <YOUR_NAMESPACE> linkerd.io/inject=enabled

# Restart existing pods to inject the sidecar
kubectl rollout restart deployment -n <YOUR_NAMESPACE>

# Confirm all pods are meshed
linkerd check --namespace <YOUR_NAMESPACE>
```

> **Note:** Repeat for every namespace whose services should appear in New Relic.

---

## Step 4 — Deploy the OTel Collector

### 4a — Create the namespace and license secret

```bash
kubectl create namespace nr-otel

kubectl -n nr-otel create secret generic nr-license \
  --from-literal=NEW_RELIC_LICENSE_KEY='<YOUR_NR_INGEST_LICENSE_KEY>' \
  --from-literal=NEWRELIC_OTLP_ENDPOINT='https://otlp.nr-data.net:443'
  # EU accounts:      https://otlp.eu01.nr-data.net:443
  # Staging accounts: https://staging-otlp.nr-data.net:443
```

### 4b — Apply the collector manifest

Use `otel-collector.yaml` for all deployments.

```bash
# 1. Set your cluster name in the OTEL_RESOURCE_ATTRIBUTES env var in the Deployment
#    (see inline comments in the file)
# 2. Apply
kubectl apply -f otel-force-runs/linkerd/otel-collector.yaml
kubectl rollout status deployment/nr-otel-collector -n nr-otel
```

**Customise before applying:**

| Field | Location | What to set |
|---|---|---|
| `OTEL_RESOURCE_ATTRIBUTES` | Deployment env | `k8s.cluster.name=<your-cluster>` — read by `env` resourcedetection detector |
| `resourcedetection.detectors` | ConfigMap | Optional: add `eks`/`gke`/`azure` for extra cloud attributes (requires IAM on EKS) |
| Docker volume (optional) | Deployment volumes | Uncomment `/var/lib/docker/containers` if nodes use Docker runtime |
| `kube-state-metrics` target | ConfigMap scrape_configs | Update namespace/name if KSM is not in `kube-system` |
| `global.scrape_interval` | ConfigMap `prometheus.config` | Default `30s`. How often the Linkerd `:4191` endpoints are scraped. |

> **Note — Linkerd Proxy Traces (Linkerd 2.19+):**
> Linkerd proxy spans (mesh routing decisions, retries, circuit breaking) are sent to
> the standard OTLP port 4317 through the Linkerd mesh. No separate port or cert-manager
> is required. See the **Optional — Enable Linkerd Proxy Traces** section below for setup.

**Key collector config sections:**

| Component | Purpose |
|---|---|
| `linkerd-controller` scrape job | Scrapes Linkerd CP pods (destination, identity, proxy-injector) |
| `linkerd-proxy` scrape job | Scrapes every meshed pod's proxy sidecar on `:4191` |
| `kube-state-metrics` scrape job | Scrapes K8s object metrics for entity synthesis |
| `otlp` receiver `:4317` | Receives app traces from OTel Java/Python/Node agents |
| `k8sattributes/traces` | Enriches spans with `linkerd_control_plane_ns` + `linkerd_control_plane_component` from pod labels — **required** for span-based `EXT:LINKERD` entity synthesis |
| `transform/linkerd_component_inject` | Stamps `linkerd_control_plane_component` on all Linkerd proxy spans so the span synthesis rule resolves them to `EXT:LINKERD` — **required** for APM relationships |
| `transform/linkerd_service_name` | Renames `service.name=linkerd-proxy` → deployment name — **required** to prevent a spurious `linkerd-proxy` APM entity |
| `metricstransform/apm_compat` | Renames `http.server.request.duration` → `apm.service.transaction.duration` for NR APM compatibility |
| `filelog` receiver | Tails Linkerd proxy container logs |

> **Important processors for APM correctness:**
> - `transform/linkerd_component_inject` — without this, proxy spans land on a generic `EXT:SERVICE` instead of `EXT:LINKERD`
> - `transform/linkerd_service_name` — without this, every Linkerd sidecar creates a spurious `APM:SERVICE(linkerd-proxy)` entity
> - `metricstransform/apm_compat` — without this, OTel SDK HTTP duration metrics don't appear in NR APM views
> All three are included in `otel-collector.yaml`.

---

## Basic Setup — Verification

Run in **NR Query Builder** (replace `<YOUR_CLUSTER>`):

```sql
-- Confirm Linkerd proxy metrics are flowing
SELECT count(*) FROM Metric
WHERE k8s.cluster.name = '<YOUR_CLUSTER>'
  AND linkerd_control_plane_ns IS NOT NULL
SINCE 5 minutes ago
-- Expected: thousands of data points per scrape interval
```

```sql
-- Confirm per-service request rates
SELECT rate(sum(inbound_http_requests_total), 1 minute) AS 'Req/min'
FROM Metric
WHERE k8s.cluster.name = '<YOUR_CLUSTER>'
  AND linkerd_control_plane_component IS NULL
FACET k8s.deployment.name
SINCE 30 minutes ago
```

```sql
-- Confirm KUBERNETES_DEPLOYMENT entities exist
SELECT uniques(entity.type), uniques(k8s.deployment.name)
FROM Metric
WHERE metricName LIKE 'kube_deployment%'
  AND k8s.cluster.name = '<YOUR_CLUSTER>'
SINCE 10 minutes ago
```

**What you get with Basic (zero code changes):**

- Request rate, latency p50/p95/p99, error rate per service
- TCP connections and bytes transferred
- mTLS certificate expiry countdown and rotation rate
- Authorization policy allow/deny rates
- Control plane health (queue depth, live endpoints, cert issuance)
- Service topology — who calls whom and at what rate
- `EXT:LINKERD` entity with full Linkerd dashboard
- Meshed pod inventory across all namespaces

---

## APM Setup — Add Distributed Tracing

The OTel Java agent adds **application-layer observability** on top of the mesh layer.
It instruments the JVM to produce distributed traces and sends them to the collector's
OTLP receiver.

### Required for APM

- OTel Collector deployed (Step 4) — the `otlp` receiver must be reachable
- Application pods **must be Linkerd-injected** (Step 3)

### Option A — Init Container (no image change)

Add to your existing Deployment:

```yaml
spec:
  template:
    metadata:
      annotations:
        linkerd.io/inject: enabled    # ensure injection
    spec:
      initContainers:
        - name: otel-agent-init
          image: busybox
          command:
            - wget
            - -O
            - /otel/opentelemetry-javaagent.jar
            - https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.4.0/opentelemetry-javaagent.jar
          volumeMounts:
            - { mountPath: /otel, name: otel-agent }

      containers:
        - name: your-app
          # existing image, ports, etc.
          env:
            - name: JAVA_TOOL_OPTIONS
              value: "-javaagent:/otel/opentelemetry-javaagent.jar"
            - name: OTEL_SERVICE_NAME
              value: "<your-service-name>"
            - name: OTEL_EXPORTER_OTLP_ENDPOINT
              value: "http://nr-otel-collector.nr-otel.svc.cluster.local:4317"
            - name: OTEL_EXPORTER_OTLP_PROTOCOL
              value: "grpc"
            # Keep OTel SDK metrics enabled — http.server.request.duration and JVM metrics
            # power golden metrics (throughput, response time) on the EXT:SERVICE entity.
            # The metrics/otlp pipeline in the collector handles these correctly.
            - name: OTEL_LOGS_EXPORTER
              value: "none"
            # Downward API — required for k8sattributes enrichment
            - name: MY_POD_IP
              valueFrom: { fieldRef: { fieldPath: status.podIP } }
            - name: MY_POD_NAME
              valueFrom: { fieldRef: { fieldPath: metadata.name } }
            - name: MY_POD_NAMESPACE
              valueFrom: { fieldRef: { fieldPath: metadata.namespace } }
            - name: OTEL_RESOURCE_ATTRIBUTES
              value: "k8s.pod.ip=$(MY_POD_IP),k8s.pod.name=$(MY_POD_NAME),k8s.namespace.name=$(MY_POD_NAMESPACE),k8s.deployment.name=<your-service-name>"
          volumeMounts:
            - { mountPath: /otel, name: otel-agent }
            # existing mounts ...

      volumes:
        - { name: otel-agent, emptyDir: {} }
        # existing volumes ...
```

### Option B — OTel Operator (production-recommended, zero Deployment changes)

```bash
# Install the OpenTelemetry Operator
kubectl apply -f https://github.com/open-telemetry/opentelemetry-operator/releases/latest/download/opentelemetry-operator.yaml

# Create an Instrumentation CR
kubectl apply -f - <<'EOF'
apiVersion: opentelemetry.io/v1alpha1
kind: Instrumentation
metadata:
  name: nr-instrumentation
  namespace: <YOUR_NAMESPACE>
spec:
  exporter:
    endpoint: http://nr-otel-collector.nr-otel.svc.cluster.local:4317
  propagators:
    - tracecontext
    - baggage
  java:
    image: ghcr.io/open-telemetry/opentelemetry-operator/autoinstrumentation-java:latest
EOF

# Annotate each Deployment — no other changes needed
kubectl annotate deployment <YOUR_DEPLOYMENT> \
  instrumentation.opentelemetry.io/inject-java="true" \
  -n <YOUR_NAMESPACE>
```

### APM Verification

```sql
-- Confirm app spans are flowing
SELECT count(*) FROM Span
WHERE service.name = '<your-service-name>'
  AND linkerd_control_plane_ns IS NOT NULL
SINCE 5 minutes ago

-- Confirm trace connectivity (frontend → backend chain)
SELECT uniques(service.name) FROM Span
WHERE trace.id IN (
  SELECT trace.id FROM Span
  WHERE service.name = '<your-frontend-service>'
  SINCE 10 minutes ago LIMIT 10)
SINCE 10 minutes ago

-- Check span attributes (all must be non-null)
SELECT latest(linkerd_control_plane_ns),
       latest(k8s.cluster.name),
       latest(instrumentation.provider),
       latest(entity.guid)
FROM Span
WHERE service.name = '<your-service-name>'
  AND linkerd_control_plane_ns IS NOT NULL
SINCE 5 minutes ago LIMIT 1
```

> **Note on golden metrics timing:** After deploying the Java agent, allow 5–10 minutes for
> NR entity synthesis to compute `nr.endpoint` from span `server.address`/`server.port` attributes.
> Without `nr.endpoint`, the EXT:SERVICE entity shows as client-only with no server-side
> throughput or response time. The `transform/service_endpoint` processor in the collector
> sets `server.address` automatically. `OTEL_METRICS_EXPORTER` should NOT be disabled —
> OTel SDK metrics (`http.server.request.duration`, JVM metrics) are required for complete
> golden metrics coverage.

**What APM adds on top of Basic:**

- Distributed trace waterfall (frontend → backend with per-span timing)
- Per-endpoint latency — `GET /api/users` vs `POST /checkout` latency separately
- Error stack traces — not just "500 happened" but the Java exception and line
- `EXT:SERVICE` entity in NR with service map
- `EXT:SERVICE → CALLS → EXT:SERVICE` relationship from distributed traces
- `EXT:LINKERD` entity visible in APM service map via span-based synthesis

---

## Entity Map Navigation

Once data flows, the following entities appear automatically in New Relic:

```
INFRA:KUBERNETESCLUSTER (<cluster-name>)
  └── CONTAINS → EXT:LINKERD (<cluster-name>-linkerd)
                    └── MANAGES → INFRA:KUBERNETES_DEPLOYMENT (each meshed workload)

EXT:SERVICE (<app-name>)   ← APM only
  └── CALLS → EXT:SERVICE (<downstream-app-name>)
```

**Navigate in NR UI:**

1. **Linkerd entity + dashboard:** Entity Explorer → search `<cluster-name>-linkerd` → EXT:LINKERD → click "Linkerd Entity" dashboard
2. **Service map (APM):** APM & Services → Services - OpenTelemetry → your service → Service map
3. **Distributed traces (APM):** Same service page → Distributed tracing tab

---

## Dashboard

The `EXT:LINKERD` entity includes a built-in dashboard with 24 widgets covering:

| Section | Widgets |
|---|---|
| Top-line | TCP Connections, Requests Served by Proxies |
| Resources | HTTP Traffic Authorization, Proxy CPU |
| Traffic | Inbound Rate, Outbound Rate |
| Health | Success Rate, Error Rate, HTTP Status Codes |
| Network | Bytes Transferred, Active TCP Connections |
| Latency | Request Latency p50/p95/p99, Outbound Latency p99, Latency p99 by Deployment |
| Security | mTLS Certificate Expiry, Cert Refresh Rate, Control Plane Live Endpoints |
| Control Plane | Authorization Policy, CP Queue Depth, CP Request Rate |
| Topology | Proxy Memory Usage, Operations by Service |
| Route-level | Route-level Request Rate (requires HTTPRoute policies) |
| Inventory | Meshed Pods |

---

## Troubleshooting

### No metrics in NR

```bash
# Check collector is running
kubectl get pods -n nr-otel

# Check collector logs for export errors
kubectl logs -n nr-otel deployment/nr-otel-collector | grep -E "error|warn|Failed" | tail -20

# Verify Prometheus scrape is working
kubectl logs -n nr-otel deployment/nr-otel-collector | grep "scrape" | tail -10
```

### No spans / traces (APM)

```bash
# Check Java agent loaded
kubectl logs -n <YOUR_NAMESPACE> deployment/<YOUR_APP> | grep "javaagent"

# Check for OTLP export failures
kubectl logs -n <YOUR_NAMESPACE> deployment/<YOUR_APP> | grep "WARN.*grpc\|Failed.*export" | tail -10

# Verify Linkerd proxy routing for OTLP (appProtocol must be grpc)
kubectl get svc -n nr-otel nr-otel-collector -o jsonpath='{.spec.ports}' | python3 -m json.tool
# Port 4317 must show "appProtocol": "grpc"
```

### linkerd_control_plane_ns missing from spans

The `k8sattributes/traces` processor uses `k8s.pod.ip` for pod association. Verify the
downward API env var is set:

```bash
kubectl exec -n <NS> deployment/<APP> -- env | grep MY_POD_IP
# Must return a valid pod IP
```

### No logs in NR

The `filelog` receiver requires:
1. The collector pod runs as root (`securityContext: runAsUser: 0`) — already set in the manifest
2. The node's `/var/log/pods` is mounted as a `hostPath` volume — already set in the manifest
3. On Docker-based runtimes, `/var/log/pods` symlinks into `/var/lib/docker/containers` — uncomment the Docker volume in the manifest if needed

If logs are missing, verify the collector can read pod log files:
```bash
kubectl exec -n nr-otel deployment/nr-otel-collector -- ls /var/log/pods 2>/dev/null | head -5
```

### Linkerd proxy routing for OTLP gRPC fails

The collector Service port `4317` must have `appProtocol: grpc`. Without it, Linkerd
routes gRPC as HTTP/1.1 causing `route default.http: service in fail-fast` errors.

```bash
kubectl patch svc -n nr-otel nr-otel-collector --type='json' \
  -p='[{"op":"add","path":"/spec/ports/0/appProtocol","value":"grpc"}]'
```

---

## Architecture Reference

```
┌─────────────────────────────────────────────────────────────┐
│  Kubernetes Cluster                                         │
│                                                             │
│  ┌──────────────┐  ┌──────────────────────────────────┐    │
│  │ linkerd (CP) │  │ Application Namespaces            │    │
│  │ destination  │  │                                   │    │
│  │ identity     │  │  [proxy] ──→ service-A            │    │
│  │ injector     │  │  [proxy] ──→ service-B            │    │
│  └──────┬───────┘  └────────────┬─────────────────────┘    │
│         │ :4191 (admin)         │ :4191 (admin)             │
│         └───────────┬───────────┘                           │
│                     │ Prometheus scrape                     │
│                     ▼                                       │
│         ┌──────────────────────┐                            │
│         │  OTel Collector      │                            │
│         │  (nr-otel ns)        │ ←── OTLP :4317            │
│         │                      │     (app traces)          │
│         └──────────┬───────────┘                            │
│                    │ OTLP/HTTP                              │
└────────────────────┼────────────────────────────────────────┘
                     │
                     ▼
           New Relic (Metric, Span, Log)
           ├── EXT:LINKERD entity + dashboard
           ├── EXT:SERVICE entities (APM)
           ├── INFRA:KUBERNETES_DEPLOYMENT entities
           └── Entity relationships
```

---

## Optional — Enable Linkerd Proxy Traces (Linkerd 2.19+)

As of Linkerd 2.19, the Linkerd-Jaeger extension is **deprecated**. Linkerd proxy trace
export is now configured directly in the Linkerd control plane. Proxy spans are sent to
the collector's standard OTLP port 4317 **through the Linkerd mesh** — no cert-manager,
no port 5317, no extra TLS setup required.

> **Note:** The `meshIdentity` stanza is **mandatory**. Linkerd can only export traces
> to a collector that is inside the mesh. The collector pod must have
> `linkerd.io/inject: enabled` (already set in `otel-collector.yaml`).

**Step 1 — Enable tracing in Linkerd:**

Via Helm (`values.yaml`):
```yaml
proxy:
  tracing:
    enabled: true
    collector:
      endpoint: nr-otel-collector.nr-otel.svc.cluster.local:4317
      meshIdentity:
        serviceAccountName: nr-otel-collector
        namespace: nr-otel
```

Via CLI:
```bash
linkerd upgrade \
  --set proxy.tracing.enabled=true \
  --set proxy.tracing.collector.endpoint=nr-otel-collector.nr-otel.svc.cluster.local:4317 \
  --set proxy.tracing.collector.meshIdentity.serviceAccountName=nr-otel-collector \
  --set proxy.tracing.collector.meshIdentity.namespace=nr-otel \
  | kubectl apply -f -
```

**Step 2 — Restart all meshed pods** to apply the new proxy configuration:

```bash
kubectl rollout restart deployment -n <YOUR_NAMESPACE>
```

**Step 3 — Instrument your applications** to propagate trace context headers. Linkerd
supports both [w3c Trace Context](https://www.w3.org/TR/trace-context/) and
[b3](https://github.com/openzipkin/b3-propagation) formats. The OTel Java agent handles
this automatically. Without header propagation, proxy spans will not be linked to app spans.

> **Note:** Each request through a Linkerd mesh produces 4 proxy spans: source proxy
> server + client, destination proxy server + client. These appear as `linkerd-proxy`
> spans in the trace waterfall.

Reference: [Linkerd Distributed Tracing docs](https://linkerd.io/2.19/tasks/distributed-tracing/)

---

## Reference Files

| File | Purpose |
|---|---|
| `otel-collector.yaml` | OTel Collector manifest — metrics, traces, logs |
| `SETUP.md` | This document |

---

## References

- [Linkerd Distributed Tracing (2.19+)](https://linkerd.io/2.19/tasks/distributed-tracing/)
- [Linkerd External Prometheus scrape config](https://linkerd.io/2.19/tasks/external-prometheus/)
- [Migrating from Linkerd-Jaeger extension](https://linkerd.io/2.19/tasks/jaeger-extension-migration/)
- [OTel Collector Prometheus receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/prometheusreceiver)
- [OTel Java agent](https://github.com/open-telemetry/opentelemetry-java-instrumentation)

---

## Supported Versions

| Component | Tested version |
|---|---|
| Linkerd | edge-26.x / stable-2.x |
| OTel Collector Contrib | 0.119.0+ |
| OTel Java Agent | 2.4.0+ |
| kube-state-metrics | 2.x |
| Kubernetes | 1.28+ |
