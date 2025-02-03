# Monitor Kubernetes with New Relic's Kubernetes integration and correlate with OpenTelemetry APM services

This example demonstrates correlation between kubernetes containers monitored with the [New Relic's Kubernetes integration](hhttps://docs.newrelic.com/install/kubernetes/) and OpenTelemetry APM services.

The New Relic infrastructure agent and a sample OpenTelemetry APM service are each run via kubernetes. The New Relic's Kubernetes integration [automatically monitors containers](https://docs.newrelic.com/install/kubernetes/). The OpenTelemetry APM service automatically detects and reports [container resource attributes](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/resource/container.md) which New Relic uses to create a relationship between the container and APM service entities.


## Requirements

* You need to have a Kubernetes cluster, and the kubectl command-line tool must be configured to communicate with your cluster. Docker desktop [includes a standalone Kubernetes server and client](https://docs.docker.com/desktop/kubernetes/) which is useful for local testing.
* Helm 3.9+
* [A New Relic account](https://one.newrelic.com/)
* [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)


## Install the Chart and Running the example

  Add OpenTelemetry Helm repository:

  ```console
  helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts && helm repo update open-telemetry
  ```

  Set your New Relic license key environment variable:

  ```console
  export NEW_RELIC_LICENSE_KEY='<<NEW_RELIC_API_KEY>>'
  ```

  Set a Kubernetes secret containing your New Relic license key:

  ```console
  kubectl create ns opentelemetry-demo && kubectl create secret generic newrelic-license-key --from-literal=license-key="$NEW_RELIC_LICENSE_KEY" -n opentelemetry-demo
  ```

  To install the chart with the release name newrelic-otel, run the following
  command and pass in the provided `values.yaml` file to customize the deployment:

  ```console
  helm upgrade --install newrelic-otel open-telemetry/opentelemetry-demo --version 0.32.0 --values ./helm/values.yaml -n opentelemetry-demo
  ```

  **Remark:** If your New Relic account is in Europe, install the chart as follows instead:

  ```console
  helm upgrade --install newrelic-otel open-telemetry/opentelemetry-demo --values ./helm/values.yaml --set opentelemetry-collector.config.exporters.otlp.endpoint="otlp.eu01.nr-data.net:4317" -n opentelemetry-demo

  ```
  To Run NR Infra Kubernetes please run following command

  ```console
  KSM_IMAGE_VERSION="v2.10.0" && helm repo add newrelic https://helm-charts.newrelic.com && helm repo update && kubectl create namespace opentelemetry-demo ; helm upgrade --install newrelic-bundle newrelic/nri-bundle --set global.licenseKey=<<NEW_RELIC_API_KEY>> --set global.cluster=opentelemetry-demo --namespace=opentelemetry-demo --set newrelic-infrastructure.privileged=true --set global.lowDataMode=true --set kube-state-metrics.image.tag=${KSM_IMAGE_VERSION} --set kube-state-metrics.enabled=true --set kubeEvents.enabled=true --set newrelic-prometheus-agent.enabled=true --set newrelic-prometheus-agent.lowDataMode=true --set newrelic-prometheus-agent.config.kubernetes.integrations_filter.enabled=false --set logging.enabled=true --set newrelic-logging.lowDataMode=true
  ```