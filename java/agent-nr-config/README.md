# OpenTelemetry Agent New Relic Config

## Introduction

This project demonstrates a simple Java application running with the OpenTelemetry Java Agent configured to write data to New Relic. New Relic expects metric data in delta aggregation temporality, whereas the default for OpenTelemetry is cumulative. Delta temporality and several other configuration options are set via [application/build.gradle](./application/build.gradle).

The project consists of two modules:

1. [application](./application): Contains a simple Spring Boot application configured to run with OpenTelemetry.
2. [config-extension](./config-extension): Contains SPI configuration code, which allows for optional additional configuration not available via environment variables. The contents are packaged as a shadow jar, which the `application` module is configured to use as an extension jar.

## Run

Set the following environment variables:
* `OTEL_EXPORTER_OTLP_HEADERS=api-key=your_license_key`
  * Replace `your_license_key` with your [Account License Key](https://one.newrelic.com/launcher/api-keys-ui.launcher).
* `OTEL_METRIC_EXPORT_INTERVAL=5000`
  * Optionally export metrics every 5000 ms instead of the default 60s.
* `OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE=DELTA`
  * New Relic supports metrics in delta temporality, instead of the default cumulative.
* `OTEL_EXPORTER_OTLP_METRICS_DEFAULT_HISTOGRAM_AGGREGATION=EXPONENTIAL_BUCKET_HISTOGRAM`
  * Use exponential histogram instead of default explicit bucket histogram for better data compression.
* `OTEL_LOGS_EXPORTER=otlp`
  * Enable log export over OTLP. Log export is disabled by default.
* `OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317`
  * Export data to New Relic's OTLP endpoint.
* `OTEL_EXPORTER_OTLP_COMPRESSION=gzip`
  * Gzip compression has good performance and lowers data egress.
* `OTEL_EXPERIMENTAL_EXPORTER_OTLP_RETRY_ENABLED=true`
  * Enable experimental export retry to help cope with the unreliability of the internet.
* `OTEL_SERVICE_NAME=agent-nr-config`
  * Optionally replace `agent-nr-config` with the name you wish to call your application.
* `OTEL_RESOURCE_ATTRIBUTES=service.instance.id=1234`
  * Give this application a unique instance id.
* `OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS=io.opentelemetry.sdk.extension.resources.ProcessResourceProvider`
  * Disable the `ProcessResourceProvider`, excluding the `process.command_line` resource attribute which often exceeds New Relic's maximum attribute length limit.
* `OTEL_SPAN_ATTRIBUTE_VALUE_LENGTH_LIMIT=4095`
  * New relic disallows attributes whose length exceeds 4095 characters.

Additional configuration using standard autoconfiguration environment variables defined in the [autoconfigure module](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure).

Run the application from a shell in the [java root](../) via:
```
export OTEL_EXPORTER_OTLP_HEADERS=api-key=your_license_key \
&& export OTEL_METRIC_EXPORT_INTERVAL=5000 \
&& export OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE=DELTA \
&& export OTEL_EXPORTER_OTLP_METRICS_DEFAULT_HISTOGRAM_AGGREGATION=EXPONENTIAL_BUCKET_HISTOGRAM \
&& export OTEL_LOGS_EXPORTER=otlp \
&& export OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317 \
&& export OTEL_EXPORTER_OTLP_COMPRESSION=gzip \
&& export OTEL_EXPERIMENTAL_EXPORTER_OTLP_RETRY_ENABLED=true \
&& export OTEL_SERVICE_NAME=agent-nr-config \
&& export OTEL_RESOURCE_ATTRIBUTES=service.instance.id=1234 \
&& export OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS=io.opentelemetry.sdk.extension.resources.ProcessResourceProvider \
&& export OTEL_SPAN_ATTRIBUTE_VALUE_LENGTH_LIMIT=4095

./gradlew agent-nr-config:application:bootRun
```

The `bootRun` command will:
- Download the OpenTelemetry Java agent.
- Build the `config-extension` shadow jar.
- Build the application executable jar.
- Run the application executable jar with jvmArgs that attach the OpenTelemetry Java agent. See the `bootRun` task config in [./application/build.gradle](./application/build.gradle) to see the jvmArg configuration.

The application exposes a simple endpoint at `http://localhost:8080/ping`.

Invoke it via: `curl http://localhost:8080/ping` to generate trace and metric data.

Check your backend to confirm data is flowing.
