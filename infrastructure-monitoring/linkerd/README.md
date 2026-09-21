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

**Already have kube-state-metrics?** Many clusters (especially those with Prometheus
or the NR Kubernetes integration) already have it. Check first:

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
- The OTel Collector runs as a **non-root user** (`runAsUser: 1001`) with a **read-only root filesystem**, matching the `nr-k8s-otel-collector` Helm chart's defaults. If your cluster has PodSecurityAdmission restrictions, you still need `hostPath` volume access added to the allowed policy.
- Nodes must expose `/var/log/pods` and `/var/lib/docker/containers` (standard on all major managed K8s providers)

---

## Step 1 — Install Linkerd

### Via Helm (recommended)

Unlike the `linkerd install` CLI, Helm cannot auto-generate the mTLS trust anchor and
issuer certificates Linkerd needs — you must generate and pass them in yourself. This
follows Linkerd's own [Helm install guide](https://linkerd.io/2/tasks/install-helm/)
and [certificate generation guide](https://linkerd.io/2/tasks/generate-certificates/);
see those for the `openssl` alternative to `step`, longer-lived certs (`--not-after`),
and HA install options. For production, also see
[automatic TLS credential rotation](https://linkerd.io/2/tasks/automatically-rotating-control-plane-tls-credentials/)
instead of the one-off certs generated below.

```bash
helm repo add linkerd https://helm.linkerd.io/stable
helm repo update

# Gateway API CRDs (required by Linkerd). Check first — many clusters already have
# these, and installing a version outside Linkerd's compatibility table can break
# your installation: https://linkerd.io/2/features/gateway-api/
kubectl get crds/httproutes.gateway.networking.k8s.io \
  -o "jsonpath={.metadata.annotations.gateway\.networking\.k8s\.io/bundle-version}" \
  2>/dev/null || \
kubectl apply --server-side \
  -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.5.1/standard-install.yaml

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
# See the note above on checking for an existing Gateway API install before applying.
kubectl apply --server-side \
  -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.5.1/standard-install.yaml

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

### Don't have a meshed service yet? Deploy the demo app

Linkerd's [emojivoto](https://github.com/BuoyantIO/emojivoto) demo deploys three
meshed services (`web`, `emoji`, `voting`) plus a `vote-bot` that continuously
generates traffic between them — useful for seeing real request-rate, latency,
and topology metrics without meshing an application of your own first.

```bash
curl -sL https://run.linkerd.io/emojivoto.yml | kubectl apply -f -
kubectl get -n emojivoto deploy -o yaml | linkerd inject - | kubectl apply -f -
linkerd check --namespace emojivoto
```

Use `emojivoto` as `<YOUR_NAMESPACE>` in the steps below, and `web` / `emoji` /
`voting` as the deployment names in the verification queries.

Cleanup: `kubectl delete namespace emojivoto`.

---

## Step 4 — Deploy the OTel Collector

### 4a — Create the namespace and license secret

```bash
kubectl create namespace nr-otel

kubectl -n nr-otel create secret generic nr-license \
  --from-literal=NEW_RELIC_LICENSE_KEY='<YOUR_NR_INGEST_LICENSE_KEY>' \
  --from-literal=NEWRELIC_OTLP_ENDPOINT='https://otlp.nr-data.net:4318'
  # EU accounts:      https://otlp.eu01.nr-data.net:4318
  # Staging accounts: https://staging-otlp.nr-data.net:4318
```

### 4b — Apply the collector manifest

`otel-collector.yaml` defines two workloads — apply both:

- **`nr-otel-collector`** (`Deployment`, 1 replica) — Prometheus metrics scrape + OTLP traces receiver.
  A single replica is fine here; both work over the network, not the local filesystem.
- **`nr-otel-collector-logs`** (`DaemonSet`, one pod per node) — tails `/var/log/pods` via `hostPath`.
  This **must** be a DaemonSet: a `Deployment` only sees the local filesystem of whichever single
  node it lands on, silently dropping every other node's pod logs.

```bash
# 1. Set your cluster name in the OTEL_RESOURCE_ATTRIBUTES env var — appears on BOTH
#    the Deployment and the DaemonSet (see inline comments in the file)
# 2. Apply
kubectl apply -f otel-collector.yaml
kubectl rollout status deployment/nr-otel-collector -n nr-otel
kubectl rollout status daemonset/nr-otel-collector-logs -n nr-otel
```

**Customise before applying:**

| Field | Location | What to set |
|---|---|---|
| `OTEL_RESOURCE_ATTRIBUTES` | Deployment **and** DaemonSet env | `k8s.cluster.name=<your-cluster>` — read by `env` resourcedetection detector |
| `resourcedetection.detectors` | Either ConfigMap | Optional: add `eks`/`gke`/`azure` for extra cloud attributes (requires IAM on EKS) |
| Docker volume (optional) | DaemonSet volumes | Uncomment `/var/lib/docker/containers` if nodes use Docker runtime |
| `kube-state-metrics` target | `nr-otel-config` scrape_configs | Update namespace/name if KSM is not in `kube-system` |
| `global.scrape_interval` | `nr-otel-config` `prometheus.config` | Default `30s`. How often the Linkerd `:4191` endpoints are scraped. |

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
| `filter/drop_unused` | Drops Linkerd proxy metrics with no dashboard/alert value (version/build info, Tokio runtime internals, frame-size histograms, scrape bookkeeping) to reduce ingest volume |
| `filelog` receiver | Tails Linkerd proxy container logs |

> **Important processors for APM correctness:**
> - `transform/linkerd_component_inject` — without this, proxy spans land on a generic `EXT:SERVICE` instead of `EXT:LINKERD`
> - `transform/linkerd_service_name` — without this, every Linkerd sidecar creates a spurious `APM:SERVICE(linkerd-proxy)` entity
> - `metricstransform/apm_compat` — without this, OTel SDK HTTP duration metrics don't appear in NR APM views
> All three are included in `otel-collector.yaml`.

### Optional — Reduce ingest volume

`filter/drop_unused` in `otel-collector.yaml` drops these Linkerd proxy metrics by default, since
they have no dashboard or alert value in normal operation:

| Metric(s) | Why it's dropped |
|---|---|
| `rustls_info`, `proxy_build_info`, `scrape_series_added` | Static version/build labels — no operational value |
| `stack_(poll\|create\|drop)_total`, `stack_poll_total_ms` | Rust connection-stack internals — useful only when debugging connection churn |
| `tokio_rt_*` | Async runtime internals — useful only when debugging proxy CPU starvation |
| `(inbound\|outbound)_http_*_frame_size_bytes` | HTTP frame-size histograms — large-payload debugging only |
| `(inbound\|outbound)_tcp_detect_http_duration_seconds` | Fires once per connection — only useful when Linkerd fails to detect HTTP |
| `outbound_tcp_balancer_queue_*` | TCP backpressure detail — only useful when investigating slow backends |
| `scrape_duration_seconds`, `scrape_samples_scraped`, `scrape_samples_post_metric_relabeling` | Collector's own scrape bookkeeping |

To re-enable any of these during an investigation, remove the matching condition from
`filter/drop_unused` and re-apply.

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
-- Deployed the emojivoto demo instead? You'll see web / emoji / voting in the results.
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
- [cert-manager](https://cert-manager.io/docs/installation/) installed — the OTel Operator's
  admission webhook needs it to issue its own TLS certificate

> **This section only produces app-level spans.** To also get the Linkerd *proxy* spans
> (mesh routing hops) merged into the same trace waterfall, complete
> [Optional — Enable Linkerd Proxy Traces](#optional--enable-linkerd-proxy-traces-linkerd-219) below
> as well — either before or after this section, in either order.

> **Cluster-wide naming:** `transform/linkerd_service_name` in the collector makes each meshed
> deployment's name its APM `service.name`. Use unique deployment names across every cluster
> reporting to the same New Relic account — a name that also exists in another cluster resolves
> to the same entity there, so their data gets merged rather than showing up as two separate services.

### Instrument your application — OTel Operator (zero Deployment changes)

The Operator's admission webhook auto-injects the OTel Java agent into annotated pods —
no image change, no init container, no manual env vars on your Deployment.

**1. Install cert-manager, then the OTel Operator:**

```bash
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/latest/download/cert-manager.yaml
kubectl -n cert-manager rollout status deployment/cert-manager
kubectl -n cert-manager rollout status deployment/cert-manager-webhook
kubectl -n cert-manager rollout status deployment/cert-manager-cainjector

kubectl apply -f https://github.com/open-telemetry/opentelemetry-operator/releases/latest/download/opentelemetry-operator.yaml
kubectl -n opentelemetry-operator-system rollout status deployment/opentelemetry-operator-controller-manager
```

**2. Create an `Instrumentation` CR:**

```yaml
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
  # Without this, the SDK defaults to OTLP/HTTP and the export silently fails —
  # port 4317 is gRPC-only. Confirmed by hitting this exact failure in testing:
  # the agent's HTTP/1.1 client choked on the gRPC port's HTTP/2 framing.
  env:
    - name: OTEL_EXPORTER_OTLP_PROTOCOL
      value: grpc
  java:
    image: ghcr.io/open-telemetry/opentelemetry-operator/autoinstrumentation-java:latest
```

```bash
kubectl apply -f instrumentation.yaml
```

**3. Annotate your Deployment's pod template — no other changes needed:**

```yaml
spec:
  template:
    metadata:
      annotations:
        linkerd.io/inject: enabled                              # ensure injection
        instrumentation.opentelemetry.io/inject-java: "true"     # auto-inject the OTel agent
```

```bash
kubectl rollout restart deployment/<YOUR_DEPLOYMENT> -n <YOUR_NAMESPACE>
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
> throughput or response time. `server.address`/`server.port` come from the OTel Java agent's
> own auto-instrumentation of your app's HTTP server framework — the collector doesn't set them.
> The agent covers most common frameworks (Servlet, Spring, Vert.x, Netty, and even the JDK's
> built-in `com.sun.net.httpserver.HttpServer`); if your app uses one it doesn't cover, no
> server-side span is produced and the entity stays client-only regardless of collector config.
> `OTEL_METRICS_EXPORTER` should NOT be disabled — OTel SDK metrics
> (`http.server.request.duration`, JVM metrics) are required for complete golden metrics
> coverage, and reach APM views via the `metricstransform/apm_compat` processor below.

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

The `filelog` receiver, running in the **`nr-otel-collector-logs` DaemonSet**, requires:
1. **A pod on every node.** `kubectl get pods -n nr-otel -l app=nr-otel-collector-logs -o wide` should
   show one Running pod per node. A `Deployment` here would silently drop every node's logs except
   the one it happens to land on — this is why logs are a separate DaemonSet, not part of the main
   `nr-otel-collector` Deployment.
2. The node's `/var/log/pods` is mounted as a `hostPath` volume — already set in the manifest
3. On Docker-based runtimes, `/var/log/pods` symlinks into `/var/lib/docker/containers` — uncomment the Docker volume in the manifest if needed
4. The non-root collector (`runAsUser: 1001`) must be able to read the log files. Observed permissions vary by runtime — adjust `fsGroup`/`runAsUser` on the DaemonSet if reads fail on your cluster.

If logs are missing, verify the collector can read pod log files:
```bash
kubectl exec -n nr-otel daemonset/nr-otel-collector-logs -- ls /var/log/pods 2>/dev/null | head -5
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
| `README.md` | This document |

---

## References

- [Linkerd Installing with Helm](https://linkerd.io/2/tasks/install-helm/)
- [Linkerd Generating your own mTLS root certificates](https://linkerd.io/2/tasks/generate-certificates/)
- [Linkerd Automatically Rotating Control Plane TLS Credentials](https://linkerd.io/2/tasks/automatically-rotating-control-plane-tls-credentials/)
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
| Gateway API CRDs | v1.5.1 (for Linkerd 2.20+ — see [compatibility table](https://linkerd.io/2/features/gateway-api/) if running an older Linkerd) |
| OTel Collector Contrib | 0.119.0+ |
| OTel Java Agent | 2.4.0+ |
| kube-state-metrics | 2.x |
| Kubernetes | 1.28+ |
