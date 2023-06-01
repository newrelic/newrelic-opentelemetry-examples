const { NodeSDK } = require("@opentelemetry/sdk-node");
const {
  OTLPTraceExporter,
} = require("@opentelemetry/exporter-trace-otlp-proto");
const {
  OTLPMetricExporter,
} = require("@opentelemetry/exporter-metrics-otlp-proto");
const {
  getNodeAutoInstrumentations,
} = require("@opentelemetry/auto-instrumentations-node");
const { PeriodicExportingMetricReader } = require("@opentelemetry/sdk-metrics");

const { Resource } = require("@opentelemetry/resources");
const {
  SemanticResourceAttributes,
} = require("@opentelemetry/semantic-conventions");

const sdk = new NodeSDK({
  resource: new Resource({
    [SemanticResourceAttributes.SERVICE_NAME]: "otel-node-server-auto",
    [SemanticResourceAttributes.SERVICE_INSTANCE_ID]:
      "otel-node-server-auto-instance",
    [SemanticResourceAttributes.SERVICE_VERSION]: "1.0.0",
  }),
  traceExporter: new OTLPTraceExporter({
    url: "https://otlp.nr-data.net:4318/v1/traces", // https://otlp.eu01.nr-data.net:4318/v1/traces for EU
    headers: {
      "api-key": `${process.env.NEW_RELIC_LICENSE_INGEST_KEY}`,
    },
  }),
  metricReader: new PeriodicExportingMetricReader({
    exporter: new OTLPMetricExporter({
      url: "https://otlp.nr-data.net:4318/v1/metrics", // https://otlp.eu01.nr-data.net:4318/v1/traces for EU
      headers: {
        "api-key": `${process.env.NEW_RELIC_LICENSE_INGEST_KEY}`,
      },
    }),
  }),
  instrumentations: [getNodeAutoInstrumentations()],
});

sdk.start();
