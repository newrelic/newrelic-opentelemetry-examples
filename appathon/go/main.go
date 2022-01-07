package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"strconv"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"

	"github.com/google/uuid"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.7.0"
	"go.opentelemetry.io/otel/trace"
)

type fibonacciResponse struct {
	N      int   `json:"n"`
	Result int64 `json:"result"`
}

type errorResponse struct {
	Message string `json:"message"`
}

var tracer trace.Tracer

func initTracer() *sdktrace.TracerProvider {

	client := otlptracegrpc.NewClient()
	exporter, err := otlptrace.New(context.Background(), client)
	if err != nil {
		log.Fatalf("creating OTLP trace exporter: %v", err)
	}

	tp := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(exporter),
		sdktrace.WithResource(resource.NewWithAttributes(semconv.SchemaURL, semconv.ServiceNameKey.String("appathon-go"), semconv.ServiceInstanceIDKey.String(uuid.NewString()), semconv.TelemetrySDKNameKey.String("opentelemetry"), semconv.TelemetrySDKLanguageKey.String("go"), semconv.TelemetrySDKVersionKey.String(otel.Version()))),
	)
	otel.SetTracerProvider(tp)
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(propagation.TraceContext{}, propagation.Baggage{}))

	tracer = tp.Tracer("appathon-go")

	return tp
}

func sendErrorResponse(errorMessage string, w http.ResponseWriter) {
	encodedResponse, _ := json.Marshal(&errorResponse{Message: errorMessage})
	w.WriteHeader(http.StatusBadRequest)
	w.Header().Set("Content-Type", "application/json")
	w.Write(encodedResponse)
}

func fibonacci(n int, ctx context.Context) (int64, error) {
	_, span := tracer.Start(ctx, "fibonacci", trace.WithSpanKind(trace.SpanKindInternal), trace.WithAttributes(attribute.Int("oteldemo.n", n)))
	defer span.End()

	if n < 1 || n > 90 {
		err := fmt.Errorf("%d must be >= 1 and <= 90", n)
		span.SetStatus(codes.Error, err.Error())
		span.RecordError(err)
		return 0, err
	}

	var n2, n1 int64 = 0, 1
	for i := int(2); i < n; i++ {
		n2, n1 = n1, n1+n2
	}

	result := n2 + n1
	span.SetAttributes(attribute.Int64("oteldemo.result", result))

	return result, nil
}

func main() {
	tp := initTracer()
	defer func() {
		if err := tp.Shutdown(context.Background()); err != nil {
			log.Printf("Error shutting down tracer provider: %v", err)
		}
	}()

	fibonacciHandler := func(w http.ResponseWriter, req *http.Request) {
		ctx := req.Context()
		rawN := req.URL.Query().Get("n")
		if len(rawN) <= 0 {
			sendErrorResponse("parameter 'n' not specified", w)
			return
		}

		n, err := strconv.Atoi(rawN)
		if err != nil {
			sendErrorResponse(err.Error(), w)
			return
		}

		result, err := fibonacci(n, ctx)
		if err != nil {
			sendErrorResponse(err.Error(), w)
			return
		}

		encodedResponse, _ := json.Marshal(&fibonacciResponse{N: n, Result: result})
		w.WriteHeader(http.StatusOK)
		w.Header().Set("Content-Type", "application/json")
		w.Write(encodedResponse)
	}

	otelHandler := otelhttp.NewHandler(http.HandlerFunc(fibonacciHandler), "fib")

	http.Handle("/fibonacci", otelHandler)
	err := http.ListenAndServe(":8080", nil)
	if err != nil {
		panic(err)
	}
}
