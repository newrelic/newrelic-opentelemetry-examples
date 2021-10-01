# OTLP (OpenTelemetry Line Protocol) Load Generator

## Introduction

This project contains a Java application that generates OpenTelemetry span and metric data and exports it over OTLP. The application can be run in a shell or as a docker container.

It currently generates the following data in line with the OpenTelemetry specification:
- Spans and metrics that simulate an HTTP server 
- Spans that simulate a gRPC server
- Spans that simulate a Kafka consumer

## Run

Set the following environment variables:
* `NEW_RELIC_LICENSE_KEY=<your_license_key>`
  * Replace `<your_license_key>` with your [Account License Key](https://one.newrelic.com/launcher/api-keys-ui.launcher).
* Optional `OTLP_HOST=http://your-collector:4317`
  * The application is [configured](../shared-utils/src/main/java/com/newrelic/shared/OpenTelemetryConfig.java) to export to New Relic via OTLP by default. Optionally change it by setting this environment variable. 

To run from a shell, execute the following from a shell in the [java root](../):
```shell
./gradlew otlp-load-generator:run
```

To run from a docker container, execute the following from a shell in the [java root](../):
```shell
./gradlew otlp-load-generator:build
docker build -t otlp-load-generator:latest ./otlp-load-generator
docker run otlp-load-generator:latest
```

Check your backend to confirm data is flowing.