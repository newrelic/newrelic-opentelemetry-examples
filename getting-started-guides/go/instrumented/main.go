package main

import (
	"context"
	"net/http"
)

func main() {
	// Get context
	ctx := context.Background()

	// Create metric provider
	mp := newMetricProvider(ctx)
	defer shutdownMetricProvider(ctx, mp)

	// Create tracer provider
	tp := newTraceProvider(ctx)
	defer shutdownTraceProvider(ctx, tp)

	// Serve
	http.Handle("/", NewHttpWrapper(http.HandlerFunc(handler), "fibonacci"))
	http.ListenAndServe(":5000", nil)
}
