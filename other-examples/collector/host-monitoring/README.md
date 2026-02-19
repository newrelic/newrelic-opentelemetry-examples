# Monitoring Hosts with OpenTelemetry Collector

This example demonstrates monitoring hosts with the [OpenTelemetry collector](https://opentelemetry.io/docs/collector/), using the [host metrics receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/hostmetricsreceiver) and sending the data to New Relic via OTLP.

Additionally, it demonstrates correlating APM entities with hosts, using the OpenTelemetry collector to enrich APM OTLP data with host metadata before sending to New Relic via OTLP.

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
FROM Metric SELECT uniques(metricName) WHERE otel.library.name like '%/receiver/hostmetricsreceiver%'
```

See [get started with querying](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) for additional details on querying data in New Relic.

## Additional notes

This example deploys the collector as a kubernetes DaemonSet to run a collector instance on each node in the kubernetes cluster. When running in this type of configuration, it's common to route application telemetry from pods to the collector instance each pod is respectively running on, and to enrich that telemetry with additional metadata via the [kubernetes attributes processor](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/k8sattributesprocessor). This example omits that configuration for brevity. See [important components for kubernetes](https://opentelemetry.io/docs/kubernetes/collector/components/#filelog-receiver) for common configuration running the collector in kubernetes.

This example makes optimizations in an effort to reduce the exported data volume: 

* Configure the [transform processor](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/transformprocessor) to override the metric description and unit.
* Configures the [cumulativetodelta processor](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/cumulativetodeltaprocessor) to transform cumulative host metrics to the delta metrics [preferred by the New Relic OTLP endpoint](https://docs.newrelic.com/docs/opentelemetry/best-practices/opentelemetry-otlp/#metric-aggregation-temporality). 
* Disables process metrics by default. These tend to be noisy and many users tend to tune them to the set most valuable. 
* Configure the [filter processor](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/filterprocessor), [metricstransform processor](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/metricstransformprocessor), and [attributes processor](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/attributesprocessor) to filter and merge various metric series not relevant to the New Relic host UI.

These optimizations can be adjusted if required, at the expense of higher data ingest.

In order to demonstrate correlation between OpenTelemetry APM entities and host entities, this example deploys an instance of the opentelemetry demo [AdService](https://opentelemetry.io/docs/demo/services/ad/), defined in [adservice.yaml](./k8s/adservice.yaml). The AdService application is configured to export OTLP data to the collector DaemonSet pod running on the same host. The collector enriches the AdService telemetry with `host.id` (and other attributes) which New Relic uses to create a relationship with the host entity.
