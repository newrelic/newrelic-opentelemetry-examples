# Minimal API ASP.NET Core service instrumented with OpenTelemetry

Demonstration of .NET 6 minimal APIs with New Relic's OpenTelemetry harvesting.

## Prerequisites

- [.NET 6](https://dotnet.microsoft.com/en-us/download/dotnet/6.0)
- [Docker](https://docs.docker.com/get-docker/)

## Run the application

Set the following environment variables:
* `OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317`
* `OTEL_EXPORTER_OTLP_HEADERS="api-key=<your_license_key>"`
  * Replace `<your_license_key>` with your [Account License Key](https://one.newrelic.com/launcher/api-keys-ui.launcher).

Run:
```shell
dotnet run
```

OR
```shell
docker compose up
```

The application serves up a single endpoint accessible at http://localhost:8080/fruits and a Swagger experience accessible at http://localhost:8080/swagger.

## View your data in New Relic

The application produces trace and metric data reporting to a service named `minimal-api`.
