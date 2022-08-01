package main

import (
	"context"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/sdk/resource"
	"go.opentelemetry.io/otel/sdk/trace"
	"log"
)

func initTrace(ctx context.Context, res *resource.Resource) {
	exporter, err := otlptrace.New(
		ctx,
		otlptracegrpc.NewClient(),
	)
	if err != nil {
		log.Fatalf("%s: %v", "failed to create metric exporter", err)
	}

	tp := trace.NewTracerProvider(
		trace.WithBatcher(exporter),
		trace.WithResource(res),
	)
	otel.SetTracerProvider(tp)
}
