# ASP.NET Core service instrumented with OpenTelemetry

This example demonstrates how to instrument and configure a simple ASP.NET Core
application with
[OpenTelemetry .NET](https://github.com/open-telemetry/opentelemetry-dotnet).

## Prerequisites
* Install the [.NET SDK](https://dotnet.microsoft.com/download)

## Run the application

Set the following environment variables:
* `OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317`
* `OTEL_EXPORTER_OTLP_HEADERS="api-key=<your_license_key>"`
  * Replace `<your_license_key>` with your [Account License Key](https://one.newrelic.com/launcher/api-keys-ui.launcher).

Run:
```shell
dotnet run
```

The application serves up a single endpoint accessible at https://localhost:5001/WeatherForecast.

## View your data in New Relic

The application produces trace data reporting to a service named `OpenTelemetry-Dotnet-Example`.
