// Start Node SDK
const { NodeSDK } = require("@opentelemetry/sdk-node");
const {
  getNodeAutoInstrumentations,
} = require("@opentelemetry/auto-instrumentations-node");
const { PeriodicExportingMetricReader } = require("@opentelemetry/sdk-metrics");

const { Resource } = require("@opentelemetry/resources");
const {
  SEMRESATTRS_SERVICE_VERSION,
  SEMRESATTRS_SERVICE_NAME,
  SEMRESATTRS_SERVICE_INSTANCE_ID,
} = require("@opentelemetry/semantic-conventions");
const { ZipkinExporter } = require("@opentelemetry/exporter-zipkin");
const {
  OTLPMetricExporter,
} = require("@opentelemetry/exporter-metrics-otlp-grpc");
const {
  containerDetector,
} = require('@opentelemetry/resource-detector-container');

const {
  OTLPTraceExporter,
} = require('@opentelemetry/exporter-trace-otlp-proto');

const os = require('os');

require("dotenv").config({ path: __dirname + "/.env" });


const {
  dockerCGroupV1Detector,
} = require('@opentelemetry/resource-detector-docker');

const metricExporter = new OTLPMetricExporter();

const metricReader = new PeriodicExportingMetricReader({
  exporter: metricExporter,
  exportIntervalMillis: 10000,
});
console.log(
  "FFFFFFF OTEL_SERVICE_NAME_NODE",
  process.env.OTEL_SERVICE_NAME
);

const hostId = os.hostname(); 

const sdk = new NodeSDK({
  resource: new Resource({
    [SEMRESATTRS_SERVICE_NAME]: `${process.env.OTEL_SERVICE_NAME}`,
    [SEMRESATTRS_SERVICE_INSTANCE_ID]: `${process.env.OTEL_SERVICE_NAME}-instance`,
    [SEMRESATTRS_SERVICE_VERSION]: "1.0.0",
    "host.id": hostId,
    "host.name": hostId,
  }),
  resourceDetectors: [containerDetector, dockerCGroupV1Detector],
  metricReader: metricReader,
  traceExporter: new OTLPTraceExporter(),
  instrumentations: [getNodeAutoInstrumentations()],
});

sdk.start();

process.on("SIGTERM", () => {
  console.log("OTel SDK shutting down (SIGTERM)");
  sdk
    .shutdown()
    .then(
      () => console.log("OTel SDK shutdown successfully"),
      (err) => console.log("OTel SDK shutdown error:", err)
    )
    .finally(() => process.exit());
});
