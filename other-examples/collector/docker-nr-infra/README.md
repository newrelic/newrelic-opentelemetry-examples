# Monitor Containers with New Relic infrastructure agent and correlate with OpenTelemetry APM services

This example demonstrates correlation between docker containers monitored with the [New Relic infrastructure agent](https://docs.newrelic.com/docs/infrastructure/introduction-infra-monitoring/) and OpenTelemetry APM services.

The New Relic infrastructure agent and a sample OpenTelemetry APM service are each run via docker. The New Relic infrastructure agent [automatically monitors containers](https://docs.newrelic.com/docs/infrastructure/infrastructure-agent/linux-installation/instrument-container/#enable). The OpenTelemetry APM service automatically detects and reports [container resource attributes](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/resource/container.md) which New Relic uses to create a relationship between the container and APM service entities.

## Requirements

* Docker daemon and docker compose
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

2. Run the New Relic infrastructure agent and sample application.

    ```
    docker compose up
    ```

    * Optionally include `-d` to run in the background.

   * When finished, cleanup resources by exiting the command with `Ctrl-D` or `Ctrl-C`. If running in the background, run the following command to stop containers.

   ```shell
   docker compose stop
   ```

## Viewing your data

To review your docker container data in New Relic, navigate to "New Relic -> All Entities -> Containers". You should see entities named `docker-nr-infra-adservice` and `newrelic-infra` as defined in `container_name` property of the respective services in [docker-compose.yaml](docker-compose.yaml). Click to view the container summary.

To review your OpenTelemetry APM data in New Relic, navigate to "New Relic -> All Entities -> OpenTelemetry" and You should see an entity named `adservice` as defined in `OTEL_SERVICE_NAME` in `docker-compose.yaml`. Click to view the OpenTelemetry summary. Click "Service Map" in the left navigation, and notice the relationship to the `docker-nr-infra-adservice` container entity.

## Additional notes

This example deploys an instance of the opentelemetry demo [AdService](https://opentelemetry.io/docs/demo/services/ad/), defined in `docker-compose.yaml` `.services.adservice`. The AdService is instrumented with the [OpenTelemetry Java Agent](https://opentelemetry.io/docs/languages/java/instrumentation/#zero-code-java-agent) and is configured to export data via OTLP to New Relic. The OpenTelemetry Java Agent comes with a number of [resource detectors](https://opentelemetry.io/docs/languages/java/configuration/#resourceprovider) which enrich the telemetry with contextual information about the environment. One of these detects and includes the `container.id` attribute, which New Relic depends on for correlation with container entities. When adapting this example to your workflow, please ensure that your application is set up to detect and include `container.id` as described in the [container resource semantic conventions](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/resource/container.md).

See [get started with querying](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) for additional details on querying data in New Relic.
