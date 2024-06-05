# Monitoring Redis with OpenTelemetry Collector

This simple example demonstrates monitoring redis with the [OpenTelemetry collector](https://opentelemetry.io/docs/collector/), using the [redis receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/redisreceiver) and sending the data to New Relic via OTLP.

## Requirements

* [Docker compose](https://docs.docker.com/compose/) must be installed and the docker daemon must be running.
* [A New Relic account](https://one.newrelic.com/)
* [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Running the application

1. Set the following environment variables to configure OpenTelemetry to send
   data to New Relic:

    ```shell
    export OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net
    export OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_license_key>
    ```

    * If your account is based in the EU, set the endpoint to: [https://otlp.eu01.nr-data.net](https://otlp.eu01.nr-data.net)

2. Run the application with the following command.

    ```shell
    docker compose up
    ```

## Viewing your data

To review your redis data in New Relic, navigate to "New Relic -> All Entities -> Redis instances" and click on the instance with name "redis" to view the instance summary. Click on "Metric explorer" to view all metrics associated with the redis instance, or use [NRQL](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) to perform ad-hoc analysis.

## Additional notes

This example monitors a redis instance defined in [docker-compose.yaml](docker-compose.yaml), which is not receiving any load. To use in production, you'll need to modify the `.receivers.redis.endpoint` value in [collector.yaml](./collector.yaml) to point to the endpoint of your redis instance.
