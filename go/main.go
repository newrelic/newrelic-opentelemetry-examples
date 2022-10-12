package main

import (
	"context"
	"errors"
	"log"
	"math/rand"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/metric/global"
	"go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/metric/metricdata"
	"go.opentelemetry.io/otel/sdk/metric/view"
	"go.opentelemetry.io/otel/sdk/resource"
	tracesdk "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/trace"

	semconv "go.opentelemetry.io/otel/semconv/v1.12.0"
)

func NewRelicTemporalitySelector(kind view.InstrumentKind) metricdata.Temporality {
	if kind == view.SyncUpDownCounter || kind == view.AsyncUpDownCounter {
		return metricdata.CumulativeTemporality
	}
	return metricdata.DeltaTemporality
}

func main() {
	ctx := context.Background()

	res := resource.NewWithAttributes(
		semconv.SchemaURL,
		semconv.ServiceNameKey.String("OpenTelemetry-Go-Example"),
	)

	initTracerProvider(ctx, res)
	initMeterProvider(ctx, res)

	tracer := otel.GetTracerProvider().Tracer("my-tracer")
	meter := global.Meter("my-meter")

	responseTimeInstrument, err := meter.SyncInt64().Histogram("http.server.duration")
	if err != nil {
		log.Panicf("failed to initialize instrument: %v", err)
	}

	log.Println("Generating spans and metrics from a service named OpenTelemetry-Go-Example... look for them in New Relic!")
	for {
		_, span := tracer.Start(ctx, "/Get/Users", trace.WithSpanKind(trace.SpanKindServer))

		duration := rand.Intn(1000)
		responseCode, err := performRequest(duration)

		if err != nil {
			span.SetStatus(codes.Error, err.Error())
			span.RecordError(err)
		}

		attributes := []attribute.KeyValue{
			semconv.HTTPMethodKey.String("GET"),
			semconv.HTTPSchemeHTTPS,
			semconv.HTTPStatusCodeKey.Int(responseCode),
			semconv.NetHostNameKey.String("localhost"),
		}

		span.SetAttributes(attributes...)

		responseTimeInstrument.Record(ctx, int64(duration), attributes...)

		span.End()
	}
}

func performRequest(duration int) (int, error) {
	time.Sleep(time.Duration(duration) * time.Millisecond)
	if rand.Intn(100) < 5 {
		return 500, errors.New("request failed")
	}
	return 200, nil
}

func initTracerProvider(ctx context.Context, res *resource.Resource) {
	exporter, err := otlptracegrpc.New(ctx)
	if err != nil {
		log.Fatalf("%s: %v", "failed to create metric exporter", err)
	}

	tracerProvider := tracesdk.NewTracerProvider(
		tracesdk.WithBatcher(exporter),
		tracesdk.WithResource(res),
	)
	otel.SetTracerProvider(tracerProvider)
}

func initMeterProvider(ctx context.Context, res *resource.Resource) {
	exporter, err := otlpmetricgrpc.New(ctx)
	if err != nil {
		log.Fatalf("%s: %v", "failed to create metric exporter", err)
	}

	reader := metric.NewPeriodicReader(
		exporter,
		metric.WithTemporalitySelector(NewRelicTemporalitySelector),
		metric.WithInterval(2*time.Second),
	)

	meterProvider := metric.NewMeterProvider(
		metric.WithResource(res),
		metric.WithReader(reader),
	)
	global.SetMeterProvider(meterProvider)
}
