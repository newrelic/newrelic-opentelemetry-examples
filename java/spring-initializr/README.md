# OpenTelemetry Spring Initializr

## Introduction

This example demonstrates a basic [Spring Initializr](https://start.spring.io/) project that has been modified to include OpenTelemetry instrumentation.

This [link](https://start.spring.io/#!type=gradle-project&language=java&platformVersion=2.5.6&packaging=jar&jvmVersion=11&groupId=com.example&artifactId=demo&name=demo&description=Demo%20project%20for%20Spring%20Boot&packageName=com.example.demo&dependencies=web) allows you to create a project with the same initial configuration.

## Run

Set the following environment variables:
* `OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317`
  * Configure the application to export over OTLP to New Relic's OTLP endpoint.
* `OTEL_METRICS_EXPORTER=otlp`
  * Configure the application to export metrics over OTLP. Metric export is disabled by default.
* `OTEL_IMR_EXPORT_INTERVAL=1000`
  * Configure the application to export metrics every second (1000ms) instead of the default 60 seconds.
* `OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_license_key>`
  * Configure the application to include a New Relic license key on export requests. Replace `<your_license_key>` with your [Account License Key](https://one.newrelic.com/launcher/api-keys-ui.launcher).
* `OTEL_RESOURCE_ATTRIBUTES=service.name=<your_service_name>,service.instance.id=<unique_service_instance_id>`
  * Configure the application to add resource attributes for the service name and id. Replace `<your_service_name>` with the name of your service. Replace `<unique_service_instance_id>` with a unique identifier for the instance of your service.

Run the application from a shell in the [spring-initializr root](./) via:
```
./gradlew bootRun
```
