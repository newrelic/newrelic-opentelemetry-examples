# Getting Started Guide - Java

This is a simple application instrumented
with [OpenTelemetry Java's](https://github.com/open-telemetry/opentelemetry-java) [automatic instrumentation javaagent](https://opentelemetry.io/docs/languages/java/automatic/).
It demonstrates how to configure OpenTelemetry Java to send data to New Relic.

## Requirements

* Java JDK 21+, due to the use of Spring Boot 4; [Java 8+ otherwise](https://github.com/open-telemetry/opentelemetry-java/blob/main/VERSIONING.md#language-version-compatibility)
* [A New Relic account](https://one.newrelic.com/)
* [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Running the application

1. Set the following environment variables to configure OpenTelemetry to send
   data to New Relic:

    ```shell
    export OTEL_SERVICE_NAME=getting-started-java
    export OTEL_EXPERIMENTAL_EXPORTER_OTLP_RETRY_ENABLED=true
    export OTEL_EXPORTER_OTLP_METRICS_DEFAULT_HISTOGRAM_AGGREGATION=BASE2_EXPONENTIAL_BUCKET_HISTOGRAM
    export OTEL_EXPERIMENTAL_RESOURCE_DISABLED_KEYS=process.command_args
    export OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net
    export OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_license_key>
    export OTEL_ATTRIBUTE_VALUE_LENGTH_LIMIT=4095
    export OTEL_EXPORTER_OTLP_COMPRESSION=gzip
    export OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
    export OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE=delta
    ```

    * If your account is based in the EU, set the endpoint to: [https://otlp.eu01.nr-data.net](https://otlp.eu01.nr-data.net)

2. Run the application with the following command and open
   [http://localhost:8080/fibonacci?n=1](http://localhost:8080/fibonacci?n=1)
   in your web browser to ensure it is working.

    ```shell
    ./gradlew bootRun
    ```

3. Experiment with providing different values for `n` in the query string.
   Valid values are between 1 and 90. Values outside this range cause an error
   which will show up in New Relic.

## Running with Docker: stable vs. legacy JVM conventions

The OpenTelemetry Java agent changed its JVM metric namespace between 1.x and
2.x. Version 1.x emits the legacy `process.runtime.jvm.*` metrics; 2.x emits
the stable `jvm.*` metrics. The Docker build accepts an `OTEL_AGENT_VERSION`
build arg so you can produce an image for either.

Set the same environment variables listed above, then:

```shell
# Stable conventions (jvm.*) — uses the 2.x agent by default
docker build -t getting-started-java:stable .
docker run --rm -p 8080:8080 \
  -e OTEL_SERVICE_NAME=otel-java-2.x \
  -e OTEL_EXPERIMENTAL_EXPORTER_OTLP_RETRY_ENABLED \
  -e OTEL_EXPORTER_OTLP_METRICS_DEFAULT_HISTOGRAM_AGGREGATION \
  -e OTEL_EXPERIMENTAL_RESOURCE_DISABLED_KEYS \
  -e OTEL_EXPORTER_OTLP_ENDPOINT -e OTEL_EXPORTER_OTLP_HEADERS \
  -e OTEL_ATTRIBUTE_VALUE_LENGTH_LIMIT -e OTEL_EXPORTER_OTLP_COMPRESSION \
  -e OTEL_EXPORTER_OTLP_PROTOCOL -e OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE \
  getting-started-java:stable

# Legacy conventions (process.runtime.jvm.*) — pin the last 1.x release.
# Map to a different host port (e.g. 8081) so it can run alongside the stable image.
docker build --build-arg OTEL_AGENT_VERSION=1.33.6-alpha -t getting-started-java:legacy .
docker run --rm -p 8081:8080 \
  -e OTEL_SERVICE_NAME=otel-java-1.x \
  -e OTEL_EXPERIMENTAL_EXPORTER_OTLP_RETRY_ENABLED \
  -e OTEL_EXPORTER_OTLP_METRICS_DEFAULT_HISTOGRAM_AGGREGATION \
  -e OTEL_EXPERIMENTAL_RESOURCE_DISABLED_KEYS \
  -e OTEL_EXPORTER_OTLP_ENDPOINT -e OTEL_EXPORTER_OTLP_HEADERS \
  -e OTEL_ATTRIBUTE_VALUE_LENGTH_LIMIT -e OTEL_EXPORTER_OTLP_COMPRESSION \
  -e OTEL_EXPORTER_OTLP_PROTOCOL -e OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE \
  getting-started-java:legacy
```

The two `OTEL_SERVICE_NAME` values above (`otel-java-2.x` and `otel-java-1.x`)
make the runs show up as separate entities in New Relic so you can compare
their JVM metrics side by side.

Once both are running, drive sustained traffic against each port for several
minutes so JVM metric histograms accumulate and dashboards populate. Mix valid
and invalid `n` values to exercise both the success and error paths:

```shell
for port in 8080 8081; do
  (for i in $(seq 1 300); do
     n=$(( RANDOM % 10 == 0 ? (RANDOM % 2 == 0 ? 0 : 100) : (RANDOM % 90) + 1 ))
     curl -s -o /dev/null "http://localhost:${port}/fibonacci?n=${n}"
     sleep 1
   done) &
done
wait
```
