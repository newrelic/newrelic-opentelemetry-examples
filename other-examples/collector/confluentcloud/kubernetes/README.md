# Confluent Cloud OpenTelemetry metrics example Kubernetes setup

This example shows a setup for running a Prometheus OpenTelemetry Collector in a Kubernetes environment to scrape metrics from Confluent Cloud and post them to the New Relic OTLP Collector Endpoint.
This example also includes config for getting more Kafka metrics about brokers, topics and consumers using the OpenTelemetry Collector's `kafkametrics` receiver.

For more information, please see our [Kafka with Confluent documentation](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/collector/collector-configuration-examples/opentelemetry-collector-kafka-confluentcloud/).

## Prerequisites

1. You must have a Kubernetes cluster running.
2. You must have [Helm](https://helm.sh/docs/intro/quickstart/) installed .
3. You must have a [Confluent Cloud account](https://www.confluent.io/get-started/) with a cluster running.

## Running the Helm example
First, set your variables in the values file `./helm/values.yaml` in this directory. For more information on the individual variables, reference the docs available below.

Once the variables are set, run the following command from the root directory to install the collector in your Kubernetes cluster.

```shell
cd ./other-examples/collector/kubernetes

helm install ./helm
```

## Running the raw k8s yaml example
First, set your variables in the deployment file `./k8s/deployment.yaml` in this directory. For more information on the individual variables, reference the docs available below.

Once the variables are set, run the following command from the root directory to apply the collector to your Kubernetes cluster.

```shell
cd ./other-examples/collector/kubernetes

kubectl apply -f ./k8s
```

## Helm `values.yaml` variable information

| Variable                      | Description                                                               | Docs                                                                                                                                                                                      |
|-------------------------------|---------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **newrelic.apiKey**           | New Relic Ingest API Key                                                  | [API Key docs](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/)                                                                                                        | 
| **newrelic.otlpEndpoint**                 | Default US OTLP Endpoint is https://otlp.nr-data.net                      | [OTLP endpoint config docs](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/get-started/opentelemetry-set-up-your-app/#review-settings) |
| **confluent.apiKey**          | API key for Confluent Cloud, can be created via cli by following the docs | [Confluent API key docs](https://docs.confluent.io/cloud/current/monitoring/metrics-api.html)                                                                                             |
| **counfluent.apiSecret**      | API secret for Confluent Cloud                                            | [Confluent API key docs](https://docs.confluent.io/cloud/current/monitoring/metrics-api.html)                                                                                             |
| **confluent.clusterId**       | ID of the cluster from Confluent Cloud                                    | [List cluster ID docs](https://docs.confluent.io/confluent-cli/current/command-reference/kafka/cluster/confluent_kafka_cluster_list.html#description)                                     |
| **confluent.clusterName**     | Name of the cluster from Confluent Cloud                                  | [List cluster name docs](https://docs.confluent.io/confluent-cli/current/command-reference/kafka/cluster/confluent_kafka_cluster_list.html#description)                                   |
| **confluent.bootstrapServer** | bootstrap server Endpoint for your cluster from Confluent Cloud           | [Describe cluster docs](https://docs.confluent.io/confluent-cli/current/command-reference/kafka/cluster/confluent_kafka_cluster_describe.html#description)                                |


## Raw k8s `env` Variable information

| Variable                    | Description                                                               | Docs |
|-----------------------------|---------------------------------------------------------------------------| ---- |
| **NEW_RELIC_API_KEY**       | New Relic Ingest API Key                                                  |[API Key docs](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/) | 
| **NEW_RELIC_OTLP_ENDPOINT** | Default US OTLP endpoint is https://otlp.nr-data.net                      | [OTLP endpoint config docs](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/get-started/opentelemetry-set-up-your-app/#review-settings) |
| **CONFLUENT_API_KEY**       | API key for Confluent Cloud, can be created via cli by following the docs |[Confluent API key docs](https://docs.confluent.io/cloud/current/monitoring/metrics-api.html)|
| **CONFLUENT_API_SECRET**    | API secret for Confluent Cloud                                            | [Confluent API key docs](https://docs.confluent.io/cloud/current/monitoring/metrics-api.html) |
| **CLUSTER_ID**              | ID of the cluster from Confluent Cloud                                    | [List cluster ID docs](https://docs.confluent.io/confluent-cli/current/command-reference/kafka/cluster/confluent_kafka_cluster_list.html#description) |
| **CLUSTER_NAME**            | Name of the cluster from Confluent Cloud                                  | [List cluster name docs](https://docs.confluent.io/confluent-cli/current/command-reference/kafka/cluster/confluent_kafka_cluster_list.html#description) |
| **BOOTSTRAP_SERVER**        | bootstrap server Endpoint for your cluster from Confluent Cloud                     | [Describe cluster docs](https://docs.confluent.io/confluent-cli/current/command-reference/kafka/cluster/confluent_kafka_cluster_describe.html#description) |
