# Internal Telemetry for nr-k8s-otel-collector Helm Chart

This example demonstrates how to enable comprehensive internal telemetry for the OpenTelemetry collectors deployed via the [newrelic/nr-k8s-otel-collector](https://github.com/newrelic/helm-charts/tree/master/charts/nr-k8s-otel-collector) helm chart. This configuration enables detailed monitoring of the collectors themselves, including metrics, logs, and optionally traces.

The nr-k8s-otel-collector chart includes a built-in option for internal telemetry, but it doesn't cover all available features. This example shows how to use the full [internal telemetry configuration](https://github.com/newrelic/nrdot-collector-releases/blob/main/examples/internal-telemetry-config.yaml) for more comprehensive observability.

## Requirements

* A Kubernetes cluster with kubectl configured
* Helm 3.x installed
* [A New Relic account](https://one.newrelic.com/)
* [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Running the example

1. Copy the template file and update with your values:

    ```shell
    cp secrets.yaml.template secrets.yaml
    ```

2. Edit `secrets.yaml` and update the following values:
    * `NEW_RELIC_LICENSE_KEY`: Your New Relic license key
    * `CLUSTER_NAME`: Your cluster name
    * `NEW_RELIC_OTLP_ENDPOINT`: If your account is based in the EU, change to `https://otlp.eu01.nr-data.net`

3. Add the New Relic helm repository:

    ```shell
    helm repo add newrelic https://helm-charts.newrelic.com
    helm repo update
    ```

4. Create the namespace and apply the secrets and ConfigMap:

    ```shell
    kubectl create namespace internal-telemetry-nr-k8s-otel-collector
    kubectl apply -f secrets.yaml
    kubectl apply -f internal-telemetry-config.yaml
    ```

5. Install the helm chart with the custom values:

    ```shell
    helm install nr-k8s-otel-collector newrelic/nr-k8s-otel-collector \
      --namespace internal-telemetry-nr-k8s-otel-collector \
      -f values.yaml
    ```

    * When finished, cleanup all resources by deleting the namespace:

    ```shell
    kubectl delete namespace internal-telemetry-nr-k8s-otel-collector
    ```

## Viewing your data

To review your collector internal telemetry in New Relic, navigate to "New Relic -> All Entities" and search for entities with the service names you configured (by default: `nr-k8s-otel-collector-deployment` and `nr-k8s-otel-collector-daemonset`). Click on an entity to view the service summary, including golden metrics and performance data for the collector itself.

## Additional notes

### Customizing telemetry levels

You can customize the telemetry levels by adding additional environment variables in [values.yaml](./values.yaml):

- `INTERNAL_TELEMETRY_METRICS_LEVEL`: `detailed` (default), `normal`, `basic`, or `none`
- `INTERNAL_TELEMETRY_LOG_LEVEL`: `INFO` (default), `DEBUG`, `WARN`, or `ERROR`
- `INTERNAL_TELEMETRY_TRACE_LEVEL`: `none` (default) or `basic` (experimental)
- `INTERNAL_TELEMETRY_TRACE_SAMPLE_RATIO`: `0.01` (default, 1% sampling)

### Two collectors

The nr-k8s-otel-collector chart deploys two collectors:
- A **deployment** collector for cluster-level metrics
- A **daemonset** collector that runs on each node for host-level metrics

This example configures internal telemetry for both, with distinct service names to differentiate them in New Relic.
