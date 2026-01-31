# Monitoring Docker with OpenTelemetry Collector

This simple example demonstrates monitoring docker with the [OpenTelemetry collector](https://opentelemetry.io/docs/collector/), using the [docker stats receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/dockerstatsreceiver) and sending the data to New Relic via OTLP.

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

To review your docker data in New Relic, navigate to "New Relic -> All Entities -> Containers". You should see entities named `docker-collector-1` and `docker-nginx-1` corresponding to the services defined in `docker-compose.yaml`. Click to view the container summary.

Optionally, install the [Docker OpenTelemetry quickstart](https://newrelic.com/instant-observability/docker-otel) which includes a dashboard and alerts based on the data produced by the docker stats reciever.

Optionally, use [NRQL](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) to perform ad-hoc analysis. To list the metrics reported, query for:

```
FROM Metric SELECT uniques(metricName) WHERE otel.library.name in ('otelcol/dockerstatsreceiver', 'github.com/open-telemetry/opentelemetry-collector-contrib/receiver/dockerstatsreceiver')
```

See [get started with querying](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) for additional details on querying data in New Relic.

## Additional notes

New Relic depends on docker container data including the `host.id` resource attribute. The collector [config.yaml](./config.yaml) contains several resource detectors in `.processors.resourcedetection.detectors` which attempt to fetch `host.id`. If `host.id` is not detected, you can manually set it by uncommenting and editing the `OTEL_RESOURCE_ATTRIBUTES` env var in [docker-compose.yaml](./docker-compose.yaml).

```yaml
# ...omitted for brevity
# host.id is required for New Relic.
# Optionally manually set it if one of the resource detectors in config.yaml is unable to identify it.
# - OTEL_RESOURCE_ATTRIBUTES=host.id=<INSERT_HOST_ID>
```

This example runs a dummy nginx image defined in [docker-compose.yaml](./docker-compose.yaml). This only exists to produce more interesting data and should be removed for production deployments.
