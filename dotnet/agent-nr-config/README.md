# OpenTelemetry Automatic Instrumentation (Agent) New Relic Config

## Introduction

This project demonstrates a simple .NET application running with the OpenTelemetry Automatic Instrumentation (Agent) configured to write data to New Relic. Docker is leveraged so that it is easier to demonstrate the following and make it easier to run the example:

- Where to dowload the agent.
- How to install the agent.
- How to configure the agent.
- How to build and run the application being monitored by the agent.

The project consists of two main components:

1. The default .NET Weather Forecast example application. This application does not directly contain any OpenTelemetry code or references.
2. [Dockerfile](./Dockerfile): Contains the code to install, configure, and run the application while being monitored by the agent.

## Run

Set the following environment variables:
* `OTEL_EXPORTER_OTLP_ENDPOINT="https://otlp.nr-data.net:4318"`
* `OTEL_EXPORTER_OTLP_HEADERS="api-key=<your_license_key>"`
  * Replace `<your_license_key>` with your [Account License Key](https://one.newrelic.com/launcher/api-keys-ui.launcher).
* `OTEL_SERVICE_NAME="my-test-service"`
  * Replace `my-test-service` with the name you wish to call the application.

The example only requires setting the `OTEL_EXPORTER_OTLP_HEADERS` environment variable. The other settings have default values defined in the [Dockerfile](./Dockerfile). 

Additional configuration can be controlled using environment variables defined in the [Automatic Instrumentation config document](https://github.com/open-telemetry/opentelemetry-dotnet-instrumentation/blob/47f16b5748218f37dd9bd543a0f133670904f9f7/docs/config.md).

Run the application from a shell in the `agent-nr-config` directory via:
```
docker build . -t agent-nr-config
docker run -d -p 8080:80 -e OTEL_EXPORTER_OTLP_HEADERS --name agent-nr-config agent-nr-config
```

The application exposes a simple endpoint at `http://localhost:8080/WeatherForecast`.

Invoke it via: `curl http://localhost:8080/WeatherForecast` to generate trace data.

Check your backend to confirm data is flowing.
