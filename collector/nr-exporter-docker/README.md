# OpenTelemetry Collector with New Relic Exporter

## Introduction

This example demonstrates how to run the OpenTelemetry Collector configured with the [New Relic exporter](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/exporter/newrelicexporter) via docker.

The `docker-compose.yaml` file configures the collector via `otel-config.yaml`.

## Run

To run, export your New Relic API key as an environment variable `NEW_RELIC_API_KEY` environment variable, which is referenced in `docker-compose.yaml`. This will add the API key to all outbound requests to New Relic. Then run the collector via `docker-compose`:

```shell
export NEW_RELIC_API_KEY=<INSERT-API-KEY-HERE>
docker-compose -f docker-compose.yaml up
```

The collector is configured to accept OTLP data on port `4317`. Configure applications to export over OTLP to `http://localhost:4317` to exercise it. The collector is also configured to accept fluent forward log data via the [Fluent Forward Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/fluentforwardreceiver) on port `8006`. You can forward application logs to it using the [Fluentd logging driver](https://docs.docker.com/config/containers/logging/fluentd/).

In addition to exporting data to New Relic, the collector is configured to export data to a logging exporter, which logs all the data it processes to standard out. This allows you to verify data is flowing.