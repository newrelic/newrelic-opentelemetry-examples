# Monitoring Hosts with OpenTelemetry Collector

This example demonstrates monitoring kubernetes with the [OpenTelemetry collector](https://opentelemetry.io/docs/collector/) and sending the data to New Relic via OTLP.

Additionally, it demonstrates correlating OTEL entities with kubernetes, using the OpenTelemetry collector.

## Requirements

* You need to have a Kubernetes cluster, and the kubectl command-line tool must be configured to communicate with your cluster. Docker desktop [includes a standalone Kubernetes server and client](https://docs.docker.com/desktop/kubernetes/) which is useful for local testing.
* [A New Relic account](https://one.newrelic.com/)
* [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Running the example

 1. Update the `NEW_RELIC_API_KEY` value in [config.yaml](./k8s/config.yml) to your New Relic license key.

    ```yaml
    # ...omitted for brevity
     otlp:
        endpoint: https://otlp.nr-data.net
        headers:
          api-key: <NEW_RELIC_API_KEY>
          insecure: true
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


 3. Install and Configure Microservices Application:  

    kubectl create namespace opentelemetry-demo
    kubectl apply -n opentelemetry-demo -f k8s/

 4. Install and Configure nr-k8s-otel-collector:  

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
   


    