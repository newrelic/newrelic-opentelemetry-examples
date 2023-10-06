# Confluent Cloud OpenTelemetry metrics example setup

This example shows a setup for running a Docker OpenTelemetry Collector to scrape metrics from Confluent Cloud and post them the New Relic OTLP Collector Endpoint. 

*Pre-requisites*: 
1. You must have a Docker daemon running
2. You must have docker compose installed (info: https://docs.docker.com/compose/)
3. You must have a Confluent cluster and account created (free account: https://www.confluent.io/get-started/) 



To run the example, set the environment variables and run `docker compose up`:

```shell
export NEW_RELIC_API_KEY=<your_api_key>
export NEW_RELIC_OTLP_ENDPOINT=https://otlp.nr-data.net
export CONFLUENT_API_ID=<your_api_id>
export CONFLUENT_API_SECRET=<your_api_secret>
export CLUSTER_ID=<your_cluster_id>
export CLUSTER_API_KEY=<your_cluster_api_key>
export CLUSTER_API_SECRET=<your_cluster_api_secret>
export CLUSTER_BOOTSTRAP_SERVER=<your_cluster_bootstrap_server>

docker compose up
```
</br>

# Local Variable information

| Variable | Description | Docs |
| -------- | ----------- | ---- |
| **NEW_RELIC_API_KEY** |New Relic Ingest API Key |[API Key docs](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/) | 
| **NEW_RELIC_OTLP_ENDPOINT** | OTLP endpoint is https://otlp.nr-data.net | [OTLP endpoint config docs](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/get-started/opentelemetry-set-up-your-app/#review-settings) |
| **CONFLUENT_API_ID** |Confluent Cloud API key| [Cloud api-key confluent docs](https://docs.confluent.io/cloud/current/monitoring/metrics-api.html#metrics-quick-start) |
| **CONFLUENT_API_SECRET** | Confluent Cloud API secret key | [Cloud api-key confluent docs](https://docs.confluent.io/cloud/current/monitoring/metrics-api.html#metrics-quick-start) |
| **CLUSTER_ID** | ID of cluster from Confluent Cloud | Available in your cluster settings |
| **CLUSTER_API_KEY** | Resource API key for your Confluent cluster |[Resource API key docs](https://docs.confluent.io/cloud/current/access-management/authenticate/api-keys/api-keys.html#create-a-resource-api-key) |
| **CLUSTER_API_SECRET**| Resource API secret key from your Confluent cluster| [Resource API key docs](https://docs.confluent.io/cloud/current/access-management/authenticate/api-keys/api-keys.html#create-a-resource-api-key) |
| **CLUSTER_BOOTSTRAP_SERVER** | Bootstrap Server for cluster | Available in your cluster settings |

</br>

# Notes

 For more information on setting up this OTeL collector for Confluent Metrics, check out our [documentation](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/collector/collector-configuration-examples/opentelemetry-collector-kafka-confluentcloud/).