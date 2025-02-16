# Monitor Host with New Relic infrastructure agent and correlate with OpenTelemetry APM services

This example demonstrates Monitoring APM with OpenTelemetry Collector and monitoring hosts with the [Newrelic infra agent](https://docs.newrelic.com/docs/infrastructure/infrastructure-agent/linux-installation/package-manager-install).

Additionally, it demonstrates correlating APM entities with hosts, using the Newrelic infra agent and OpenTelemetry Collector.

## Requirements

* You need to have a Amazon Linux server with docker installed init.
* [A New Relic account](https://one.newrelic.com/)
* [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Running the example

1. Install the Newrelic Infra agent
   
   Please follow to link install the [Newrelic infra agent](https://docs.newrelic.com/docs/infrastructure/infrastructure-agent/linux-installation/)package-manager-install  

2. Update the `NEW_RELIC_API_KEY` value in [.env](.env) to your New Relic license key.
    ```yaml
      # New Relic API key to authenticate the export requests.
      # docs: https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key
      NEW_RELIC_API_KEY: <NEW_RELIC_API_KEY>
      NEW_RELIC_OTLP_ENDPOINT=https://otlp.nr-data.net
    ```

    * If your account is based in the EU, update the `NEW_RELIC_OTLP_ENDPOINT` value in [.env](.env) the endpoint to: [https://otlp.eu01.nr-data.net](https://otlp.eu01.nr-data.net)


3. Run the application with the following command.

    ```shell
    docker compose -f docker-compose.yml up -d 
    ```
   


## Viewing your data

To review your host data in New Relic, navigate to "New Relic -> All Entities -> Hosts" and click on the instance with name from aws 

In order to demonstrate correlation between OpenTelemetry APM entities and host entities Instrumented with NR infra, this example deploys an instance of the opentelemetry demo [AdService](https://opentelemetry.io/docs/demo/services/ad/), defined in [docker-compose.yaml](./docker-compose.yaml). The AdService application is configured to export OTLP data to the collector running on the same host. The collector enriches the AdService telemetry with `host.id` (and other attributes) which New Relic uses to create a relationship with the host entity.
