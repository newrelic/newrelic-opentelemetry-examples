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
  MeterProvider,
  PeriodicExportingMetricReader,
} = require("@opentelemetry/sdk-metrics");
const {
  OTLPMetricExporter,
} = require("@opentelemetry/exporter-metrics-otlp-proto");
const opentelemetry = require("@opentelemetry/api");

// Optionally register instrumentation libraries
registerInstrumentations({
  instrumentations: [],
});

const resource = Resource.default().merge(
  new Resource({
    [SemanticResourceAttributes.SERVICE_NAME]: `${process.env.OTEL_SERVICE_NAME_NODE}`, // getting-started-js-node
    [SemanticResourceAttributes.SERVICE_INSTANCE_ID]: `${process.env.OTEL_SERVICE_NAME_NODE}-instance`, // getting-started-js-node-instance
    [SemanticResourceAttributes.SERVICE_VERSION]: "1.0.0",
  })
);

// Traces
const traceProvider = new NodeTracerProvider({
  resource: resource,
});
const traceExporter = new OTLPTraceExporter({
  url: `${process.env.OTEL_EXPORTER_OTLP_ENDPOINT}/v1/traces`,
  headers: {
    "api-key": `${process.env.NEW_RELIC_LICENSE_INGEST_KEY}`,
  },
});
const spanProcessor = new BatchSpanProcessor(traceExporter);
traceProvider.addSpanProcessor(spanProcessor);
traceProvider.register();

// Metrics
const meterProvider = new MeterProvider({
  resource,
});
const metricExporter = new OTLPMetricExporter({
  url: `${process.env.OTEL_EXPORTER_OTLP_ENDPOINT}/v1/metrics`,
  headers: {
    "api-key": `${process.env.NEW_RELIC_LICENSE_INGEST_KEY}`,
  },
});
const metricReader = new PeriodicExportingMetricReader({
  exporter: metricExporter,
  exportIntervalMillis: 10000,
});
meterProvider.addMetricReader(metricReader);
opentelemetry.metrics.setGlobalMeterProvider(meterProvider);
