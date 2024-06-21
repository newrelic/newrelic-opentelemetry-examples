# Monitoring Singlestore with OpenTelemetry Collector

This simple example demonstrates monitoring [Singlestore](https://www.singlestore.com/) prometheus metrics with the [OpenTelemetry collector](https://opentelemetry.io/docs/collector/), using the [prometheus receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/prometheusreceiver) and sending the data to New Relic via OTLP.

## Requirements

* You need to have a Kubernetes cluster, and the kubectl command-line tool must be configured to communicate with your cluster. Docker desktop [includes a standalone Kubernetes server and client](https://docs.docker.com/desktop/kubernetes/) which is useful for local testing.
* [A New Relic account](https://one.newrelic.com/)
* [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)
* [A Singlestore account](https://www.singlestore.com/cloud-trial/) with a [workspace group](https://docs.singlestore.com/cloud/getting-started-with-singlestore-helios/about-workspaces/creating-and-using-workspaces/)
* [A Singlestore API key](https://support.singlestore.com/hc/en-us/articles/12396018910228-Creating-Management-API-Key)

## Running the example

1. Update the `NEW_RELIC_API_KEY` values `SINGLESTORE_API_KEY` values in [secrets.yaml](./k8s/secrets.yaml) to your New Relic license key, and singlestore API key respectively. See [Singlestore docs](https://support.singlestore.com/hc/en-us/articles/12396018910228-Creating-Management-API-Key) for obtaining API key / secret.

    ```yaml
    # ...omitted for brevity
   stringData:
      # New Relic API key to authenticate the export requests.
      # docs: https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key
      NEW_RELIC_API_KEY: <INSERT_API_KEY>
      # Set your Singlestore API Key.
      # docs: https://support.singlestore.com/hc/en-us/articles/12396018910228-Creating-Management-API-Key
      SINGLESTORE_API_KEY: <INSERT_API_KEY>>
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
   
2. Set the `SINGLESTORE_ORG_ID` and `SINGLESTORE_WORKSPACE_GROUP_ID` env var values in [collector.yaml](./k8s/collector.yaml). See [Singlestore docs](https://support.singlestore.com/hc/en-us/articles/12396547132564-Workspace-Group-ID-or-Cluster-ID) for details on obtaining org and workspace group ids.

    ```yaml
   # ...omitted for brevity
   # The Singlestore Org ID.
   # docs: https://support.singlestore.com/hc/en-us/articles/12396547132564-Workspace-Group-ID-or-Cluster-ID
   - name: SINGLESTORE_ORG_ID
     value: <INSERT_SINGLESTORE_ORG_ID>
   # The Singlestore Workspace Group ID.
   # docs: https://support.singlestore.com/hc/en-us/articles/12396547132564-Workspace-Group-ID-or-Cluster-ID
   - name: SINGLESTORE_WORKSPACE_GROUP_ID
     value: <INSERT_SINGLESTORE_WORKSPACE_GROUP_ID>
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

To review your Singlestore data in New Relic, navigate to "New Relic -> Query Your Data". To list the metrics reported, query for:

```
FROM Metric SELECT uniques(metricName) WHERE otel.library.name = 'otelcol/prometheusreceiver' AND metricName LIKE 'singlestoredb%'
```

See [get started with querying](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) for additional details on querying data in New Relic.

## Additional notes

The prometheus receiver includes `service.name` and `service.instance.id` resource attributes derived from job name and target configured in `.receivers.prometheus.config.scrape_configs`. As documented [here](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/best-practices/opentelemetry-best-practices-resources/#services), New Relic considers any data with `service.name` as a service despite the fact that not all prometheus data sources are services. As a result, you can find a `singlestore` entity under "New Relic -> All Entities -> Services - OpenTelemetry", although the panels will not contain data because the scraped metrics do not represent APM data.
