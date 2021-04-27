# OpenTelemetry Collector with New Relic Exporter

## Introduction

This example demonstrates how to run the OpenTelemetry Collector configured with the [New Relic exporter](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/exporter/newrelicexporter) via docker.

The `docker-compose.yaml` file configures the collector via `otel-config.yaml`.

## Run

To run, first add a New Relic API key to the `NEW_RELIC_API_KEY` environment variable in `docker-compose.yaml`. This will add the API key to all outbound requests to New Relic.

Then, run the collector via:

```shell
docker-compose -f docker-compose.yaml up
```

The collector is configured to accept OTLP data on port `4317`. Configure applications to export over OTLP to `http://localhost:4317` to exercise it.

In addition to exporting data to New Relic, the collector is configured to export data to a logging exporter, which logs all the data it processes to standard out. This allows you to verify data is flowing.