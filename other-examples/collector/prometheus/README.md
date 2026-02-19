# Monitoring Prometheus with OpenTelemetry Collector

This simple example demonstrates monitoring prometheus sources with the [OpenTelemetry collector](https://opentelemetry.io/docs/collector/), using the [prometheus receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/prometheusreceiver) and sending the data to New Relic via OTLP. A simple prometheus data generator is configured to generate dummy metrics scraped by prometheus receiver.

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
FROM Metric SELECT uniques(metricName) WHERE otel.library.name = 'otelcol/prometheusreceiver' LIMIT MAX
```

See [get started with querying](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) for additional details on querying data in New Relic.

## Additional notes

This example monitors a simple prometheus data generator instance defined in [prometheus-data-generator.yaml](./k8s/prometheus-data-generator.yaml). To use in production, you'll need to modify the `.receivers.prometheus.config.scrape_configs` value in [collector.yaml](k8s/collector.yaml) ConfigMap to point at your prometheus sources.

The prometheus receiver includes `service.name` and `service.instance.id` resource attributes derived from job name and target configured in `.receivers.prometheus.config.scraep_configs`. As documented [here](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/best-practices/opentelemetry-best-practices-resources/#services), New Relic considers any data with `service.name` as a service despite the fact that not all prometheus data sources are services. As a result, you can find a `prometheus_data_generator` entity under "New Relic -> All Entities -> Services - OpenTelemetry", although the panels will not contain data because the scraped metrics do not represent APM data.
