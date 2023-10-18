# Getting Started Guides

This repo holds the source code for the demo applications used in the [Getting Started Guides](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/get-started/opentelemetry-get-started-intro/).

Each language directory illustrates how to add OpenTelemetry instrumentation to a simple web application, and configure OpenTelemetry for an optimal New Relic experience. This includes exporting over OTLP, limiting attributes according to New Relic ingest limits, and more.

In order to provide some degree of uniformity, each sample app is written to comply with the [demo app specification](./demo-app-specification.md). Each language contains the following sub-directories:

* **Uninstrumented:** Contains the uninstrumented version of the app.
* **Instrumented:** Contains the instrumented versions of the app. The uninstrumented app is enhanced with OpenTelemetry based instrumentation to generate metrics, logs, and traces, and SDK configuration aligned with New Relic [best practices](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/best-practices/opentelemetry-best-practices-overview/) to export telemetry to New Relic.

To run, please see the [Getting Started Guides](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/get-started/opentelemetry-get-started-intro/) documentation and follow the instructions in the README of the root of each respective sample app.
