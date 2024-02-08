# SingleStore OpenTelemetry metrics example setup

This example shows a setup for running a prometheus OpenTelemetry Collector in a docker container to scrape metrics from your SingleStore environment and post them the New Relic OTLP Collector Endpoint.

For more information, please see our [SingleStore OTeL collector docs](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/collector/collector-configuration-examples/opentelemetry-collector-singlestore/).

## Prerequisites

1. You must have a Docker daemon running.
2. You must have [Docker compose](https://docs.docker.com/compose/) installed.
3. You must have a SingleStore workspace running.

## Running the example

First, set your environment variables in the `.env` file in this directory. For more information on the individual variables, reference the docs available below.

Once the variables are set, run the following command from the root directory to start the collector.

```shell
cd ./other-examples/collector/singlstore

docker compose up
```

## Local Variable information

| Variable | Description | Docs |
| -------- | ----------- | ---- |
| **NEW_RELIC_API_KEY** |New Relic Ingest API Key |[API Key docs](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/) |
| **NEW_RELIC_OTLP_ENDPOINT** |Default US OTLP endpoint is `https://otlp.nr-data.net` | [OTLP endpoint config docs](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/get-started/opentelemetry-set-up-your-app/#review-settings) |
| **SINGLESTORE_ORG** | ID of your SingleStore Organization |[SingleStore Organization docs](https://docs.singlestore.com/cloud/user-and-workspace-administration/manage-organizations/)|
| **SINGLESTORE_WORKSPACE_GROUP** | ID of your SingleStore Workspace Group | [SingleStore workspace docs](https://docs.singlestore.com/cloud/getting-started-with-singlestoredb-cloud/about-workspaces/what-is-a-workspace/) |
| **SINGLESTORE_API_KEY** | SingleStore API Key | [SingleStore API Key docs](https://support.singlestore.com/hc/en-us/articles/12396018910228-Creating-Management-API-Key)|
