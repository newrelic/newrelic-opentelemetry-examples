# OpenTelemetry Agent New Relic Config

## Introduction

This project demonstrates a simple Java application running with the OpenTelemetry Java Agent configured to write data to New Relic. New Relic expects metric data in delta aggregation temporality, whereas the default for OpenTelemetry is cumulative. Delta temporality and several other configuration options are set via [application/build.gradle](./application/build.gradle).

The project consists of two modules:

1. [application](./application): Contains a simple Spring Boot application configured to run with OpenTelemetry.
2. [config-extension](./config-extension): Contains SPI configuration code, which allows for optional additional configuration not available via environment variables. The contents are packaged as a shadow jar, which the `application` module is configured to use as an extension jar.

## Run

Set the following environment variables:
* `OTEL_EXPORTER_OTLP_ENDPOINT="https://otlp.nr-data.net:4317"`
* `OTEL_EXPORTER_OTLP_HEADERS="api-key=<your_license_key>"`
  * Replace `<your_license_key>` with your [Account License Key](https://one.newrelic.com/launcher/api-keys-ui.launcher).
* `OTEL_RESOURCE_ATTRIBUTES="service.name=my-test-service"`
  * Replace `my-test-service` with the name you wish to call the application.

Additional configuration using standard autoconfiguration environment variables defined in the [autoconfigure module](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure).

Run the application from a shell in the [java root](../) via:
```
./gradlew agent-nr-config:application:bootRun
```

The `bootRun` command will:
- Download the OpenTelemetry Java agent.
- Build the `config-extension` shadow jar.
- Build the application executable jar.
- Run the application executable jar with jvmArgs that attach the OpenTelemetry Java agent. See the `bootRun` task config in [./application/build.gradle](./application/build.gradle) to see the jvmArg environment variable configuration.

The application exposes a simple endpoint at `http://localhost:8080/ping`.

Invoke it via: `curl http://localhost:8080/ping` to generate trace and metric data.

Check your backend to confirm data is flowing.
