# Monitoring Elasticsearch with OpenTelemetry Collector

This simple example demonstrates monitoring Elasticsearch with the [OpenTelemetry collector](https://opentelemetry.io/docs/collector/), using the [elasticsearch receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/elasticsearchreceiver) and sending the data to New Relic via OTLP.

The elasticsearch receiver collects cluster and node-level metrics including JVM stats, indexing performance, search latency, cluster health, and resource utilization.

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

    * If your account is based in the EU, update the `NEW_RELIC_OTLP_ENDPOINT` value in [collector.yaml](./k8s/collector.yaml) to the endpoint: [https://otlp.eu01.nr-data.net](https://otlp.eu01.nr-data.net)

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

### Example NRQL queries

List all metrics reported:
```sql
FROM Metric SELECT uniques(metricName) WHERE otel.library.name = 'github.com/open-telemetry/opentelemetry-collector-contrib/receiver/elasticsearchreceiver' LIMIT MAX
```

See [get started with querying](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) for additional details on querying data in New Relic.

## Additional notes

**This is a demo/development configuration** - This example monitors an Elasticsearch instance defined in [elasticsearch.yaml](./k8s/elasticsearch.yaml) with security disabled and no authentication required. This configuration is suitable for testing only.

**For production deployments:**

1. **Use dynamic discovery** - For production Kubernetes environments, consider using `receiver_creator` with `k8s_observer` for automatic pod discovery. See the [New Relic Elasticsearch on Kubernetes guide](https://docs.newrelic.com/docs/opentelemetry/integrations/elasticsearch/kubernetes/) for a production-ready configuration with:
   - Dynamic pod discovery across namespaces
   - RBAC configuration for cluster-wide monitoring
   - Resource limits and health checks
   - Additional processors for cardinality reduction

2. **Enable authentication** - Configure credentials using the `username` and `password` fields in the receiver configuration:
   ```yaml
   receivers:
     elasticsearch:
       endpoint: https://elasticsearch-service:9200
       username: ${env:ELASTICSEARCH_USERNAME}
       password: ${env:ELASTICSEARCH_PASSWORD}
   ```

3. **Enable TLS** - Remove `skip_verify: true` and configure proper TLS certificate validation:
   ```yaml
   receivers:
     elasticsearch:
       endpoint: https://elasticsearch-service:9200
       tls:
         insecure_skip_verify: false
         ca_file: /path/to/ca.crt
   ```

4. **Modify the endpoint** - Update the `.receivers.elasticsearch.endpoint` value in [collector.yaml](k8s/collector.yaml) ConfigMap to point to your actual Elasticsearch instance

5. **Update resource attributes** - Change the `server.address` and `server.port` in the `attributes/elasticsearch_metrics` processor to reflect your Elasticsearch instance

6. **Ensure compatibility** - This receiver requires Elasticsearch 7.16 or higher (tested with Elasticsearch 8.x)

### Learn more

- [New Relic Elasticsearch monitoring documentation](https://docs.newrelic.com/docs/opentelemetry/integrations/elasticsearch/elasticsearch-otel-integration-install/)
- [OpenTelemetry Elasticsearch receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/elasticsearchreceiver)
