# Monitoring StatsD with OpenTelemetry Collector

This simple example demonstrates monitoring statsd sources with the [OpenTelemetry collector](https://opentelemetry.io/docs/collector/), using the [statsd receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/statsdreceiver) and sending the data to New Relic via OTLP. A simple load statsd load generator is configured to send dummy data to the statsd receiver.

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

To review your statsd data in New Relic, navigate to "New Relic -> Query Your Data". To list the metrics reported, query for:

```
FROM Metric SELECT uniques(metricName) WHERE otel.library.name = 'otelcol/statsdreceiver' LIMIT MAX
```

See [get started with querying](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) for additional details on querying data in New Relic.

## Additional notes

A simple load statsd load generator is configured to send dummy data to the statsd receiver, specified in [gen-statsd.yaml](./k8s/gen-statsd.yaml). To use in production, you'll need to configure your statsd data sources to point to the collector's statsd receiver endpoint. In this example, the collector's statsd receiver is listing for UDP on `0.0.0.0:8125`, which is exposed in a [service](https://kubernetes.io/docs/concepts/services-networking/service/). The load generator is configured point to this using service env vars set by kubernetes.
