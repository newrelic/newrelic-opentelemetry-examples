# OTLP (OpenTelemetry Line Protocol) Load Generator

## Introduction

This project contains a Java application that generates OpenTelemetry span and metric data and exports it over OTLP. The application can be run in a shell or as a docker container.

It currently generates the following data in line with the OpenTelemetry specification:
- Spans and metrics that simulate an HTTP server 
- Spans that simulate a gRPC server

## Run

The application is configured to export data via OTLP to a collector running at `http://localhost:4317`. This can be changed by specifying an alternative via `OTLP_HOST` environment variable:
```shell
export OTLP_HOST=http://my-collector-host:4317
```

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

Check your collector logs to confirm data is flowing.