# Monitoring NGINX with OpenTelemetry Collector

This simple example demonstrates monitoring NGINX with the [OpenTelemetry collector](https://opentelemetry.io/docs/collector/), using the [nginx receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/nginxreceiver) and sending the data to New Relic via OTLP.

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

To review your NGINX data in New Relic, navigate to "New Relic -> All Entities -> NGINX servers" and click on the instance with name "nginx" to view the instance summary. Click on "Metric explorer" to view all metrics associated with the NGINX instance, or use [NRQL](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) to perform ad-hoc analysis.

To list the metrics reported, query for:

```
FROM Metric SELECT uniques(metricName) WHERE otel.library.name = 'otelcol/nginxreceiver' LIMIT MAX
```

See [get started with querying](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) for additional details on querying data in New Relic.

## Additional notes

This example monitors an NGINX instance defined in [nginx.yaml](./k8s/nginx.yaml), which includes the `stub_status` module enabled to expose metrics. To use in production, you'll need to:

1. Ensure your NGINX instance has the `stub_status` module enabled and accessible
2. Modify the `.receivers.nginx.endpoint` value in [collector.yaml](k8s/collector.yaml) ConfigMap to point to the stub_status endpoint of your NGINX instance
3. Update the `server.address` and `server.port` resource attributes defined in `attributes/nginx_metrics` to values which reflect the NGINX instance being monitored

The nginx receiver collects metrics from the NGINX stub_status endpoint including active connections, requests per second, and connection states (reading, writing, waiting).
