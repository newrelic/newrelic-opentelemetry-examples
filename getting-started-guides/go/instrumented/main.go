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

	// Serve
	http.Handle("/", http.HandlerFunc(handler))
	http.ListenAndServe(":5000", nil)
}
