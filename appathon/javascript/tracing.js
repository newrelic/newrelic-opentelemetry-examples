"use strict";

const { v4: uuidv4 } = require("uuid");
const grpc = require("@grpc/grpc-js");
const process = require("process");
const opentelemetry = require("@opentelemetry/sdk-node");
const { getNodeAutoInstrumentations } = require("@opentelemetry/auto-instrumentations-node");
const { Resource } = require("@opentelemetry/resources");
const { SemanticResourceAttributes } = require("@opentelemetry/semantic-conventions");
const { OTLPTraceExporter } =  require('@opentelemetry/exporter-trace-otlp-grpc');

const resource = new Resource({
  [SemanticResourceAttributes.SERVICE_NAME]: "appathon-javascript",
  [SemanticResourceAttributes.SERVICE_INSTANCE_ID]: uuidv4(),
});

const instrumentations = [getNodeAutoInstrumentations()];

const credentials = grpc.credentials.createSsl();

const collectorOptions = {
  credentials,
};

const traceExporter = new OTLPTraceExporter(collectorOptions);

const sdk = new opentelemetry.NodeSDK({
  resource,
  traceExporter,
  instrumentations,
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