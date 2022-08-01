package main

import (
	"context"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/sdk/resource"
	"log"
)

func main() {
	ctx := context.Background()

	res, err := resource.New(ctx,
		resource.WithAttributes(
			attribute.String("service.name", "OpenTelemetry-Go-Example"),
		),
	)
	if err != nil {
		log.Fatalf("%s: %v", "failed to create resource", err)
	}

	initTrace(ctx, res)
	initMeter(ctx, res)
	Run(ctx)
}
