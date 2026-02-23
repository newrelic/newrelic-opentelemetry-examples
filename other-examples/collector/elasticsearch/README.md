# Monitoring Elasticsearch with OpenTelemetry Collector

This simple example demonstrates monitoring Elasticsearch with the [OpenTelemetry collector](https://opentelemetry.io/docs/collector/), using the [elasticsearch receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/elasticsearchreceiver) and sending the data to New Relic via OTLP.

## Requirements

* You need to have a Kubernetes cluster, and the kubectl command-line tool must be configured to communicate with your cluster. Docker desktop [includes a standalone Kubernetes server and client](https://docs.docker.com/desktop/kubernetes/) which is useful for local testing.
* [A New Relic account](https://one.newrelic.com/)
* [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Running the example

1. Create your secrets file from the template and update the values:
    ```shell
    cp k8s/secrets.yaml.template k8s/secrets.yaml
    # Edit k8s/secrets.yaml with your New Relic license key
    ```
    See the [New Relic docs](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key) for how to obtain a license key.

    * If your account is based in the EU, update the `NEW_RELIC_OTLP_ENDPOINT` value in [collector.yaml](./k8s/collector.yaml) the endpoint to: [https://otlp.eu01.nr-data.net](https://otlp.eu01.nr-data.net)

    ```yaml
    # ...omitted for brevity
   env:
     # The default US endpoint is set here. You can change the endpoint and port based on your requirements if needed.
     # docs: https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/best-practices/opentelemetry-otlp/#configure-endpoint-port-protocol
     - name: NEW_RELIC_OTLP_ENDPOINT
       value: https://otlp.eu01.nr-data.net
    ```

2. Run the application with the following command.

    ```shell
    kubectl apply -f k8s/
    ```

   * When finished, cleanup resources with the following command. This is also useful to reset if modifying configuration.

   ```shell
   kubectl delete -f k8s/
   ```

## Viewing your data

To review your Elasticsearch data in New Relic, navigate to "New Relic -> All Entities -> Elasticsearch nodes" and click on the instance with name "elasticsearch" to view the instance summary. Click on "Metric explorer" to view all metrics associated with the Elasticsearch instance, or use [NRQL](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) to perform ad-hoc analysis.

To list the metrics reported, query for:

```
FROM Metric SELECT uniques(metricName) WHERE otel.library.name = 'otelcol/elasticsearchreceiver' LIMIT MAX
```

See [get started with querying](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) for additional details on querying data in New Relic.

## Additional notes

This example monitors an Elasticsearch instance defined in [elasticsearch.yaml](./k8s/elasticsearch.yaml), which is not receiving any significant load. To use in production, you'll need to modify the `.receivers.elasticsearch.endpoint` value in [collector.yaml](k8s/collector.yaml) ConfigMap to point to the endpoint of your Elasticsearch instance. Additionally, update the `server.address` and `server.port` resource attributes defined in `attributes/elasticsearch_metrics` to values which reflect the Elasticsearch instance being monitored.

The elasticsearch receiver collects cluster and node-level metrics including JVM stats, indexing performance, search latency, and cluster health. For production deployments with authentication enabled, you can configure credentials using the `username` and `password` fields in the receiver configuration.
