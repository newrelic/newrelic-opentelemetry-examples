# Monitor Kubernetes with Newrelic infra agent and correlate with OpenTelemetry APM services

This example demonstrates correlation between kubernetes container monitored with the [New Relic infrastructure agent](https://docs.newrelic.com/docs/infrastructure/introduction-infra-monitoring/) and OpenTelemetry APM services.


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

  2. Run the application with the following command.

      ```shell
      kubectl create namespace opentelemetry-demo
      kubectl apply -n opentelemetry-demo -f k8s/
      ```

      * When finished, cleanup resources with the following command. This is also useful to reset if modifying configuration.

      ```shell
      kubectl delete -n opentelemetry-demo -f k8s/
      ```
  3. Install the NR Infra Kubernetes agent the following command.

      ```shell
      KSM_IMAGE_VERSION="v2.10.0" && helm repo add newrelic https://helm-charts.newrelic.com && helm repo update && helm upgrade --install newrelic-bundle newrelic/nri-bundle --set global.licenseKey=<<NEW_RELIC_API_KEY>> --set global.cluster=opentelemetry-demo --namespace=opentelemetry-demo --set newrelic-infrastructure.privileged=true --set global.lowDataMode=true --set kube-state-metrics.image.tag=${KSM_IMAGE_VERSION} --set kube-state-metrics.enabled=true --set kubeEvents.enabled=true --set newrelic-prometheus-agent.enabled=true --set newrelic-prometheus-agent.lowDataMode=true --set newrelic-prometheus-agent.config.kubernetes.integrations_filter.enabled=false --set logging.enabled=true --set newrelic-logging.lowDataMode=true
      ```
## Viewing your data

To review your kubernetes container data in New Relic, navigate to "New Relic -> All Entities -> Containers". You should see entities named `adservice` as defined in `name` property of the respective services in [deployment.yaml](k8s/deployement.yaml). Click to view the container summary.

To review your OpenTelemetry APM data in New Relic, navigate to "New Relic -> All Entities -> OpenTelemetry" and You should see an entity named `adservice` as defined in `OTEL_SERVICE_NAME` in `deployment.yaml`. Click to view the OpenTelemetry summary. Click "Service Map" in the left navigation, and notice the relationship to the `adservice` container entity.