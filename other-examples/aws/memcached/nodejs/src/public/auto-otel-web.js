import { SimpleSpanProcessor } from "@opentelemetry/sdk-trace-base";
import { WebTracerProvider } from "@opentelemetry/sdk-trace-web";
import { getWebAutoInstrumentations } from "@opentelemetry/auto-instrumentations-web";
import { registerInstrumentations } from "@opentelemetry/instrumentation";
import { ZoneContextManager } from "@opentelemetry/context-zone";
import { OTLPTraceExporter } from "@opentelemetry/exporter-trace-otlp-proto";
import { Resource } from "@opentelemetry/resources";
import { SemanticResourceAttributes } from "@opentelemetry/semantic-conventions";

const provider = new WebTracerProvider({
  resource: new Resource({
    [SemanticResourceAttributes.SERVICE_NAME]: `${process.env.OTEL_SERVICE_NAME_WEB}-auto`, // getting-started-js-web-auto
    [SemanticResourceAttributes.SERVICE_INSTANCE_ID]: `${process.env.OTEL_SERVICE_NAME_WEB}-auto-instance`, // getting-started-js-web-auto-instance
    [SemanticResourceAttributes.SERVICE_VERSION]: "1.0.0",
  }),
});
const exporter = new OTLPTraceExporter({
  url: `${process.env.OTEL_EXPORTER_OTLP_ENDPOINT}/v1/traces`,
  headers: {
    "api-key": `${process.env.NEW_RELIC_LICENSE_INGEST_KEY}`,
  },
});
const processor = new SimpleSpanProcessor(exporter);
provider.addSpanProcessor(processor);
provider.register({
  contextManager: new ZoneContextManager(),
  // propagator: new B3Propagator(),
});

registerInstrumentations({
  instrumentations: [
    getWebAutoInstrumentations({
      // load custom configuration if needed
      // for example  xml-http-request instrumentation
      // "@opentelemetry/instrumentation-xml-http-request": {
      //   clearTimingResources: true,
      // },
    }),
  ],
});
