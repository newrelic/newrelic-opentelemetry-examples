# Monitoring Squid with OpenTelemetry Collector

This simple example demonstrates monitoring [Squid Web Proxy Cache](https://github.com/squid-cache/squid) prometheus metrics with the [OpenTelemetry collector](https://opentelemetry.io/docs/collector/), using the [prometheus receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/prometheusreceiver) and sending the data to New Relic via OTLP.

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
   
2. Set the `SQUID_CACHEMGR` env var value in [collector.yaml](./k8s/collector.yaml). The value is used to identify the squid entity in New Relic.

    ```yaml
   # ...omitted for brevity
   # A unique identifier for the instance of the squid cache manager being monitored, used as the entity name in New Relic.
   - name: SQUID_CACHEMGR
     value: <INSERT_SQUID_IDENTIFIER>
    ```

3. Run the application with the following command.

    ```shell
    kubectl apply -f k8s/
    ```
   
   * When finished, cleanup resources with the following command. This is also useful to reset if modifying configuration.

   ```shell
   kubectl delete -f k8s/
   ```

## Viewing your data

To review your squid data in New Relic, navigate to "New Relic -> All Entities -> Squid Cache managers" and click on the instance with name corresponding to the `SQUID_CACHEMGR` env var value to view the instance summary. Use [NRQL](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) to perform ad-hoc analysis.

```
FROM Metric SELECT uniques(metricName) WHERE otel.library.name = 'otelcol/prometheusreceiver' AND metricName like 'squid%' LIMIT MAX
```

See [get started with querying](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) for additional details on querying data in New Relic.

## Additional notes

This example monitors a squid instance defined in [squid.yaml](./k8s/squid.yaml), with [squid-exporter](https://github.com/boynux/squid-exporter) running in a sidecar container. To use in production, you'll need to modify the `.receivers.prometheus.config.scrape_configs[0].static_configs[0].targets` value in [collector.yaml](k8s/collector.yaml) ConfigMap to point to the `squid-exporter` target corresponding to your squid instance.
