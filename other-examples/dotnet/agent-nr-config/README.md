# OpenTelemetry Automatic Instrumentation (Agent) New Relic Config

## Introduction

This project demonstrates a simple .NET application running with the OpenTelemetry Automatic Instrumentation (Agent) configured to write data to New Relic. Docker is leveraged so that it is easier to demonstrate the following and make it easier to run the example:

- Where to download the agent.
- How to install the agent.
- How to configure the agent.
- How to build and run the application being monitored by the agent.

The project consists of two main components:

1. The default .NET Weather Forecast example application. ***This application does not directly contain any OpenTelemetry code or references, because the agent automatically instruments the application***.
2. [Dockerfile](./Dockerfile): Contains all of the setup code to download, install, and configure the agent so that it will monitor the example application when it is run within the container.

## Run

This example only requires setting the environment variable:

* `OTEL_EXPORTER_OTLP_HEADERS="api-key=<your_license_key>"`
  * Replace `<your_license_key>` with your [Account License Key](https://one.newrelic.com/launcher/api-keys-ui.launcher).

The other settings below have default values defined in the [Dockerfile](./Dockerfile), but can otherwise be overwritten/manually set:
* `OTEL_EXPORTER_OTLP_ENDPOINT="https://otlp.nr-data.net"`
* `OTEL_EXPORTER_OTLP_PROTOCOL="http/protobuf"`
* `OTEL_SERVICE_NAME="my-test-service"`
  * Replace `my-test-service` with the name you wish to call the application.

Additional configuration can be controlled using environment variables defined in the [Automatic Instrumentation config document](https://github.com/open-telemetry/opentelemetry-dotnet-instrumentation/blob/2f5e1fc2b30f444944966393fba1d2d45a69f08b/docs/config.md).

Run the application from a shell in the `agent-nr-config` directory via:
```
docker build . -t agent-nr-config
docker run -d -p 8080:80 -e OTEL_EXPORTER_OTLP_HEADERS --name agent-nr-config agent-nr-config
```

The application exposes a simple endpoint at `http://localhost:8080/WeatherForecast`.

Invoke it via: `curl http://localhost:8080/WeatherForecast` to generate trace data.

Check your [New Relic account](https://one.newrelic.com) to confirm data is flowing. For EU users check your [account here](https://one.eu.newrelic.com).
