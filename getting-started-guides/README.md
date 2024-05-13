# Getting Started with New Relic using OpenTelemetry

The examples within this directory demonstrate how to send data to New Relic
using OpenTelemetry.

Each language directory illustrates how to add OpenTelemetry instrumentation to
a simple web application and configure OpenTelemetry for an optimal New Relic
experience. This includes exporting over OTLP, limiting attributes according to
New Relic ingest limits, and more.

To get started quickly, you can use Docker Compose to spin up all the example
applications.

1. First, open the [.env](./.env) file and configure your New Relic API key.
   If necessary, also change the
   [New Relic OTLP endpoint](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/best-practices/opentelemetry-otlp/#configure-endpoint-port-protocol)
   to match your region and needs.

2. Then, run Docker Compose

    ```shell
    docker compose up --build
    ```

3. Lastly, go view your data in New Relic. Running using Docker Compose also
   starts a simple load generator, so data should be flowing.
