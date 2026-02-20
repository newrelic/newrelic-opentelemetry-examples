# OpenTelemetry Collector Internal Telemetry - Docker Compose

This example demonstrates how to run the OpenTelemetry Collector with internal telemetry monitoring using Docker Compose.

## Requirements

- [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/)
- [A New Relic account](https://one.newrelic.com/)
- [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Running the example

1. Create your `.env` file from the template and update the values:
    ```shell
    cp .env.template .env
    # Edit .env with your actual API key, OTLP endpoint, and service name
    ```

2. Start the collector:
    ```shell
    docker compose up -d
    ```

3. View logs:
    ```shell
    docker compose logs -f collector
    ```

4. When finished, stop and remove the container:
    ```shell
    docker compose down
    ```

## Viewing your data

Navigate to "New Relic -> All Entities -> Services - OpenTelemetry" and find the service with the name corresponding to the value you set for `SERVICE_NAME` in your `.env` file.

## Configuration

The collector configuration is in `config.yaml`. It configures the collector's internal telemetry to export:
- **Metrics**: Detailed collector performance metrics
- **Logs**: Collector operational logs (with sampling)
- **Traces**: Disabled by default (set `level: basic` to enable)

All telemetry is exported directly to New Relic via OTLP without requiring any receivers, processors, or exporters to be defined in the configuration.
