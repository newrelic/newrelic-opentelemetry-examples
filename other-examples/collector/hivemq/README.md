# Monitoring HiveMQ with OpenTelemetry Collector

This simple example demonstrates monitoring [HiveMQ](https://github.com/hivemq/hivemq-community-edition) with the [OpenTelemetry collector](https://opentelemetry.io/docs/collector/), using the [hivemq-prometheus-extension](https://github.com/hivemq/hivemq-prometheus-extension/tree/master) and the collector [prometheus receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/prometheusreceiver) and sending the data to New Relic via OTLP.

## Requirements

* You need to have a Kubernetes cluster, and the kubectl command-line tool must be configured to communicate with your cluster. Docker desktop [includes a standalone Kubernetes server and client](https://docs.docker.com/desktop/kubernetes/) which is useful for local testing.
* [A New Relic account](https://one.newrelic.com/)
* [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Running the example

1. Download and setup the [hivemq-prometheus-extension](https://github.com/hivemq/hivemq-prometheus-extension/tree/master) extension.

    This example runs an instance of HiveMQ community edition via the [hivemq-ce](https://hub.docker.com/r/hivemq/hivemq-ce/tags) docker image, which does not include the required [hivemq-prometheus-extension](https://github.com/hivemq/hivemq-prometheus-extension/tree/master). Run the following command to download the `hivemq-prometheus-extension`.

    ```shell
    ./download-prometheus-extension.sh
    ```
   
2. Update the path to the prometheus extension volume in [hivemq.yaml](./k8s/hivemq.yaml).
   
    Run `pwd` from the root of this directory to get the fully qualified path on your system.
    
    Replace the `<INSERT_PATH_TO_HIVEMQ_EXAMPLE>` with the fully qualified path.

    ```yaml
     # ...omitted for brevity
     volumes:
       # This volume contains the hivemq-prometheus-extension, which is downloaded via ../download-prometheus-extension.sh
       # Replace <INSERT_PATH_TO_HIVEMQ_EXAMPLE> with the fully qualified path to the root of the hivemq example.
       - name: hivemq-prometheus-extension
         hostPath:
           path: <INSERT_PATH_TO_HIVEMQ_EXAMPLE>/hivemq-prometheus-extension
           type: Directory
    ```

3. Update the `NEW_RELIC_API_KEY` value in [secrets.yaml](./k8s/secrets.yaml) to your New Relic license key.

    ```yaml
    # ...omitted for brevity
    stringData:
      # New Relic API key to authenticate the export requests.
      # docs: https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key
      NEW_RELIC_API_KEY: <INSERT_API_KEY>
    ```
   
    * Note, be careful to avoid inadvertent secret sharing when modifying `secrets.yaml`. To ignore changes to this file from git, run `git update-index --skip-worktree k8s/secrets.yaml`.

    * If your account is based in the EU, update the `NEW_RELIC_OTLP_ENDPOINT` value in [collector.yaml](./k8s/collector.yaml) the endpoint to: [https://otlp.eu01.nr-data.net](https://otlp.eu01.nr-data.net)

    ```yaml
    # ...omitted for brevity
   env:
     # The default US endpoint is set here. You can change the endpoint and port based on your requirements if needed.
     # docs: https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/best-practices/opentelemetry-otlp/#configure-endpoint-port-protocol
     - name: NEW_RELIC_OTLP_ENDPOINT
       value: https://otlp.eu01.nr-data.net
    ```

4. Run the application with the following command.

    ```shell
    kubectl apply -f k8s/
    ```
   
   * When finished, cleanup resources with the following command. This is also useful to reset if modifying configuration.

   ```shell
   kubectl delete -f k8s/
   ```

## Viewing your data

To review your HiveMQ data in New Relic, navigate to "New Relic -> Query Your Data". To list the metrics reported, query for:

```
FROM Metric SELECT uniques(metricName) WHERE otel.library.name = 'otelcol/prometheusreceiver' and metricName like 'com_hivemq%'
```

See [get started with querying](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) for additional details on querying data in New Relic.

## Additional notes

This example monitors a HiveMQ instance defined in [hivemq.yaml](./k8s/hivemq.yaml), which is not receiving any load. To use in production, you'll need to modify the `.receivers.prometheus.config.scrape_configs[0].static_configs[].targets` value in [collector.yaml](k8s/collector.yaml) ConfigMap to point to the endpoint of your HiveMQ instance running with the [hivemq-prometheus-extension](https://github.com/hivemq/hivemq-prometheus-extension/tree/master). See [HiveMQ Platform Operator for Kubernetes](https://docs.hivemq.com/hivemq-platform-operator/index.html) for guidance on running HiveMQ in kubernetes in production.
