const { Resource } = require("@opentelemetry/resources");
const {
  SemanticResourceAttributes,
} = require("@opentelemetry/semantic-conventions");
const { NodeTracerProvider } = require("@opentelemetry/sdk-trace-node");
const { registerInstrumentations } = require("@opentelemetry/instrumentation");
const {
  OTLPTraceExporter,
} = require("@opentelemetry/exporter-trace-otlp-proto");
const { BatchSpanProcessor } = require("@opentelemetry/sdk-trace-base");
const {
  getNodeAutoInstrumentations,
} = require("@opentelemetry/auto-instrumentations-node");
const {
  MeterProvider,
  PeriodicExportingMetricReader,
} = require("@opentelemetry/sdk-metrics");
const {
  OTLPMetricExporter,
} = require("@opentelemetry/exporter-metrics-otlp-proto");

// Optionally register instrumentation libraries
registerInstrumentations({
  instrumentations: [getNodeAutoInstrumentations()],
});

const resource = Resource.default().merge(
  new Resource({
    [SemanticResourceAttributes.SERVICE_NAME]: "otel-node-server-manual",
    [SemanticResourceAttributes.SERVICE_INSTANCE_ID]:
      "otel-node-server-manual-instance",
    [SemanticResourceAttributes.SERVICE_VERSION]: "1.0.0",
  })
);

// Tracer Exporter
const provider = new NodeTracerProvider({
  resource: resource,
});
const exporter = new OTLPTraceExporter({
  url: "https://otlp.nr-data.net:4318/v1/traces", // https://otlp.eu01.nr-data.net:4318/v1/traces for EU
  headers: {
    "api-key": `${process.env.NEW_RELIC_LICENSE_INGEST_KEY}`,
  },
});
const processor = new BatchSpanProcessor(exporter);
provider.addSpanProcessor(processor);
provider.register();

// Metric Exporter
const metricExporter = new OTLPMetricExporter({
  url: "https://otlp.nr-data.net:4318/v1/metrics", // https://otlp.eu01.nr-data.net:4318/v1/metrics for EU
  headers: {
    "api-key": `${process.env.NEW_RELIC_LICENSE_INGEST_KEY}`,
  },
});
const metricReader = new PeriodicExportingMetricReader({
  exporter: metricExporter,
});
const meterProvider = new MeterProvider({
  metricReader,
  resource,
});
meterProvider.getMeter("otel-node-server-manual");
metricReader.forceFlush();
