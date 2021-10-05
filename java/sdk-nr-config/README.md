# OpenTelemetry SDK New Relic Config

## Introduction

This project demonstrates a simple Java application with custom OpenTelemetry instrumentation configured to write data to New Relic. New Relic expects metric data to delta aggregation temporality, whereas the default for OpenTelemetry is cumulative.

It also uses several pieces of [standalone library instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/standalone-library-instrumentation.md) ([Runtime Metrics](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/runtime-metrics/library) and [Spring WebMVC](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/spring/spring-webmvc-3.1/library)) published by the [opentelemetry-java-instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation) project. These artifacts allow projects to add zero or low configuration instrumentation for common libraries without using the OpenTelemetry Java Agent. 

## Run

Set the following environment variables:
* `NEW_RELIC_LICENSE_KEY=<your_license_key>`
  * Replace `<your_license_key>` with your [Account License Key](https://one.newrelic.com/launcher/api-keys-ui.launcher).
* Optional `OTLP_HOST=http://your-collector:4317`
  * The application is [configured](../shared-utils/src/main/java/com/newrelic/shared/OpenTelemetryConfig.java) to export to New Relic via OTLP by default. Optionally change it by setting this environment variable.

Run the application from a shell in the [java root](../) via:
```
./gradlew sdk-nr-config:bootRun
```

The application exposes a simple endpoint at `http://localhost:8080/ping`.

Invoke it via: `curl http://localhost:8080/ping` to generate trace and metric data.

Check your backend to confirm data is flowing.