# Confluent Cloud OpenTelemetry metrics example setup

This example shows a setup for running a prometheus OpenTelemetry Collector in a docker container to scrape metrics from Confluent Cloud and post them the New Relic OTLP Collector Endpoint. 

For more information, please see our [Kafka with Confluent documentation](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/collector/collector-configuration-examples/opentelemetry-collector-kafka-confluentcloud/).

## Prerequisites

### Docker compose deployment option
1. You must have a Docker daemon running.
2. You must have [Docker compose](https://docs.docker.com/compose/) installed .
3. You must have a [Confluent Cloud account](https://www.confluent.io/get-started/) with a cluster running.

### Running the example
First, set your environment variables in the `.env` file in this directory. For more information on the individual variables, reference the docs available below.

Once the variables are set, run the following command from the root directory to start the collector.

```shell
cd ./other-examples/collector/confluentcloud

docker compose up
```

### Local Variable information

| Variable | Description | Docs |
| -------- | ----------- | ---- |
| **NEW_RELIC_API_KEY** |New Relic Ingest API Key |[API Key docs](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/) | 
| **NEW_RELIC_OTLP_ENDPOINT** |Default US OTLP endpoint is https://otlp.nr-data.net | [OTLP endpoint config docs](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/get-started/opentelemetry-set-up-your-app/#review-settings) |
| **CONFLUENT_API_KEY** |API key for Confluent Cloud, can be created via cli by following the docs |[Confluent API key docs](https://docs.confluent.io/cloud/current/monitoring/metrics-api.html)|
| **CONFLUENT_API_SECRET** | API secret for Confluent Cloud | [Confluent API key docs](https://docs.confluent.io/cloud/current/monitoring/metrics-api.html) |
| **CLUSTER_ID** | ID of the cluster from Confluent Cloud | [List cluster ID docs](https://docs.confluent.io/confluent-cli/current/command-reference/kafka/cluster/confluent_kafka_cluster_list.html#description) |
| **CONNECTOR_ID** |(Optional) ID of the connector from Confluent Cloud | [List connector ID docs](https://docs.confluent.io/confluent-cli/current/command-reference/connect/cluster/confluent_connect_cluster_list.html) |
| **SCHEMA_REGISTRY_ID** | (Optional) ID of schema registry from Confluent Cloud | [List schema-registry ID docs](https://docs.confluent.io/confluent-cli/current/command-reference/schema-registry/schema/confluent_schema-registry_schema_list.html) |

### Helm Chart deployment option
1. You must have a Kubernetes cluster running.
2. You must have [Helm CLI](https://helm.sh/docs/intro/install/) installed. 
3. You must have a [Confluent Cloud account](https://www.confluent.io/get-started/) with a cluster running.

### Running the example
First, copy the `values.yml` from the repo to your local drive. 
Update the `values.yml` and replace the following string with your data. 
- `<CONFLUENT_KEY>`
- `<CONFLUENT_SECRET>`
- `<CONFLUENT_CLUSTER_ID>`
- `<CONFLUENT_CONNECTOR_ID>`
- `<CONFLUENT_SCHEMA_REGISTRY_ID>`
- `<CONFLUENT_KSQL_CLUSTER_ID>`
- `<NEW_RELIC_INGEST_KEY>`

Once the file is updated, run the following command from the current directory to deploy the helm chart.

1. Add `open-telemetry` helm chart repo
```shell
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-chart
```

2. Deploy `opentelemetry-collector` helm chart with the `values.yml` file
```shell
helm install otel open-telemetry/opentelemetry-collector --values ./values.yml
```

3. If you make changes to the `values.yml`, you can refresh the deployment
```shell
helm upgrade otel open-telemetry/opentelemetry-collector --values ./values.yml
```

4. Check the helm status 
```shell
helm list
```
