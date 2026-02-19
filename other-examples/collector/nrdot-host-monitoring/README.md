# Monitoring Hosts with OpenTelemetry Collector

This example demonstrates monitoring hosts with the [NRDOT collector](https://github.com/newrelic/nrdot-collector-releases/tree/1.11.0/distributions/nrdot-collector), using the [host metrics receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/hostmetricsreceiver) and sending the data to New Relic via OTLP.

Functionally this example is similar to the [host monitoring example](../host-monitoring/README.md) but using the pre-packaged configuration of the NRDOT collector.


## Requirements

* You need to have a Kubernetes cluster, and the kubectl command-line tool must be configured to communicate with your cluster. Docker desktop [includes a standalone Kubernetes server and client](https://docs.docker.com/desktop/kubernetes/) which is useful for local testing.
* [A New Relic account](https://one.newrelic.com/)
* [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Running the example

1. Update the `NEW_RELIC_API_KEY` value in [secrets.yaml](./k8s/secrets.yaml) to your New Relic license key.
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

3. Run the application with the following command.

    ```shell
    kubectl apply -f k8s/
    ```
   
   * When finished, cleanup resources with the following command. This is also useful to reset if modifying configuration.

   ```shell
   kubectl delete -f k8s/
   ```

## Viewing your data

To review your host data in New Relic, navigate to "New Relic -> All Entities -> Hosts" and click on the instance with name corresponding to the collector pod name to view the instance summary. Use [NRQL](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) to perform ad-hoc analysis.

```
FROM Metric SELECT uniques(metricName) WHERE otel.library.name like '%/receiver/hostmetricsreceiver/%'
```

See [get started with querying](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) for additional details on querying data in New Relic.
