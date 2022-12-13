const opentelemetry = require('@opentelemetry/api');
const { diag, DiagConsoleLogger, DiagLogLevel } = opentelemetry;
const { NodeTracerProvider } = require("@opentelemetry/sdk-trace-node");
const { ConsoleSpanExporter, BatchSpanProcessor } = require("@opentelemetry/sdk-trace-base");
const { Resource } = require('@opentelemetry/resources');
const {
  SemanticAttributes,
  SemanticResourceAttributes,
  FaasTriggerValues,
  CloudProviderValues
} = require('@opentelemetry/semantic-conventions');
const { registerInstrumentations } = require('@opentelemetry/instrumentation');
const { OTLPTraceExporter } =  require('@opentelemetry/exporter-trace-otlp-grpc');
const { HttpInstrumentation } = require('@opentelemetry/instrumentation-http');
const grpc = require('@grpc/grpc-js');
const uuid = require('uuid');

const DEFAULT_BUFFER_TIMEOUT_MS = 5000;
const DEFAULT_BUFFER_SIZE = 500;

// Adjust logging lovel for OTel logging. DiagLogLevel.DEBUG, etc.
diag.setLogger(new DiagConsoleLogger(), DiagLogLevel.INFO);

const provider = new NodeTracerProvider({
  resource: new Resource({
    [SemanticResourceAttributes.SERVICE_NAME]: process.env.SERVICE_NAME || process.env.WEBSITE_SITE_NAME,
    [SemanticResourceAttributes.SERVICE_INSTANCE_ID]: process.env.WEBSITE_INSTANCE_ID || uuid.v4(),
    [SemanticResourceAttributes.HOST_NAME]: process.env.WEBSITE_HOSTNAME.split(':')[0],
    [SemanticResourceAttributes.CLOUD_PROVIDER]: CloudProviderValues.AZURE
  }),
});

const metadata = new grpc.Metadata();
metadata.set('api-key', process.env.API_KEY);

const otlpExporter = new OTLPTraceExporter({
  url: process.env.EXPORT_URL || 'https://otlp.nr-data.net:4317',
  headers: `api-key="${process.env.API_KEY}"`,
  credentials: grpc.credentials.createSsl(),
  metadata
});

const otlpBatchProcessor = new BatchSpanProcessor(otlpExporter, {
  bufferTimeout: process.env.EXPORT_BUFFER_TIMEOUT || DEFAULT_BUFFER_TIMEOUT_MS,
  bufferSize: process.env.EXPORT_BUFFER_SIZE || DEFAULT_BUFFER_SIZE
});

provider.addSpanProcessor(otlpBatchProcessor);

if (_shouldExportToConsole()) {
  const consoleBatchProcessor = new BatchSpanProcessor(new ConsoleSpanExporter(), {
    bufferTimeout: process.env.EXPORT_BUFFER_TIMEOUT || DEFAULT_BUFFER_TIMEOUT_MS,
    bufferSize: process.env.EXPORT_BUFFER_SIZE || DEFAULT_BUFFER_SIZE
  });

  provider.addSpanProcessor(consoleBatchProcessor);
}

provider.register();

registerInstrumentations({
  // List out individual pieces of instrumentation. Http will be the most ubuititous.
  // The Azure SDK does not yet have auto-instrumentation but once exists would make sense to add here.
  instrumentations: [new HttpInstrumentation()]

  // OR add all auto-instrumentation. This can get noisy with instrumentation on fs (file system) and other modules.
  // Require at top of file: // const { getNodeAutoInstrumentations } = require("@opentelemetry/auto-instrumentations-node");
  //instrumentations: [getNodeAutoInstrumentations()],
});

let coldStart = true;

function instrumentQueueFunction(originalFunction) {
  const tracer = opentelemetry.trace.getTracer('azure-queue-function-tracer');

  return wrappedQueueFunction;

  async function wrappedQueueFunction(context, serviceBusMessage) {
    const { executionContext, bindingData } = context;

    // Azure Functions are not currently auto-grabbing into context object for Node, even if setting diagnostic ID
    const { applicationProperties } = bindingData;
    const incomingContext = opentelemetry.propagation.extract(opentelemetry.ROOT_CONTEXT, applicationProperties);

    // Name should be Azure Function Application name and Function name.
    const appAndFunctionName = `${process.env.WEBSITE_SITE_NAME}/${executionContext.functionName}`;

    // NOTE: example does not currently add Pub/Sub specific semantic conventions.
    const span = tracer.startSpan(
      executionContext.functionName,
      {
        kind: opentelemetry.SpanKind.SERVER,
        attributes: {
          // FAAS attributes are not currently needed/required by New Relic but may
          // drive experiences in the future and are part of the semantic conventions.
          [SemanticResourceAttributes.FAAS_NAME]: appAndFunctionName,
          [SemanticAttributes.FAAS_TRIGGER]: FaasTriggerValues.PUBSUB,
          [SemanticAttributes.FAAS_EXECUTION]: executionContext.invocationId,
          [SemanticAttributes.FAAS_COLDSTART]: _isColdStart(),
        }
      },
      incomingContext
    );

    let _error = null;
    try {
      const spanContext = opentelemetry.trace.setSpan(opentelemetry.context.active(), span);
      await opentelemetry.context.with(spanContext, async () => {
        return originalFunction.apply(this, arguments);
      });
    } catch (error) {
      _error = error;

      // Re-throwing original error. This is not us.
      // May be able to catch in less-invasive ways.
      throw error;
    } finally {
      await _endInvocation(span, _error);
    }
  }
}

async function _endInvocation(span, error) {
  if (error) {
    span.recordException(error);

    span.setStatus({
      code: opentelemetry.SpanStatusCode.ERROR,
      message: error.message
    });
  }

  span.end();

  await _flush();
}

async function _flush() {
  try {
    return provider.forceFlush();
  } catch (error) {
    console.error(error); // Better logging here.
  }
}

function _isColdStart() {
  if (coldStart) {
    coldStart = false;
    return true;
  }

  return coldStart;
}

function _shouldExportToConsole() {
  const shouldExport = process.env.EXPORT_CONSOLE && process.env.EXPORT_CONSOLE.toLowerCase();
  return shouldExport === "true" || shouldExport === "1";
}

module.exports = {
  instrumentQueueFunction
};
