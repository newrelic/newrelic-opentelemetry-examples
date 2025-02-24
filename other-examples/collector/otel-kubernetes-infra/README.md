# Monitor Kubernetes with OpenTelemetry collector and correlate with OpenTelemetry APM services

This example demonstrates correlation between kubernetes container monitored with the [OpenTelemetry collector](https://opentelemetry.io/docs/collector/) and OpenTelemetry APM services.


## Requirements

* You need to have a Kubernetes cluster, and the kubectl command-line tool must be configured to communicate with your cluster. Docker desktop [includes a standalone Kubernetes server and client](https://docs.docker.com/desktop/kubernetes/) which is useful for local testing.
* [A New Relic account](https://one.newrelic.com/)
* [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Running the example

 1. Update the `NEW_RELIC_API_KEY` value in [config.yaml](./k8s/config.yml) to your New Relic license key.

    ```yaml
    # ...omitted for brevity
     otlphttp:
        endpoint: https://otlp.nr-data.net
        headers:
          api-key: <NEW_RELIC_API_KEY>
        # New Relic API key to authenticate the export requests.
        # docs: https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key
    ```
2. Update the `NEW_RELIC_API_KEY` value in [values.yaml](.values.yml) to your New Relic license key.

    ```yaml
     # ...omitted for brevity
        licenseKey: "<NEW_RELIC_LICENSE_KEY>"
        # New Relic API key to authenticate the export requests.
        # 
     ```

 3. Install and Configure nr-k8s-otel-collector:  

    https://docs.newrelic.com/docs/kubernetes-pixie/kubernetes-integration/installation/k8s-otel/#install
    To get OpenTelemetry up and running in your cluster, follow these steps:

   * Download the [Helm chart values file](https://github.com/newrelic/helm-charts/tree/master/charts/nr-k8s-otel-collector/values.yaml#L20-L24) adapt it to meet your specific requirements.

      * Cluster name and <InlinePopover type="licenseKey"/> are mandatory.

      * Check the entire list of [configuration parameters](https://github.com/newrelic/helm-charts/tree/master/charts/nr-k8s-otel-collector#values).

   * Install the [Helm chart](https://github.com/newrelic/helm-charts/tree/master/charts/nr-k8s-otel-collector) together with the values file.

      ```shell
      helm repo add newrelic https://helm-charts.newrelic.com
      helm upgrade nr-k8s-otel-collector newrelic/nr-k8s-otel-collector -f values.yaml -n newrelic --create-namespace --install
      ```    
  4. Run the application with the following command.

      ```shell
      kubectl create namespace opentelemetry-demo

      kubectl apply -n opentelemetry-demo -f k8s/

      ```
   
      * When finished, cleanup resources with the following command. This is also useful to reset if modifying configuration.

      ```shell
      kubectl delete -n opentelemetry-demo -f k8s/
      ```
## Viewing your data

To review your kubernetes container data in New Relic, navigate to "New Relic -> All Entities -> Containers". You should see entities named `adservice` as defined in `name` property of the respective services in [deployment.yaml](k8s/deployement.yaml). Click to view the container summary.

To review your OpenTelemetry APM data in New Relic, navigate to "New Relic -> All Entities -> OpenTelemetry" and You should see an entity named `adservice` as defined in `OTEL_SERVICE_NAME` in `deployment.yaml`. Click to view the OpenTelemetry summary. Click "Service Map" in the left navigation, and notice the relationship to the `adservice` container entity.

## Additional notes

This example deploys an instance of the opentelemetry demo [AdService](https://opentelemetry.io/docs/demo/services/ad/), defined in `deployement.yaml` `.services.adservice`. The AdService is instrumented with the [OpenTelemetry Java Agent](https://opentelemetry.io/docs/languages/java/instrumentation/#zero-code-java-agent) and is configured to export data via OTLP to New Relic. 

See [get started with querying](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) for additional details on querying data in New Relic.

    
   


    