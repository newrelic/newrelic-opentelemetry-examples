# Instrumented Go Demo App

This is the solution (completely instrumented with OpenTelemetry) for the Go demo application used in the Getting Started Guide - Go doc.

## Prerequisites

- Go 1.18+ (1.20 is used in this example)
- [A New Relic account](https://one.newrelic.com/)

## Setting up environment variables

```
export OTEL_SERVICE_NAME=getting-started-go
export OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_license_key>
export OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317
export OTEL_ATTRIBUTE_VALUE_LENGTH_LIMIT=4095
```

### Remarks

- Make sure to use your [ingest license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)
- If your account is based in the EU, set the endpoint to: `https://otlp.eu01.nr-data.net:4317`

## Running the demo application

Run the following command:

```go
go run *.go
```

## Generate traffic

Run the following command in a new terminal tab:

```shell
bash ./load-generator.sh
```

## Clean up

To shut down the program, run the following in both shells or terminal tabs: `ctrl + c`.
