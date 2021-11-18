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

const collectorOptions = {
  credentials: grpc.credentials.createSsl(),
};

const traceExporter = new CollectorTraceExporter(collectorOptions);
const sdk = new opentelemetry.NodeSDK({
  resource: new Resource({
    [SemanticResourceAttributes.SERVICE_NAME]: "OpenTelemetry-Node.JS-Example",
    [SemanticResourceAttributes.SERVICE_INSTANCE_ID]: uuidv4(),
  }),
  traceExporter,
  instrumentations: [getNodeAutoInstrumentations()],
});

sdk
  .start()
  .then(() => console.log("Tracing initialized"))
  .catch((error) => console.log("Error initializing tracing", error));

process.on("SIGTERM", () => {
  sdk
    .shutdown()
    .then(() => console.log("Tracing terminated"))
    .catch((error) => console.log("Error terminating tracing", error))
    .finally(() => process.exit(0));
});
