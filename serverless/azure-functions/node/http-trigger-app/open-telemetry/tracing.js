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

// Adjust logging lovel for OTel logging. DiagLogLevel.DEBUG, etc.
diag.setLogger(new DiagConsoleLogger(), DiagLogLevel.INFO);

// Azure Functions debugging tooling does not populate this ENV var.
const functionAppName = process.env.WEBSITE_SITE_NAME || 'http-trigger-app';

const provider = new NodeTracerProvider({
  resource: new Resource({
    [SemanticResourceAttributes.SERVICE_NAME]: functionAppName,
    [SemanticResourceAttributes.SERVICE_INSTANCE_ID]: process.env.WEBSITE_INSTANCE_ID || uuid.v4(),
    [SemanticResourceAttributes.HOST_NAME]: process.env.WEBSITE_HOSTNAME.split(':')[0],
    [SemanticResourceAttributes.CLOUD_PROVIDER]: CloudProviderValues.AZURE
  }),
  generalLimits: {
    // Stay within NR limits to avoid dropping server-side. https://docs.newrelic.com/docs/data-apis/manage-data/view-system-limits/#all_products
    // ENV var configuration also valid: OTEL_ATTRIBUTE_VALUE_LENGTH_LIMIT=4094.
    attributeValueLengthLimit: 4094
  }
});

// It is also valid to set ENV vars for configuration here instead of manually setting.
// OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317
// OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_license_key_here>
const metadata = new grpc.Metadata();
metadata.set('api-key', process.env.API_KEY);

const otlpExporter = new OTLPTraceExporter({
  url: 'https://otlp.nr-data.net:4317',
  credentials: grpc.credentials.createSsl(),
  metadata
});

const otlpBatchProcessor = new BatchSpanProcessor(otlpExporter, {
  bufferTimeout: 5000,
  bufferSize: 500,
});

provider.addSpanProcessor(otlpBatchProcessor);

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

function instrumentHttpFunction(originalFunction) {
  const tracer = opentelemetry.trace.getTracer('azure-http-function-tracer');

  return wrappedHttpFunction;

  async function wrappedHttpFunction(context, request) {
    const { executionContext } = context;

    // The trace context headers are also available in the `traceContext` property on the `context` object.
    // Since for other trigger types this can be populated from Azure instrumentation with sampled set off
    // (Trace flags set to 00), this example uses the web headers directly.
    const headers = request.headers;
    const incomingContext = opentelemetry.propagation.extract(opentelemetry.ROOT_CONTEXT, headers);

    // Name should be Azure Function Application name and Function name.
    const appAndFunctionName = `${functionAppName}/${executionContext.functionName}`;

    // NOTE: There is a lot more HTTP information that can be added than plumbed in this example for
    // request and response attributes defined in the http semantic conventions. FaaS conventions are currently
    // experimental and subject to change. For FaaS and Http Semantic contentions, see the following specifications:
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/resource/semantic_conventions/faas.md
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/faas.md
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md
    const span = tracer.startSpan(
      executionContext.functionName,
      {
        kind: opentelemetry.SpanKind.SERVER,
        attributes: {
          // FAAS attributes are not currently needed/required by New Relic but may
          // drive experiences in the future and are part of the semantic conventions.
          [SemanticResourceAttributes.FAAS_NAME]: appAndFunctionName,
          [SemanticAttributes.FAAS_TRIGGER]: FaasTriggerValues.HTTP,
          [SemanticAttributes.FAAS_EXECUTION]: executionContext.invocationId,
          [SemanticAttributes.FAAS_COLDSTART]: _isColdStart(),
          [SemanticAttributes.HTTP_SCHEME]: 'http',
          [SemanticAttributes.HTTP_METHOD]: request.method
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

      // Re-throwing original error.
      throw error;
    } finally {
      await _endInvocation(span, _error, context.res.status);
    }
  }
}

async function _endInvocation(span, error, status) {
  if (error) {
    span.recordException(error);

    // Defaulting to 500 when the function throws.
    span.setAttributes({
      [SemanticAttributes.HTTP_STATUS_CODE]: 500
    });

    span.setStatus({
      code: opentelemetry.SpanStatusCode.ERROR,
      message: error.message
    });
  } else {
    const statusCode = status && parseInt(status); // Should have some error safety here.

    span.setAttributes({
      [SemanticAttributes.HTTP_STATUS_CODE]: statusCode
    });

    // Your threshold considerations may vary.
    if (status >= 400) {
      span.setStatus({
        code: opentelemetry.SpanStatusCode.ERROR,
        message: `Status Code Error: ${statusCode}`
      });
    }
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

module.exports = {
  instrumentHttpFunction
};
