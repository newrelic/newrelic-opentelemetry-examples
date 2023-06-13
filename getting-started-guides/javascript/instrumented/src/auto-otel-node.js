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
const { MeterProvider } = require("@opentelemetry/sdk-metrics");

const meterProvider = new MeterProvider({
  exporter: new OTLPMetricExporter({
    url: `${process.env.OTEL_EXPORTER_OTLP_ENDPOINT}/v1/metrics`,
    headers: {
      "api-key": `${process.env.NEW_RELIC_LICENSE_INGEST_KEY}`,
    },
  }),
  interval: 1000,
});

const sdk = new NodeSDK({
  resource: new Resource({
    [SemanticResourceAttributes.SERVICE_NAME]: `${process.env.OTEL_SERVICE_NAME_NODE}`,
    [SemanticResourceAttributes.SERVICE_INSTANCE_ID]: `${process.env.OTEL_SERVICE_NAME_NODE}-instance`,
    [SemanticResourceAttributes.SERVICE_VERSION]: "1.0.0",
  }),
  traceExporter: new OTLPTraceExporter({
    url: `${process.env.OTEL_EXPORTER_OTLP_ENDPOINT}/v1/traces`,
    headers: {
      "api-key": `${process.env.NEW_RELIC_LICENSE_INGEST_KEY}`,
    },
  }),
  metricReader: new PeriodicExportingMetricReader({
    exporter: meterProvider.getMeter(),
  }),
  instrumentations: [getNodeAutoInstrumentations()],
});

sdk.configureMeterProvider(meterProvider);
sdk.start();

module.exports = {
  meterProvider,
};
