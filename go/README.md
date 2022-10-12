# OpenTelemetry Go example

This example demonstrates how to instrument and configure a simple Go
application with
[OpenTelemetry Go](https://github.com/open-telemetry/opentelemetry-go).

## Run the application

Set the following environment variables:

* `OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317`
* `OTEL_EXPORTER_OTLP_HEADERS="api-key=<your_license_key>"`
  * Replace `<your_license_key>` with your
    [Account License Key](https://one.newrelic.com/launcher/api-keys-ui.launcher).

Run:

```shell
go run .
```

## View your data in New Relic

The application produces trace and metric data reporting to a service named `OpenTelemetry-Go-Example`.
