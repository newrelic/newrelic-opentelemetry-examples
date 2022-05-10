package main

import (
	"context"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"log"
)

func getLargeValue() string {
	result := ""
	for i := 0; i < 4095; i++ {
		result += "A"
	}
	result += "BBBBBBBBBBBBBBB"
	return result
}

var largeValue = attribute.StringValue(getLargeValue())

func Run(ctx context.Context) error {
	for {
		_, span := otel.Tracer("OpenTelemetry-Go-Example").Start(ctx, "Run")
		// New Relic only accepts attributes values that are less than 4096 characters.
		// When viewing this span in New Relic, the value of the "truncate" attribute will contain no Bs
		span.SetAttributes(attribute.KeyValue{Key: "truncate", Value: largeValue})
		log.Println("Metric recording started")
		generateMetrics()
		log.Println("Metric recording complete")
		span.End()
	}
}
