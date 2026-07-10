# Monitoring IBM MQ with OpenTelemetry Collector

Monitor IBM MQ with New Relic using OpenTelemetry. The
[NRDOT Collector](https://github.com/newrelic/nrdot-collector-releases) scrapes
the [`ibm-messaging/mq-metric-samples`](https://github.com/ibm-messaging/mq-metric-samples)
Prometheus exporter and ships metrics to New Relic via OTLP.

Metrics keep their native Prometheus shape (e.g. `ibmmq_queue_depth`, labels
`qmgr` / `queue` / `channel`) so they map cleanly onto New Relic's IBM MQ entity
definitions:

- [`INFRA / IBMMQ_MANAGER`](https://github.com/newrelic/entity-definitions/tree/main/entity-types/infra-ibmmq_manager) — one per queue manager
- [`INFRA / IBMMQ_QUEUE`](https://github.com/newrelic/entity-definitions/tree/main/entity-types/infra-ibmmq_queue) — one per (queue manager, queue)

## Requirements

- Docker and Docker Compose v2 (for the quick start)
- A New Relic account and [ingest license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/)
- An IBM MQ Prometheus exporter (`mq_prometheus`) reachable from the collector — the Docker Compose quick start builds one from source (needs internet access to fetch the IBM MQ redistributable client)

## Architecture

```
IBM MQ Queue Manager (PCF) ──▶ mq-metric-samples exporter (:9157) ──▶ NRDOT Collector ──▶ New Relic (OTLP)
```

The exporter speaks PCF to the queue manager and exposes Prometheus; the
collector scrapes Prometheus and exports OTLP. They are decoupled — either can be
added to a host that already runs the other.

The Docker Compose quick start builds the
[`mq-metric-samples`](https://github.com/ibm-messaging/mq-metric-samples) exporter
from source (there is no published image) and runs it against the queue manager
with `objects.queues` / `objects.channels` and `useObjectStatus: true` (see
[`mq_prometheus.yaml`](./mq_prometheus.yaml)). That full metric set — `ibmmq_qmgr_*`,
`ibmmq_queue_*`, `ibmmq_channel_*` — synthesizes **both** the `IBMMQ_MANAGER` and
`IBMMQ_QUEUE` entities.

> **Note.** The IBM MQ container's built-in metrics endpoint (`MQ_ENABLE_METRICS`)
> is queue-manager-level only (`ibmmq_qmgr_*` → `IBMMQ_MANAGER`). Per-queue metrics
> and `IBMMQ_QUEUE` entities require the standalone exporter, which is why this
> example builds and runs it.

## Quick start (Docker Compose)

Brings up IBM MQ, the `mq-metric-samples` exporter (built from source — the first
`up` takes a few minutes), a small traffic generator (`mq-load`, which puts/gets
messages on `DEV.QUEUE.1` so throughput/depth metrics move), and the collector.

```bash
cp .env.example .env
# edit .env: set NEW_RELIC_LICENSE_KEY (and NEW_RELIC_OTLP_ENDPOINT for EU/JP)

# build the exporter and start everything detached (the first build takes a few minutes)
docker compose up -d --build

# follow the logs if you like — Ctrl+C stops following, not the containers
docker compose logs -f

# verify IBM MQ metrics are being served (queue-manager + per-queue)
curl -s http://localhost:9157/metrics | grep -E '^ibmmq_(qmgr|queue)'
```

Cleanup: `docker compose down -v`.

> **Security.** MQ ports are published to `127.0.0.1` only — the web console
> (`:9443`, default `admin`/`passw0rd`) and listener (`:1414`) aren't reachable
> off-host. On a remote host (e.g. EC2), reach them via SSH tunnel, keep the
> security group locked down, and change the default passwords for any non-demo use.

## Use the collector against your own IBM MQ

If you already run an IBM MQ exporter (`mq_prometheus`, default `localhost:9157`),
run only the collector with [`otel-collector-config.yaml`](./otel-collector-config.yaml):

```bash
export NEW_RELIC_LICENSE_KEY="<your-key>"
export IBMMQ_EXPORTER_ENDPOINT="localhost:9157"
# export IBMMQ_CLUSTER_NAME="payments-cluster" # optional cluster tag
# export NEW_RELIC_OTLP_ENDPOINT="https://otlp.eu01.nr-data.net:4317"  # non-US region (EU/JP)

nrdot-collector --config otel-collector-config.yaml
```

The collector detects the host identity (`host.id` / `host.name`) automatically —
no host-name configuration needed.

Don't have an exporter yet? Build/run `mq_prometheus` from
[`ibm-messaging/mq-metric-samples`](https://github.com/ibm-messaging/mq-metric-samples)
pointed at your queue manager's `DEV.ADMIN.SVRCONN` (or a least-privilege
monitoring channel in production).

## Environment variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `NEW_RELIC_LICENSE_KEY` | Yes | — | New Relic ingest (license) key |
| `IBMMQ_EXPORTER_ENDPOINT` | No | `localhost:9157` | IBM MQ exporter host:port |
| `IBMMQ_CLUSTER_NAME` | No | _(absent)_ | Cluster name tag on the IBM MQ data. If unset, the tag is omitted. |
| `IBMMQ_SCRAPE_INTERVAL` | No | `60s` | Prometheus scrape interval |
| `NEW_RELIC_OTLP_ENDPOINT` | No | `https://otlp.nr-data.net:4317` | OTLP/gRPC endpoint (`https://otlp.eu01.nr-data.net:4317` EU, `https://otlp.jp.nr-data.net:4317` JP, `https://staging-otlp.nr-data.net:4317` staging) |

## Verify in New Relic

```sql
-- confirm metrics are arriving (note the underscore-style names)
FROM Metric SELECT uniques(metricName) WHERE metricName LIKE 'ibmmq_%' SINCE 10 minutes ago

-- see all the IBM MQ dimensional data points that arrived, with every attribute
FROM Metric SELECT * WHERE metricName LIKE 'ibmmq_%' SINCE 10 minutes ago
```

Then look under **All Entities → Infrastructure** for `IBMMQ_MANAGER` (one per
queue manager) and its `IBMMQ_QUEUE` children.

## How the pipeline keeps entity synthesis working

The [config](./otel-collector-config.yaml) intentionally does **not** rename
`ibmmq_*` metrics to `ibmmq.*` or rewrite labels like `qmgr`. It:

1. **Scrapes** the exporter and detects host identity (`host.id` → entity identity, `host.name`).
2. **Filters** exporter overhead (`go_*`, `process_*`, `promhttp_*`, `scrape_*`).
3. **Filters** high-cardinality system queues (`SYSTEM.*`, `AMQ.*`).
4. **Strips** a couple of collector-injected resource attributes (`service.name`,
   `url.scheme`) so metrics map to the IBM MQ entities, not the collector
   (host identity is kept).
5. **Batches** and exports via OTLP.

## Related

- [IBM MQ metric samples](https://github.com/ibm-messaging/mq-metric-samples)
- [OpenTelemetry Prometheus receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/prometheusreceiver)
- [New Relic OTLP setup](https://docs.newrelic.com/docs/opentelemetry/best-practices/opentelemetry-otlp/)
