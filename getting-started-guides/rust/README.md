# Getting Started Guide - Rust

This is a simple application instrumented with [OpenTelemetry Rust](https://github.com/open-telemetry/opentelemetry-rust).
It demonstrates how to configure OpenTelemetry Rust to send data to New Relic.

## Requirements

* [Rust](https://www.rust-lang.org/tools/install)
* [A New Relic account](https://one.newrelic.com/)
* [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Running the application

1. Set the following environment variables to configure OpenTelemetry to send
   data to New Relic:

    ```shell
    export OTEL_SERVICE_NAME=getting-started-rust
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
    cargo run fibonacci
    ```

3. Experiment with providing different values for `n` in the query string.
   Valid values are between 1 and 90. Values outside this range cause an error
   which will show up in New Relic.
