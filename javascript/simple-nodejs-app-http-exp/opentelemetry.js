"use strict";

const { v4: uuidv4 } = require("uuid");
const process = require("process");
const opentelemetry = require("@opentelemetry/sdk-node");
const { getNodeAutoInstrumentations } = require("@opentelemetry/auto-instrumentations-node");
const { Resource } = require("@opentelemetry/resources");
const { SemanticResourceAttributes } = require("@opentelemetry/semantic-conventions");
const { OTLPTraceExporter } = require("@opentelemetry/exporter-trace-otlp-proto");
const { OTLPMetricExporter } = require("@opentelemetry/exporter-metrics-otlp-proto");
const { AggregationTemporality, PeriodicExportingMetricReader } = require('@opentelemetry/sdk-metrics');
const api = require("@opentelemetry/api");

// enable logging ONLY for developement
// this is useful for debugging instrumentation issues
// remove from production after issues (if any) are resolved
// view more logging levels here: https://github.com/open-telemetry/opentelemetry-js-api/blob/main/src/diag/types.ts#L67
api.diag.setLogger(
  new api.DiagConsoleLogger(),
  api.DiagLogLevel.DEBUG,
);

// Declare the resource to be used.
//    A resource represents a collection of attributes describing the
//    service. This collection of attributes will be associated with all
//    telemetry generated from this service (traces, metrics, logs).
const resource = new Resource({
  [SemanticResourceAttributes.SERVICE_NAME]: "OpenTelemetry-Node.JS-Example",
  [SemanticResourceAttributes.SERVICE_INSTANCE_ID]: uuidv4(),
});

// Enable auto-instrumentation from the meta package.
const instrumentations = [getNodeAutoInstrumentations()];

// Configure the OTLP/PROTO exporters.
//    The following assumes you've set the OTEL_EXPORTER_OTLP_ENDPOINT and OTEL_EXPORTER_OLTP_HEADERS
//    environment variables.
const traceExporter = new OTLPTraceExporter();
const metricExporter = new OTLPMetricExporter({
  temporalityPreference: AggregationTemporality.DELTA
});

// If you haven't set the OTEL_EXPORTER_OTLP_ENDPOINT and OTEL_EXPORTER_OLTP_HEADERS
// environment variables, you can configure the OTLP exporter programmatically by
// uncommenting the following code:

// this endpoint contains a path since this exporter is signal specific (traces)
// see more details here: https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/opentelemetry-quick-start/#note-endpoints
// const url = "https://otlp.nr-data.net:4317/v1/traces";

// const collectorOptions = {
//   url,
//   headers: {
//    'api-key': <YOUR_API_KEY_HERE>
//   }
// }

// const traceExporter = new OTLPTraceExporter(collectorOptions);
// const metricExporter = new OTLPMetricExporter(collectorOptions);

// Configure PeriodicMetricReader
const metricReader = new PeriodicExportingMetricReader({
  exporter: metricExporter,
  exportIntervalMillis: 5000,
})

// Configure the OpenTelemetry NodeSDK
const sdk = new opentelemetry.NodeSDK({
  resource,
  traceExporter,
  metricReader,
  instrumentations,
});

// Initialize the SDK and register with the OpenTelemetry API:
//    this enables the API to record telemetry
sdk
  .start()
  .then(() => console.log("OpenTelemetry initialized"))
  .catch((error) => console.log("Error initializing OpenTelemetry", error));

// Gracefully shut down the SDK on process exit
process.on("SIGTERM", () => {
  sdk
    .shutdown()
    .then(() => console.log("OpenTelemetry terminated"))
    .catch((error) => console.log("Error terminating OpenTelemetry", error))
    .finally(() => process.exit(0));
});
