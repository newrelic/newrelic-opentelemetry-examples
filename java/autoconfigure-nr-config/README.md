# OpenTelemetry Autoconfigure New Relic Config

## Introduction

This project demonstrate a simple Java application using the [autoconfigure](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure) module to configure export of trace, metric, and log data to New Relic.

It uses several pieces of [standalone library instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/standalone-library-instrumentation.md) ([Runtime Metrics](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/runtime-metrics/library) and [Spring WebMVC](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/spring/spring-webmvc-3.1/library)) published by the [opentelemetry-java-instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation) project. These artifacts allow projects to add zero or low configuration instrumentation for common libraries without using the OpenTelemetry Java Agent. 

It uses the experimental [Log SDK](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk/logs) with the [Log4j2 OpenTelemetry Appender](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/log4j/log4j-appender-2.16/library). This sends logs collected by the Log4j2 API through the OpenTelemetry SDK and exports them to New Relic via OTLP.

## Run

Set the following environment variables:
* `OTEL_EXPORTER_OTLP_HEADERS=api-key=your_license_key`
  * Replace `your_license_key` with your [Account License Key](https://one.newrelic.com/launcher/api-keys-ui.launcher).
* `OTEL_METRICS_EXPORTER=otlp`
  * Enable metric export over OTLP. Metric export is disabled by default.
* `OTEL_METRIC_EXPORT_INTERVAL=5000`
  * Optionally export metrics every 5000 ms instead of the default 60s.
* `OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY=DELTA`
  * New Relic supports metrics in delta temporality, instead of the default cumulative.
* `OTEL_LOGS_EXPORTER=otlp`
  * Enable log export over OTLP. Log export is disabled by default.
* `OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317`
  * Export data to New Relic's OTLP endpoint. 
* `OTEL_EXPORTER_OTLP_COMPRESSION=gzip`
  * Gzip compression has good performance and lowers data egress.
* `OTEL_EXPERIMENTAL_EXPORTER_OTLP_RETRY_ENABLED=true`
  * Enable experimental export retry to help cope with the unreliability of the internet.
* `OTEL_SERVICE_NAME=autoconfigure-nr-config`
  * Optionally replace `autoconfigure-nr-config` with the name you wish to call your application.
* `OTEL_RESOURCE_ATTRIBUTES=service.instance.id=1234`
  * Give this application a unique instance id.
* `OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS=io.opentelemetry.sdk.extension.resources.ProcessResourceProvider`
  * Disable the `ProcessResourceProvider`, excluding the `process.command_line` resource attribute which often exceeds New Relic's maximum attribute length limit.

Run the application from a shell in the [java root](../) via:
```
export OTEL_EXPORTER_OTLP_HEADERS=api-key=your_license_key \
&& export OTEL_METRICS_EXPORTER=otlp \
&& export OTEL_METRIC_EXPORT_INTERVAL=5000 \
&& export OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY=DELTA \
&& export OTEL_LOGS_EXPORTER=otlp \
&& export OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317 \
&& export OTEL_EXPORTER_OTLP_COMPRESSION=gzip \
&& export OTEL_EXPERIMENTAL_EXPORTER_OTLP_RETRY_ENABLED=true \
&& export OTEL_SERVICE_NAME=autoconfigure-nr-config \
&& export OTEL_RESOURCE_ATTRIBUTES=service.instance.id=1234 \
&& export OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS=io.opentelemetry.sdk.extension.resources.ProcessResourceProvider

./gradlew autoconfigure-nr-config:bootRun
```

The application exposes a simple endpoint at `http://localhost:8080/ping`.

Invoke it via: `curl http://localhost:8080/ping` to generate trace, metric, and log data.

Check New Relic to confirm data is flowing.