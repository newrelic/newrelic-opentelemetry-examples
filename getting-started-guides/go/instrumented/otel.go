package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/metric"

	"go.opentelemetry.io/otel/propagation"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/metric/metricdata"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.19.0"
	"go.opentelemetry.io/otel/semconv/v1.19.0/httpconv"
	"go.opentelemetry.io/otel/trace"
)

func newRelicTemporalitySelector(kind sdkmetric.InstrumentKind) metricdata.Temporality {
	if kind == sdkmetric.InstrumentKindUpDownCounter || kind == sdkmetric.InstrumentKindObservableUpDownCounter {
		return metricdata.CumulativeTemporality
	}
	return metricdata.DeltaTemporality
}

func newMetricProvider(
	ctx context.Context,
) *sdkmetric.MeterProvider {
	var exp sdkmetric.Exporter
	var err error

	exp, err = otlpmetricgrpc.New(
		ctx,
		otlpmetricgrpc.WithTemporalitySelector(newRelicTemporalitySelector),
	)
	if err != nil {
		panic(err)
	}

	mp := sdkmetric.NewMeterProvider(
		sdkmetric.WithReader(
			sdkmetric.NewPeriodicReader(
				exp,
				sdkmetric.WithInterval(2*time.Second),
			)))
	otel.SetMeterProvider(mp)
	return mp
}

func shutdownMetricProvider(
	ctx context.Context,
	mp *sdkmetric.MeterProvider,
) {
	// Do not make the application hang when it is shutdown.
	ctx, cancel := context.WithTimeout(ctx, time.Second*5)
	defer cancel()
	if err := mp.Shutdown(ctx); err != nil {
		panic(err)
	}
}

func newTraceProvider(
	ctx context.Context,
) *sdktrace.TracerProvider {

	var exp sdktrace.SpanExporter
	var err error

	exp, err = otlptracegrpc.New(ctx)
	if err != nil {
		panic(err)
	}

	// Instantiate a default resource with environment variables
	r := resource.Default()

	// Create trace provider
	tp := sdktrace.NewTracerProvider(
		sdktrace.WithSampler(sdktrace.AlwaysSample()),
		sdktrace.WithBatcher(exp),
		sdktrace.WithResource(r),
	)

	// Set global trace provider
	otel.SetTracerProvider(tp)

	// Set trace propagator
	otel.SetTextMapPropagator(
		propagation.NewCompositeTextMapPropagator(
			propagation.TraceContext{},
			propagation.Baggage{},
		))

	return tp
}

func shutdownTraceProvider(
	ctx context.Context,
	tp *sdktrace.TracerProvider,
) {
	// Do not make the application hang when it is shutdown.
	ctx, cancel := context.WithTimeout(ctx, time.Second*5)
	defer cancel()
	if err := tp.Shutdown(ctx); err != nil {
		panic(err)
	}
}

type HttpWrapper struct {
	operation            string
	serverName           string
	handler              http.Handler
	httpServerDuration   metric.Float64Histogram
	fibonacciInvocations metric.Int64Counter
}

func NewHttpWrapper(
	handler http.Handler,
	operation string,
) http.Handler {

	// Get service name from environment variables
	serverName := os.Getenv("OTEL_SERVICE_NAME")

	// Create HTTP server duration histogram
	httpServerDuration, err := otel.GetMeterProvider().
		Meter(serverName).
		Float64Histogram("http.server.duration")
	if err != nil {
		log.Print(err.Error())
		panic(err.Error())
	}

	// Create Fibonacci invocation counter
	fibonacciInvocations, err := otel.GetMeterProvider().
		Meter(serverName).
		Int64Counter("fibonacci.invocations")
	if err != nil {
		log.Print(err.Error())
		panic(err.Error())
	}

	// Initialize custom HTTP handler wrapper
	w := HttpWrapper{
		serverName:           serverName,
		handler:              handler,
		operation:            operation,
		httpServerDuration:   httpServerDuration,
		fibonacciInvocations: fibonacciInvocations,
	}

	return &w
}

// Implement custom ServeHTTP to wrap & track spans & metrics of the request
func (h *HttpWrapper) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	requestStartTime := time.Now()

	// Get context from request
	ctx := r.Context()

	// Set up trace attributes
	startSpanAttributes := []trace.SpanStartOption{
		trace.WithSpanKind(trace.SpanKindServer),
		trace.WithAttributes(httpconv.ServerRequest(h.serverName, r)...),
		trace.WithAttributes(semconv.NetHostName(h.serverName)),
	}

	// Start the server span
	ctx, span := otel.GetTracerProvider().
		Tracer("Fibonacci").
		Start(ctx, r.Method+" /fibonacci", startSpanAttributes...)
	defer span.End()

	// Create response writer wrapper
	rww := NewResponseWriterWrapper(w)
	h.handler.ServeHTTP(rww, r.WithContext(ctx))

	// Set up metric attributes
	httpServerMetricAttributes := httpconv.ServerRequest(h.serverName, r)
	fibonacciInvocationMetricAttributes := []attribute.KeyValue{}

	if rww.statusCode > 0 {
		// Add status code to metric attributes
		httpServerMetricAttributes = append(
			httpServerMetricAttributes,
			semconv.HTTPStatusCode(rww.statusCode),
		)
		if rww.statusCode == 200 {
			fibonacciInvocationMetricAttributes = append(
				fibonacciInvocationMetricAttributes,
				attribute.Bool("fibonacci.valid.n", true),
			)
		} else {
			fibonacciInvocationMetricAttributes = append(
				fibonacciInvocationMetricAttributes,
				attribute.Bool("fibonacci.valid.n", false),
			)
		}

		// Add status code to span attributes
		endSpanAttributes := []attribute.KeyValue{semconv.HTTPStatusCode(rww.statusCode)}
		span.SetAttributes(endSpanAttributes...)
	}

	// Use floating point division here for higher precision (instead of Millisecond method).
	elapsedTime := float64(time.Since(requestStartTime)) / float64(time.Millisecond)

	h.fibonacciInvocations.Add(ctx, 1, metric.WithAttributes(fibonacciInvocationMetricAttributes...))
	h.httpServerDuration.Record(ctx, elapsedTime, metric.WithAttributes(httpServerMetricAttributes...))
}

// Wrapper for response writer in order to retrieve the status code of the HTTP call
type responseWriterWrapper struct {
	http.ResponseWriter
	statusCode int
}

// Initialize HTTP response writer wrapper
func NewResponseWriterWrapper(w http.ResponseWriter) *responseWriterWrapper {
	return &responseWriterWrapper{w, http.StatusOK}
}

// Wrapper method to intercept & store the HTTP status code
func (rww *responseWriterWrapper) WriteHeader(code int) {
	rww.statusCode = code
	rww.ResponseWriter.WriteHeader(code)
}
