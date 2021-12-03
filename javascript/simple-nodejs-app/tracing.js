"use strict";

const { v4: uuidv4 } = require("uuid");
const grpc = require("@grpc/grpc-js");
const process = require("process");
const opentelemetry = require("@opentelemetry/sdk-node");
const { getNodeAutoInstrumentations } = require("@opentelemetry/auto-instrumentations-node");
const { Resource } = require("@opentelemetry/resources");
const { SemanticResourceAttributes } = require("@opentelemetry/semantic-conventions");
const { CollectorTraceExporter } = require("@opentelemetry/exporter-collector-grpc");

// Step 1. Declare the resource to be used.
//    A resource represents a collection of attributes describing the
//    service. This collection of attributes will be associated with all
//    telemetry generated from this service (traces, metrics, logs).
const resource = new Resource({
  [SemanticResourceAttributes.SERVICE_NAME]: "OpenTelemetry-Node.JS-Example",
  [SemanticResourceAttributes.SERVICE_INSTANCE_ID]: uuidv4(),
});

// Step 2: Enable auto-instrumentation from the meta package.
const instrumentations = [getNodeAutoInstrumentations()];

// Step 3: Configure the gRPC/OTLP trace exporter.
//    The following assumes you've set the OTEL_EXPORTER_OTLP_ENDPOINT and OTEL_EXPORTER_OLTP_HEADERS
//    environment variables.

// New Relic requires TLS.
const credentials = grpc.credentials.createSsl();

const collectorOptions = {
  credentials,
};

const traceExporter = new CollectorTraceExporter(collectorOptions);

// If you haven't set the OTEL_EXPORTER_OTLP_ENDPOINT and OTEL_EXPORTER_OLTP_HEADERS
// environment variables, you can configure the OTLP exporter programmatically by
// uncommenting the following code

// const metadata = new grpc.Metadata();
// metadata.set("api-key", "<YOUR_API_KEY_HERE>");

// const url = "grpc://otlp.nr-data.net:4317";

// const credentials = grpc.credentials.createSsl();

// const collectorOptions = {
//   url,
//   metadata,
//   credentials,
// };

// const traceExporter = new CollectorTraceExporter(collectorOptions);

// Step 4: Configure the OpenTelemetry NodeSDK to export traces.
const sdk = new opentelemetry.NodeSDK({
  resource,
  traceExporter,
  instrumentations,
});

// Step 5: Initialize the SDK and register with the OpenTelemetry API:
//    this enables the API to record telemetry
sdk
  .start()
  .then(() => console.log("Tracing initialized"))
  .catch((error) => console.log("Error initializing tracing", error));

// Step 6: Gracefully shut down the SDK on process exit
process.on("SIGTERM", () => {
  sdk
    .shutdown()
    .then(() => console.log("Tracing terminated"))
    .catch((error) => console.log("Error terminating tracing", error))
    .finally(() => process.exit(0));
});
