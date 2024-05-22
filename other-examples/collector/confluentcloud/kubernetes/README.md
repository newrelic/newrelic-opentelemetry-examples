# Confluent Cloud OpenTelemetry metrics example kubernetes setup

This example shows a setup for running a prometheus OpenTelemetry Collector in a kubernetes environment to scrape metrics from Confluent Cloud and post them to the New Relic OTLP Collector Endpoint.
This example also includes config for getting more kafka metrics about brokers, topics and consumers using the OpenTelemetry Collector's `kafkametrics` receiver.

For more information, please see our [Kafka with Confluent documentation](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/collector/collector-configuration-examples/opentelemetry-collector-kafka-confluentcloud/).

## Prerequisites

1. You must have a kubernetes cluster running.
2. You must have [helm](https://helm.sh/docs/intro/quickstart/) installed .
3. You must have a [Confluent Cloud account](https://www.confluent.io/get-started/) with a cluster running.

## Running the example
First, set your variables in the values file `./helm/values.yaml` in this directory. For more information on the individual variables, reference the docs available below.

Once the variables are set, run the following command from the root directory to start the collector in your kubernetes cluster.

```shell
cd ./other-examples/collector/kubernetes

helm install ./helm
```

## Helm `values.yaml` variable information

| Variable                      | Description                                                               | Docs                                                                                                                                                                                      |
|-------------------------------|---------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **newrelic.apiKey**           | New Relic Ingest API Key                                                  | [API Key docs](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/)                                                                                                        | 
| **newrelic.otlpEndpoint**                 | Default US OTLP endpoint is https://otlp.nr-data.net                      | [OTLP endpoint config docs](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/get-started/opentelemetry-set-up-your-app/#review-settings) |
| **confluent.apiKey**          | API key for Confluent Cloud, can be created via cli by following the docs | [Confluent API key docs](https://docs.confluent.io/cloud/current/monitoring/metrics-api.html)                                                                                             |
| **counfluent.apiSecret**      | API secret for Confluent Cloud                                            | [Confluent API key docs](https://docs.confluent.io/cloud/current/monitoring/metrics-api.html)                                                                                             |
| **confluent.clusterId**       | ID of the cluster from Confluent Cloud                                    | [List cluster ID docs](https://docs.confluent.io/confluent-cli/current/command-reference/kafka/cluster/confluent_kafka_cluster_list.html#description)                                     |
| **confluent.clusterName**     | Name of the cluster from Confluent Cloud                                  | [List cluster name docs](https://docs.confluent.io/confluent-cli/current/command-reference/kafka/cluster/confluent_kafka_cluster_list.html#description)                                   |
| **confluent.bootstrapServer** | bootstrap server endpoint for your cluster from Confluent Cloud           | [Describe cluster docs](https://docs.confluent.io/confluent-cli/current/command-reference/kafka/cluster/confluent_kafka_cluster_describe.html#description)                                |

