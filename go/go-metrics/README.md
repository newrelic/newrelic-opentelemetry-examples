# Sending metrics using the OpenTelemetry Go SDK

## Introduction

This project demonstrates how to send metrics to New Relic using the [OpenTelemetry Go SDK](https://github.com/open-telemetry/opentelemetry-go). New Relic requires that the SDK be configured to use a delta aggregation temporality. By default, the SDK uses a cumulative aggregation temporality. For more information about aggregation temporatility see [our documentation](https://docs.newrelic.com/docs/integrations/open-source-telemetry-integrations/opentelemetry/opentelemetry-advanced-configuration/).

*TODO: Move the following to the docs site and update the docs link above.*

In OpenTelemetry [metric instruments](https://github.com/open-telemetry/opentelemetry-specification/blob/a4b08e2a9eabe6c8bd55739c3dd5538baae120d5/specification/metrics/api.md#metric-instruments) are the tools used to record raw measurements. Each type of metric instrument has a default aggregation defining how raw measurements are aggregated. For example, the [counter](https://github.com/open-telemetry/opentelemetry-specification/blob/a4b08e2a9eabe6c8bd55739c3dd5538baae120d5/specification/metrics/api.md#counter) instrument is used for recording the number of occurences of some event over a period of time. Raw values recorded using a counter use a `sum` aggregation, by default. That is, recorded measurments are summed together over an interval. In addition to this, a metric data point has a notion of an [aggregation temporality](https://github.com/open-telemetry/opentelemetry-specification/blob/a4b08e2a9eabe6c8bd55739c3dd5538baae120d5/specification/metrics/datamodel.md#temporality) which indicates whether reported values incorporate previous measurements (cumulative aggregation temporality), or not (delta aggregation temporality).

## Run

The application is configured to export data via OTLP to a collector running at `http://localhost:4317`.

You can adjust where data is exported to, or you can run a collector instance locally via docker by following the [nr-otlp-export](../../collector/nr-otlp-export/README.md) example.

After running the collector, run the application:
```
go run main.go
```
