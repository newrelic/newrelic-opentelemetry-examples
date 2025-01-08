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
require("dotenv").config({ path: __dirname + "/.env" });

const collectorOptions = {
  // url is optional and can be omitted - default is http://localhost:4317
  // Unix domain sockets are also supported: 'unix:///path/to/socket.sock'
  url: "http://otel-collector-container-nr-agent:4317",
};

const {
  dockerCGroupV1Detector,
} = require('@opentelemetry/resource-detector-docker');

const metricExporter = new OTLPMetricExporter(collectorOptions);

const zipkinExporter = new ZipkinExporter({
  url: `${process.env.ZIPKIN_EXPORTER_ENDPOINT}`,
  serviceName: `${process.env.OTEL_SERVICE_NAME_NODE}`,
});

const metricReader = new PeriodicExportingMetricReader({
  exporter: metricExporter,
  exportIntervalMillis: 10000,
});
console.log(
  "FFFFFFF OTEL_SERVICE_NAME_NODE",
  process.env.OTEL_SERVICE_NAME_NODE
);
const sdk = new NodeSDK({
  resource: new Resource({
    [SEMRESATTRS_SERVICE_NAME]: `${process.env.OTEL_SERVICE_NAME_NODE}`,
    [SEMRESATTRS_SERVICE_INSTANCE_ID]: `${process.env.OTEL_SERVICE_NAME_NODE}-instance`,
    [SEMRESATTRS_SERVICE_VERSION]: "1.0.0",
  }),
  resourceDetectors: [containerDetector, dockerCGroupV1Detector],
  metricReader: metricReader,
  traceExporter: zipkinExporter,
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
