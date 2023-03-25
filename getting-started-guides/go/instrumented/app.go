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
	Message string  `json:"message"`
	Input   *uint64 `json:"input"`
	Output  *uint64 `json:"output"`
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
	uint64,
	error,
) {
	num, err := strconv.ParseUint(r.URL.Query().Get("num"), 10, 64)
	if err != nil {
		log.Print(err.Error())
	}
	return num, err
}

// Calculate Fibonacci per given input number
func calculateFibonacci(
	r *http.Request,
	n uint64,
) (
	uint64,
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

	// Check input
	if n <= 1 || n > 90 {
		log.Print(INPUT_IS_OUTSIDE_OF_RANGE)

		// Set error span attributes
		fibonacciSpanAttrs := []attribute.KeyValue{
			semconv.OtelStatusCodeError,
			semconv.OtelStatusDescriptionKey.String(INPUT_IS_OUTSIDE_OF_RANGE),
		}
		span.SetAttributes(fibonacciSpanAttrs...)

		return 0, errors.New("invalid input")
	}

	// Calculate Fibonacci
	var n2, n1 uint64 = 0, 1
	for i := uint64(2); i < n; i++ {
		n2, n1 = n1, n1+n2
	}

	return n2 + n1, nil
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
