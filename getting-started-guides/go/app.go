package main

import (
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"strconv"

	"go.opentelemetry.io/otel/attribute"
	semconv "go.opentelemetry.io/otel/semconv/v1.17.0"
	"go.opentelemetry.io/otel/trace"
)

const (
	INPUT_COULD_NOT_BE_PARSED = "Input could not be parsed."
	INPUT_IS_OUTSIDE_OF_RANGE = "Input is outside of the range [1,90]."
	CALCULATION_SUCCEEDED     = "Fibonacci is calculated successfully."
)

type responseObject struct {
	Message string `json:"message"`
	Input   *int64 `json:"input"`
	Output  *int64 `json:"output"`
}

// HTTP handler for Fibonacci calculation
func handler(
	w http.ResponseWriter,
	r *http.Request,
) {

	// Parse input number
	num, err := parseNum(r)
	if err != nil {
		createHttpResponse(
			w,
			http.StatusBadRequest,
			&responseObject{
				Message: INPUT_COULD_NOT_BE_PARSED,
				Input:   &num,
				Output:  nil,
			})
		return
	}

	// Calculate Fibonacci
	out, err := calculateFibonacci(r, num)
	if err != nil {
		createHttpResponse(
			w,
			http.StatusBadRequest,
			&responseObject{
				Message: INPUT_IS_OUTSIDE_OF_RANGE,
				Input:   &num,
				Output:  nil,
			})
		return
	}

	createHttpResponse(
		w,
		http.StatusOK,
		&responseObject{
			Message: CALCULATION_SUCCEEDED,
			Input:   &num,
			Output:  &out,
		})
}

func parseNum(
	r *http.Request,
) (
	int64,
	error,
) {
	num, err := strconv.ParseInt(r.URL.Query().Get("n"), 10, 64)
	if err != nil {
		log.Print(err.Error())
	}
	return num, err
}

// Calculate Fibonacci per given input number
func calculateFibonacci(
	r *http.Request,
	n int64,
) (
	int64,
	error,
) {

	// Start an internal child span for Fibonacci calculation
	_, span := trace.SpanFromContext(r.Context()).
		TracerProvider().
		Tracer("Fibonacci").
		Start(
			r.Context(),
			"fibonacci",
			trace.WithSpanKind(trace.SpanKindInternal),
		)
	defer span.End()

	fibonacciSpanAttrs := []attribute.KeyValue{
		attribute.Int64("fibonacci.n", n),
	}

	// Check input
	if n <= 1 || n > 90 {
		log.Print(INPUT_IS_OUTSIDE_OF_RANGE)

		// Set error span attributes
		fibonacciSpanAttrs = append(fibonacciSpanAttrs,
			semconv.OtelStatusCodeError,
			semconv.OtelStatusDescriptionKey.String(INPUT_IS_OUTSIDE_OF_RANGE),
		)
		span.SetAttributes(fibonacciSpanAttrs...)

		return 0, errors.New("invalid input")
	}

	// Calculate Fibonacci
	var n2, n1 int64 = 0, 1
	for i := int64(2); i < n; i++ {
		n2, n1 = n1, n1+n2
	}
	res := n2 + n1

	// Set calculation result into span
	fibonacciSpanAttrs = append(fibonacciSpanAttrs,
		attribute.Int64("fibonacci.result", res),
	)
	span.SetAttributes(fibonacciSpanAttrs...)

	return res, nil
}

func createHttpResponse(
	w http.ResponseWriter,
	statusCode int,
	res *responseObject,
) {
	w.WriteHeader(statusCode)
	payload, _ := json.Marshal(res)
	w.Write(payload)
}
