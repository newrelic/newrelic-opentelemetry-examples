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
	http.Handle("/fibonacci", NewHttpWrapper(http.HandlerFunc(handler), "fibonacci"))
	http.ListenAndServe(":8080", nil)
}
