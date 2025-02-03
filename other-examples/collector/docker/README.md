# Monitoring Docker with OpenTelemetry Collector

This simple example demonstrates monitoring docker with the [OpenTelemetry collector](https://opentelemetry.io/docs/collector/), using the [docker stats receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/dockerstatsreceiver) and sending the data to New Relic via OTLP. An OpenTelemetry APM service also runs, and its container is monitored with the docker stats receiver. 

The OpenTelemetry APM service automatically detects and reports [container resource attributes](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/resource/container.md) which New Relic uses to create a relationship between the container and APM service entities.

## Requirements

* A linux machine with docker daemon and docker compose (docker stats receiver only supports Linux).
* [A New Relic account](https://one.newrelic.com/)
* [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Running the example

1. Update the `NEW_RELIC_API_KEY` value in [.env](./.env) to your New Relic license key.

    ```
    # New Relic API key to authenticate the call.
    # docs: https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key
    NEW_RELIC_API_KEY=
    ```
   
    * Note, be careful to avoid inadvertent secret sharing when modifying `.env`. To ignore changes to this file from git, run `git update-index --skip-worktree .env`.

    * If your account is based in the EU, update the `NEW_RELIC_OTLP_ENDPOINT` value in `.env` to: [https://otlp.eu01.nr-data.net](https://otlp.eu01.nr-data.net)

    ```
    # ...omitted for brevity
    # The default US endpoint is set here. You can change the endpoint and port based on your requirements if needed.
    # docs: https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/get-started/opentelemetry-set-up-your-app/#review-settings
    NEW_RELIC_OTLP_ENDPOINT=https://otlp.nr-data.net/
    ```

2. Update the `HOST_USER_ID` to the id of a user with permission to access the docker socket.

   The docker stats receiver reads from docker daemon socket. This example runs the collector and docker stats receiver inside a docker container, and makes the host docker socket accessible to the container by mounting a volume with `/var/run/docker.sock` from the host machine. By default, the collector contrib image runs with a user with limited permissions. We must override the user and run it with permission to access the docker socket.

   Update the `HOST_USER_ID` value in [.env](./.env) to a user with required permissions. It's set to `0` by default, which corresponds to the root user. Its good practice to run with a user with more limited access in production.

    ```
    # ...omitted for brevity
    # User ID used to run the collector. Must have permission to access the docker socket.
    # Obtain for a given user via "id -g <username>"
    HOST_USER_ID=0
    ```

3. Run the application with the following command.

    ```shell
    docker compose up
    ```
   
   * Optionally include `-d` to run in the background.
   
   * When finished, cleanup resources by exiting the command with `Ctrl-D` or `Ctrl-C`. If running in the background, run the following command to stop containers.

   ```shell
   docker compose stop
   ```

## Viewing your data

To review your docker data in New Relic, navigate to "New Relic -> All Entities -> Containers". You should see entities named `docker-collector` and `docker-adservice` corresponding to the services defined in `docker-compose.yaml`. Click to view the container summary.

To review your OpenTelemetry APM data in New Relic, navigate to "New Relic -> All Entities -> OpenTelemetry" and You should see an entity named `adservice` as defined in `OTEL_SERVICE_NAME` in `docker-compose.yaml`. Click to view the OpenTelemetry summary. Click "Service Map" in the left navigation, and notice the relationship to the `docker-adservice` container entity.

Optionally, install the [Docker OpenTelemetry quickstart](https://newrelic.com/instant-observability/docker-otel) which includes a dashboard and alerts based on the data produced by the docker stats receiver.

Optionally, use [NRQL](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) to perform ad-hoc analysis. To list the metrics reported, query for:

```
FROM Metric SELECT uniques(metricName) WHERE otel.library.name = 'otelcol/dockerstatsreceiver'
```

See [get started with querying](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) for additional details on querying data in New Relic.

## Additional notes

This example deploys an instance of the opentelemetry demo [AdService](https://opentelemetry.io/docs/demo/services/ad/), defined in `docker-compose.yaml` `.services.adservice`. The AdService is instrumented with the [OpenTelemetry Java Agent](https://opentelemetry.io/docs/languages/java/instrumentation/#zero-code-java-agent) and is configured to export data via OTLP to New Relic. The OpenTelemetry Java Agent comes with a number of [resource detectors](https://opentelemetry.io/docs/languages/java/configuration/#resourceprovider) which enrich the telemetry with contextual information about the environment. One of these detects and includes the `container.id` attribute, which New Relic depends on for correlation with container entities. When adapting this example to your workflow, please ensure that your application is set up to detect and include `container.id` as described in the [container resource semantic conventions](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/resource/container.md).
