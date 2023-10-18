# GCP Cloud Spanner OpenTelemetry metrics example setup

This example shows a setup for running a Docker OpenTelemetry Collector to scrape metrics from your GCP Cloud Spanner instance and post them the New Relic OTLP Collector Endpoint. You can view the metrics out of the box in New Relic by adding the [GCP Cloud Spanner dashboard](https://newrelic.com/instant-observability/google-cloud-spanner-otel) to your account.

## Pre-requisites: 
1. You must have a Docker daemon running
2. You must have docker compose installed | [Docker Compose docs](https://docs.docker.com/compose/)
3. You must have a GCP Cloud Spanner instance/application created | [GCP free account](https://cloud.google.com/spanner/docs/free-trial-quickstart)
4. You will need to generate a GCP service account key JSON file, and add it to this director as `key.json` | [GCP key creation docs](https://cloud.google.com/iam/docs/keys-create-delete)

## Running the example
To run the example: add in the key files, set the environment variables, and run `docker compose up`

```shell
export NEW_RELIC_API_KEY=<your_api_key>
export NEW_RELIC_OTLP_ENDPOINT=https://otlp.nr-data.net:4318
export SPANNER_PROJECT_ID=<your_project_id>
export SPANNER_INSTANCE_ID=<your_instance_id>
export SPANNER_DATABASE_ID=<your_database_id>

docker compose up
```
</br>

To add multiple Projects, Instances, or Databases to the collector, you can update the file named `collector.yaml` with the pertinent information. Here is an example:  *(note that you will need a second key if you add a second project)*

```yaml
    projects:
      - project_id: "spanner project 1"
        service_account_key: "path to spanner project 1 service account json key"
        instances:
          - instance_id: "id1"
            databases:
              - "db11"
              - "db12"
          - instance_id: "id2"
            databases:
              - "db21"
              - "db22"
      - project_id: "spanner project 2"
        service_account_key: "path to spanner project 2 service account json key"
        instances:
          - instance_id: "id3"
            databases:
              - "db31"
              - "db32"
          - instance_id: "id4"
            databases:
              - "db41"
              - "db42"

```

## Local Variable information

| Variable | Description | Docs |
| -------- | ----------- | ---- |
| **NEW_RELIC_API_KEY** |New Relic Ingest API Key |[API Key docs](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/) | 
| **NEW_RELIC_OTLP_ENDPOINT** | OTLP endpoint for NA is https://otlp.nr-data.net:4318 | [OTLP endpoint config docs](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/get-started/opentelemetry-set-up-your-app/#review-settings) |
| **SPANNER_PROJECT_ID** | ID of the GCP project | Available in your [Cloud Dashboard](https://console.cloud.google.com/home/dashboard) |
| **SPANNER_INSTANCE_ID** | ID of the Spanner instance |Available in your [Cloud Console](https://console.cloud.google.com/spanner/instances) |
| **SPANNER_DATABASE_ID**| ID of the Spanner Database |Available in your [Cloud Console](https://console.cloud.google.com/spanner/instances) under `Databases` |

</br>