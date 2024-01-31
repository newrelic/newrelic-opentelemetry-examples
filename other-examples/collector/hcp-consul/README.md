# HCP Consul OpenTelemetry metrics example setup

This example shows a setup for running a prometheus OpenTelemetry Collector in a docker container to scrape metrics from HCP Consul and post them the New Relic OTLP Collector Endpoint.

**NOTE**: This is *not* for self-managed Consul, and will only work with HCP managed Consul. For self-managed Consul you will have to communicate with the [agent API](https://developer.hashicorp.com/consul/api-docs/agent).


## Prerequisites

1. You must have a Docker daemon running.
2. You must have [Docker compose](https://docs.docker.com/compose/) installed .
3. You must have a [Consul cluster](https://developer.hashicorp.com/hcp/docs/consul) running in Consul Cloud.

## Running the example
First, set your environment variables in the `.env` file in this directory. For more information on the individual variables, reference the docs available below.

Once the variables are set, run the following command from the root directory to start the collector.

```shell
cd ./other-examples/collector/hcp-consul

docker compose up
```

## Local Variable information

| Variable | Description | Docs |
| -------- | ----------- | ---- |
| **NEW_RELIC_API_KEY** |New Relic Ingest API Key |[API Key docs](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/) | 
| **NEW_RELIC_OTLP_ENDPOINT** |Default US OTLP endpoint is https://otlp.nr-data.net | [OTLP endpoint config docs](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/get-started/opentelemetry-set-up-your-app/#review-settings) |
| **CONSUL_ACCESS_URL** | URL for communicating with your HCP managed Consul cluster |[Consul URL docs](https://developer.hashicorp.com/hcp/docs/consul/hcp-managed/access#get-access-url)|
| **CONSUL_ACCESS_TOKEN** | Consul admin token to authenticate with your HCP managed Consul Cluster| [Consul token docs](https://developer.hashicorp.com/hcp/docs/consul/hcp-managed/access#generate-admin-token) |