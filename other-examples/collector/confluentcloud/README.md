# Confluent Cloud OpenTelemetry metrics example setup

This example shows a setup for running a Docker OpenTelemetry Collector to scrape metrics from Confluent Cloud and post them the New Relic OTLP Collector Endpoint. For more information, please see our [Kafka with Confluent documentation](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/collector/collector-configuration-examples/opentelemetry-collector-kafka-confluentcloud/).

## Pre-requisites: 
1. You must have a Docker daemon running
2. You must have docker compose installed (info: https://docs.docker.com/compose/)
3. You must have a Confluent cluster and account created (free account: https://www.confluent.io/get-started/)
4. For best security practices, you should use a TLS authentication method as given in this exmaple. To do so, you can add the ca, cert, and key files in this directory. </br>
(Confluent TLS encryption docs: https://docs.confluent.io/platform/current/kafka/authentication_ssl.html)



To run the example: add in the key files, set the environment variables, and run `docker compose up`

```shell
export NEW_RELIC_API_KEY=<your_api_key>
export NEW_RELIC_OTLP_ENDPOINT=https://otlp.nr-data.net:4318  # This works for NA http endpoint. If you need an EU endpoint or non-http endpoint, refer to the docs.
export CLUSTER_ID=<your_cluster_id>
export CONFLUENT_API_ID=<your_confluent_api_id>
export CONFLUENT_API_SECRET=<your_cluster_api_secret>
export CLUSTER_BOOTSTRAP_SERVER=<your_cluster_bootstrap_server>

docker compose up
```
</br>

## Local Variable information

| Variable | Description | Docs |
| -------- | ----------- | ---- |
| **NEW_RELIC_API_KEY** |New Relic Ingest API Key |[API Key docs](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/) | 
| **NEW_RELIC_OTLP_ENDPOINT** | New Relic OTLP endpoint for NA is https://otlp.nr-data.net:4318 | [OTLP endpoint config docs](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/get-started/opentelemetry-set-up-your-app/#review-settings) |
| **CLUSTER_ID** | ID of cluster from Confluent Cloud | Available in your cluster settings |
| **CONFLUENT_API_ID** | Cloud API key |[Cloud API key docs](https://docs.confluent.io/cloud/current/monitoring/metrics-api.html#metrics-quick-start) |
| **CONFLUENT_API_SECRET**| Cloud API secret | [Resource API key docs](https://docs.confluent.io/cloud/current/monitoring/metrics-api.html#metrics-quick-start) |
| **CLUSTER_BOOTSTRAP_SERVER** | Bootstrap Server for cluster | Available in your cluster settings |

</br>