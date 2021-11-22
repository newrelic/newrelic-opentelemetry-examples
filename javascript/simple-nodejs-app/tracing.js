// https://github.com/open-telemetry/opentelemetry-js#instantiate-tracing

"use strict";

const { v4: uuidv4 } = require("uuid");
const grpc = require("@grpc/grpc-js");

const process = require("process");
const opentelemetry = require("@opentelemetry/sdk-node");
const {
  getNodeAutoInstrumentations,
} = require("@opentelemetry/auto-instrumentations-node");
const { Resource } = require("@opentelemetry/resources");
const {
  SemanticResourceAttributes,
} = require("@opentelemetry/semantic-conventions");
const {
  CollectorTraceExporter,
} = require("@opentelemetry/exporter-collector-grpc");

// Step 1. Set up collectorOptions to be used by the OTLP exporter
//    TLS encryption is required
const collectorOptions = {
//    In order to use TLS in node.js, credentials option is needed here
  credentials: grpc.credentials.createSsl(),
};

// Step 2. Configure the OTLP exporter to export to New Relic:
//    The OTEL_EXPORTER_OTLP_ENDPOINT environment variable should be set to New Relic's OTLP endpoint:
//      OTEL_EXPORTER_OTLP_ENDPOINT=grpc://otlp.nr-data.net:4317
//    The OTEL_EXPORTER_OTLP_HEADERS environment variable should be set to include your New Relic API ingest license key:
//      OTEL_EXPORTER_OTLP_HEADERS=api-key=<YOUR_API_KEY_HERE>

// Alternative to Step 2: Declare New Relic's OTLP endpoint and API ingest license key inside the collectorOptions:
//    Example:
//      create a metadata object to be send with each request
//      const metadata = new grpc.Metadata();

//      set New Relic's API ingest license key
//      metadata.set('api-key', '<YOUR_API_KEY_HERE>');

//      const collectorOptions = {
//        url: 'grpc://otlp.nr-data.net:4317',
//        metadata,
//        credentials: grpc.credentials.createSsl(),
//      };

// Step 3: Configure the SDK to export traces:
const traceExporter = new CollectorTraceExporter(collectorOptions);
const sdk = new opentelemetry.NodeSDK({
//    add service name
//    service instance id is auto-generated by the uuid package
  resource: new Resource({
    [SemanticResourceAttributes.SERVICE_NAME]: "OpenTelemetry-Node.JS-Example",
    [SemanticResourceAttributes.SERVICE_INSTANCE_ID]: uuidv4(),
  }),
  traceExporter,
//    enable all auto-instrumentations from the meta package
  instrumentations: [getNodeAutoInstrumentations()],
});

// Step 4: Initialize the SDK and register with the OpenTelemetry API:
//    this enables the API to record telemetry
sdk
  .start()
  .then(() => console.log("Tracing initialized"))
  .catch((error) => console.log("Error initializing tracing", error));

// Step 5: Gracefully shut down the SDK on process exit
process.on("SIGTERM", () => {
  sdk
    .shutdown()
    .then(() => console.log("Tracing terminated"))
    .catch((error) => console.log("Error terminating tracing", error))
    .finally(() => process.exit(0));
});
