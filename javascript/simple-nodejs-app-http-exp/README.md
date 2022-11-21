# OpenTelemetry-JS SDK New Relic Config

## Introduction

This is an example of auto-instrumenting a simple express application with [OpenTelemetry-JS](https://github.com/open-telemetry/opentelemetry-js) and exporting data to New Relic using the OTLP http/proto Exporter.

## Run

Run `npm install`.

Set the following environment variables, replacing `<your_license_key_here>` with your New Relic account ingest license key:

```shell
export OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317
export OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_license_key_here>
export OTEL_ATTRIBUTE_VALUE_LENGTH_LIMIT=4095
```

Run the application:

```shell
npm start
```

The application exposes a simple endpoint at `http://localhost:8080/ping`.

Invoke it via: `curl http://localhost:8080/ping` to generate trace data.

## View your data in the New Relic UI

The application produces trace and metric data reporting to a service named `OpenTelemetry-Node.JS-Example`.
