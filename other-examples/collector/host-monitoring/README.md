# Host Monitoring with OpenTelemetry Collector Setup

This example shows a setup for running the OpenTelemetry Collector in a docker container configured to scrape host metrics, and to enrich application telemetry with resource attributes required for entity synthesis relationships between host and service entities. This type of configuration is ideal when running the collector as an agent with an instance on every node (i.e. using kubernetes [DaemonSet](https://kubernetes.io/docs/concepts/workloads/controllers/daemonset/)) and forwarding telemetry for applications running on that node.

For more information, please see our [Collector for host monitoring](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/collector/opentelemetry-collector-infra-hosts/).

## Prerequisites

1. You must have a Docker daemon running.
2. You must have [Docker compose](https://docs.docker.com/compose/) installed .

## Running the example

First, set your environment variables in the `.env` file in this directory. For more information on the individual variables, reference the docs available below.

Once the variables are set, run the following command from the root directory to start the collector.

```shell
cd ./other-examples/collector/host-monitoring

docker compose up
```

Optionally, run an OpenTelemetry instrumented application and point it at the collector's OTLP endpoint via `http://localhost:4318`. The application telemetry is enriched with resource information which New Relic uses to form relationships between the host and service entity.

## Viewing your data

To review your Host data in New Relic, navigate `All Entities -> Hosts` and click on the host with the name of your local machine.

## Local Variable information

| Variable                    | Description                                            | Docs                                                                                                                                                                                      |
|-----------------------------|--------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **NEW_RELIC_API_KEY**       | New Relic Ingest API Key                               | [API Key docs](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/)                                                                                                        |
| **NEW_RELIC_OTLP_ENDPOINT** | Default US OTLP endpoint is `https://otlp.nr-data.net` | [OTLP endpoint config docs](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/get-started/opentelemetry-set-up-your-app/#review-settings) |
