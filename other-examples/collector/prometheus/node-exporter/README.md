# Monitoring Node Exporter with OpenTelemetry Collector

This example demonstrates monitoring host-level system metrics using [Prometheus Node Exporter](https://github.com/prometheus/node_exporter) with the [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/), using the [Prometheus receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/prometheusreceiver) and sending the data to New Relic via OTLP.

Node Exporter exposes hardware and OS metrics (CPU, memory, disk, network, etc.) from a Linux host on port `9100`. The OpenTelemetry Collector scrapes these metrics and enriches them with host resource attributes before forwarding to New Relic.

## Requirements

* [Prometheus Node Exporter](https://github.com/prometheus/node_exporter/releases) installed and running on the host
* [OpenTelemetry Collector Contrib](https://github.com/open-telemetry/opentelemetry-collector-contrib/releases) installed on the host
* [A New Relic account](https://one.newrelic.com/)
* [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Steps

### 1. Install Node Exporter

Before proceeding, ensure [Prometheus Node Exporter](https://github.com/prometheus/node_exporter/releases) is installed and running on your host, exposing metrics on port `9100`.

### 2. Configure the OpenTelemetry Collector

The [config.yaml](./config.yaml) file in this directory is ready to use. Before running the collector, replace the placeholder license key with your actual New Relic license key:

```yaml
exporters:
  otlphttp:
    endpoint: https://otlp.nr-data.net:4318
    headers:
      api-key: YOUR_NEW_RELIC_LICENSE_KEY  # <-- Replace this
```

See the [New Relic docs](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key) for how to obtain a license key.

> **EU accounts:** Change the endpoint to `https://otlp.eu01.nr-data.net:4318`.

**Cloud provider resource detection (optional)**

The `resourcedetection` processor is pre-configured to detect the hostname from the OS. If your host runs on a cloud provider, uncomment the relevant block in [config.yaml](./config.yaml) and add the detector to the `detectors` list:

| Cloud     | Detector name | Adds                   |
|-----------|---------------|------------------------|
| AWS EC2   | `ec2`         | `host.id` from EC2     |
| GCP       | `gcp`         | `host.id` from GCP     |
| Azure     | `azure`       | `host.id` from Azure   |

For example, for AWS EC2:

```yaml
processors:
  resourcedetection:
    detectors: ["ec2", "system"]
    ec2:
      resource_attributes:
        host.id:
          enabled: true
```

### 3. Run the OpenTelemetry Collector

Start the collector with the provided config:

```shell
otelcol-contrib --config config.yaml
```

The `debug` exporter will print scraped metrics to stdout so you can confirm data is flowing before it reaches New Relic.

## Viewing your data

Once the collector is running, navigate to **New Relic → Query Your Data** and run the following NRQL query to list the Node Exporter metrics being reported:

```sql
FROM Metric SELECT uniques(metricName)
WHERE otel.library.name = 'otelcol/prometheusreceiver'
AND job = 'node-exporter'
LIMIT MAX
```

To explore host-level metrics such as CPU usage:

```sql
FROM Metric SELECT average(node_cpu_seconds_total)
FACET mode, cpu
WHERE job = 'node-exporter'
TIMESERIES
```

See [get started with querying](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) for additional details on querying data in New Relic.

## Configuration overview

| Component           | Purpose                                                                                          |
|---------------------|--------------------------------------------------------------------------------------------------|
| `prometheus` receiver | Scrapes metrics from Node Exporter at `localhost:9100` every 10 seconds                       |
| `resourcedetection` processor | Detects host metadata (hostname, host ID) from the OS or cloud provider          |
| `transform` processor | Stamps each datapoint with `instrumentation.provider`, `instance`, and `instanceid` attributes |
| `batch` processor   | Batches telemetry before export to improve throughput                                            |
| `debug` exporter    | Prints detailed metric output to stdout for local verification                                  |
| `otlphttp` exporter | Sends metrics to New Relic via OTLP/HTTP                                                        |
| `health_check` extension | Exposes a health check endpoint on `http://localhost:13133` for liveness probes           |

## Additional notes

* Node Exporter exposes a large number of metrics by default. To reduce data ingest, consider using the [`--collector.disable-defaults`](https://github.com/prometheus/node_exporter#filtering-enabled-collectors) flag combined with selectively enabling only the collectors you need.
* The `transform` processor sets `instrumentation.provider=prometheus` so that Node Exporter metrics can be easily distinguished from other OpenTelemetry sources in New Relic.
* The `instance` and `instanceid` attributes are propagated from host resource attributes to datapoint attributes, enabling per-host filtering in New Relic dashboards.
