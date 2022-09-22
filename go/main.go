package main

import (
	"context"

	"go.opentelemetry.io/otel/sdk/resource"

	semconv "go.opentelemetry.io/otel/semconv/v1.12.0"
)

func main() {
	ctx := context.Background()

	res := resource.NewWithAttributes(
		semconv.SchemaURL,
		semconv.ServiceNameKey.String("OpenTelemetry-Go-Example"),
	)

	initTrace(ctx, res)
	initMeter(ctx, res)
	Run(ctx)
}
