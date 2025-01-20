# Monitoring Hosts with OpenTelemetry Collector

This example demonstrates monitoring kubernetes with the [OpenTelemetry collector](https://opentelemetry.io/docs/collector/) and sending the data to New Relic via OTLP.

Additionally, it demonstrates correlating OTEL entities with kubernetes, using the OpenTelemetry collector.

## Requirements

* You need to have a Kubernetes cluster, and the kubectl command-line tool must be configured to communicate with your cluster. Docker desktop [includes a standalone Kubernetes server and client](https://docs.docker.com/desktop/kubernetes/) which is useful for local testing.
* [A New Relic account](https://one.newrelic.com/)
* [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Running the example

 1. Update the `NEW_RELIC_API_KEY` value in [configmap.yaml](./opentelemetry-collector/configmap.yml) to your New Relic license key.
    ```yaml
    # ...omitted for brevity
     otlphttp:
        endpoint: https://otlp.nr-data.net:4318
        headers:
          api-key: #NEW_RELIC_API_KEY
          insecure: true
        # New Relic API key to authenticate the export requests.
        # docs: https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key
    ```
2. Update the `NEW_RELIC_API_KEY` value in [daemonset.yaml](./opentelemetry-collector/daemonset.yml) to your New Relic license key.
    ```yaml

    ```yaml
     # ...omitted for brevity
         otlphttp:
            endpoint: https://otlp.nr-data.net:4318
            headers:
            api-key: #NEW_RELIC_API_KEY
        # New Relic API key to authenticate the export requests.
        # 
     ```


 3. Install and Configure Microservices Application:  

    cd opentelemetry-app
    kubectl create namespace otel-demo
    kubectl apply -n otel-demo -f .

 4. Install and Configure Open Telemetry Collector:  

    This will collect traces from application and captures at the collector port.

    cd opentelemetry-collector
     kubectl apply -n otel-demo -f .


    