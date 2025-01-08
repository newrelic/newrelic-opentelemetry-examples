# Monitoring apllication OpenTelemetry Collector and Docker with NR infra agent

This is a simple application instrumented with OpenTelemetry JavaScript. It demonstrates how to configure OpenTelemetry JavaScript to send data to New Relic.

Additionally, it demonstrates correlating OpenTelemetry entities with docker.


## Requirements

* A linux machine with docker daemon and docker compose (docker stats receiver only supports Linux).
* [A New Relic account](https://one.newrelic.com/)
* [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Running the example

  1. Update the `NEW_RELIC_API_KEY` value with to your New Relic license key.

      * [newrelic-infra.yaml](newrelic-infra/newrelic-infra.yaml)

      ```yaml
        # New Relic API key to authenticate the export requests.
        # docs: https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key
      license_key: <NEW_RELIC_API_KEY>
      ```

      * [otel-config.yaml](./otel-config.yaml)

      ```yaml
      # ...omitted for brevity
        # New Relic API key to authenticate the export requests.
        # docs: https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key
        otlp:
          endpoint: otlp.nr-data.net:4318
          headers:
            api-key: <NEW_RELIC_API_KEY>
      ```

      * If your account is based in the EU, update the `NEW_RELIC_OTLP_ENDPOINT` value in [otel-config.yaml](./otel-config.yaml) the endpoint to: [https://otlp.eu01.nr-data.net](https://otlp.eu01.nr-data.net)

      ```yaml
      # ...omitted for brevity
      # The default US endpoint is set here. You can change the endpoint and port based on your requirements if needed.
      # docs: https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/best-practices/opentelemetry-otlp/#configure-endpoint-port-protocol

      exporters:
        debug:
          verbosity: detailed
        otlp:
          endpoint: https://otlp.eu01.nr-data.net:4317
      ```

  2. Run the NR Infra agent with the following command.

      ```shell
      cd newrelic-infra
      ```
      ```shell
      docker build -t newrelic-infra .
      ```

      ```shell
        docker run \
        --detach \
        --name newrelic-infra \
        --network=host \
        --cap-add=SYS_PTRACE \
        --privileged \
        --pid=host \
        --cgroupns=host \
        --volume "/:/host:ro" \
        --volume "/var/run/docker.sock:/var/run/docker.sock" \
        --env NRIA_LICENSE_KEY=<NEW_RELIC_API_KEY> \
        newrelic-infra
      ```
      
  3. Run the application with the following command.

    ```shell
      docker-compose build
    ```

    ```shell
      docker-compose up -d
    ```
    
    * Optionally include `-d` to run in the background.

## Viewing your data

  To review your OpenTelemetry data in New Relic, navigate to "New Relic -> All Entities -> OpenTelemetry" and You should see entities named `docker-app-nr-infra-agent`  defined in `.env`. Click to view the OpenTelemetry summary.


  To review your docker data in New Relic, navigate to "New Relic -> All Entities -> Containers". You should see entities named `otel-collector-container-nr-agent` defined in `docker-compose.yaml`. Click to view the container summary.


  See [get started with querying](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) for additional details on querying data in New Relic.


