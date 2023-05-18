const { Resource } = require("@opentelemetry/resources");
const {
  SemanticResourceAttributes,
} = require("@opentelemetry/semantic-conventions");
const { NodeTracerProvider } = require("@opentelemetry/sdk-trace-node");
const { registerInstrumentations } = require("@opentelemetry/instrumentation");
const {
  ConsoleSpanExporter,
  BatchSpanProcessor,
} = require("@opentelemetry/sdk-trace-base");
const {
  getNodeAutoInstrumentations,
} = require("@opentelemetry/auto-instrumentations-node");

// Optionally register instrumentation libraries
registerInstrumentations({
  instrumentations: [getNodeAutoInstrumentations()],
});

const resource = Resource.default().merge(
  new Resource({
    [SemanticResourceAttributes.SERVICE_NAME]: "otel-node-server",
    [SemanticResourceAttributes.SERVICE_VERSION]: "1.0.0",
  })
);

const provider = new NodeTracerProvider({
  resource: resource,
});
const exporter = new ConsoleSpanExporter();
const processor = new BatchSpanProcessor(exporter);
provider.addSpanProcessor(processor);

provider.register();
