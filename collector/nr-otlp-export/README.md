# OpenTelemetry Collector with OTLP Export to New Relic

## Introduction

This example demonstrates how to run the OpenTelemetry Collector configured to export to New Relic with the [OTLP gRPC exporter](https://github.com/open-telemetry/opentelemetry-collector/tree/main/exporter/otlpexporter) via docker.

New Relic OTLP Ingest is currently in pre-release, and requires that you [sign up](https://docs.google.com/forms/d/e/1FAIpQLSdIJVEAYaP7TXe9LmQA64yIObGvt-nOiz5kXYsjxLBbvut_1A/viewform) before use. 

The `docker-compose.yaml` file configures the collector via `otel-config.yaml`.

## Run

Set the following environment variables:
* `OTEL_EXPORTER_OTLP_ENDPOINT=<newrelic_otlp_endpoint>`
    * Replace `<newrelic_otlp_endpoint>` with the endpoint you received upon signing up for the pre-release.
* `NEW_RELIC_API_KEY=<your_license_key>`
    * Replace `<your_license_key>` with your [Account License Key](https://one.newrelic.com/launcher/api-keys-ui.launcher).

Run:
```shell
docker-compose -f docker-compose.yaml up
```

The collector is configured to accept OTLP data on port `4317`. Configure applications to export over OTLP to `http://localhost:4317` to exercise it. The collector is also configured to accept fluent forward log data via the [Fluent Forward Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/fluentforwardreceiver) on port `8006`. You can forward application logs to it using the [Fluentd logging driver](https://docs.docker.com/config/containers/logging/fluentd/).

In addition to exporting data to New Relic over OTLP, the collector is configured to export data to a logging exporter, which logs all the data it processes to standard out. This allows you to verify data is flowing.