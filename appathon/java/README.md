# Summary

Java implementation of appathon app.

## Run via docker

No dependencies needed besides docker.

Replace `<your_license_key>` with your New Relic license key.

```shell
docker build -t appathon-java . \
  && docker run -p 8080:8080 \
    --env OTEL_SERVICE_NAME=appathon-java \
    --env OTEL_RESOURCE_ATTRIBUTES=service.instance.id=6d1331ab-cfd3-4fff-9dfe-21e5cbfab49a \
    --env OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317 \
    --env OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_license_key> \
    --env OTEL_EXPORTER_OTLP_COMPRESSION=gzip \
    --env OTEL_SPAN_ATTRIBUTE_VALUE_LENGTH_LIMIT=1000 \
    appathon-java
```

## Run via JDK

Requires java 11 to be installed. 

Replace `<your_license_key>` with your New Relic license key.

```shell
export OTEL_SERVICE_NAME=appathon-java
export OTEL_RESOURCE_ATTRIBUTES=service.instance.id=6d1331ab-cfd3-4fff-9dfe-21e5cbfab49a
export OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317
export OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_license_key>
export OTEL_EXPORTER_OTLP_COMPRESSION=gzip
export OTEL_SPAN_ATTRIBUTE_VALUE_LENGTH_LIMIT=1000

./gradlew bootRun
```