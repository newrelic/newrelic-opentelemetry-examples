'use strict';

// this will be needed to get a tracer
import opentelemetry from '@opentelemetry/api';
// tracer provider for web
import { WebTracerProvider } from '@opentelemetry/web';
// and an exporter with span processor
import {
  ConsoleSpanExporter,
  SimpleSpanProcessor,
} from '@opentelemetry/tracing';
import { CollectorTraceExporter } from '@opentelemetry/exporter-collector';

// Create a provider for activating and tracking spans
const tracerProvider = new WebTracerProvider();

// Configure a span processor and exporter for the tracer
tracerProvider.addSpanProcessor(
  new SimpleSpanProcessor(new ConsoleSpanExporter())
);
tracerProvider.addSpanProcessor(new SimpleSpanProcessor(new CollectorTraceExporter({
  url: "http://localhost:4318/v1/trace"
})));

// Register the tracer
tracerProvider.register();
const tracer = opentelemetry.trace.getTracer('web-app');

const span = tracer.startSpan('Bleep');
span.setAttribute('customAttribute', 'customValue');
span.addEvent('My span event');

const childSpan = tracer.startSpan('Bloop', {
  parent: span,
});
childSpan.end();

span.end();
