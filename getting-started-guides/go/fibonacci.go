package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"strconv"

	"go.opentelemetry.io/contrib/bridges/otelslog"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/metric"
	semconv "go.opentelemetry.io/otel/semconv/v1.17.0"
)

const (
	name                      = "getting-started-go"
	INPUT_COULD_NOT_BE_PARSED = "Input could not be parsed."
	INPUT_IS_OUTSIDE_OF_RANGE = "Input is outside of the range [1,90]."
	CALCULATION_SUCCEEDED     = "Fibonacci is calculated successfully."
)

var (
	tracer               = otel.Tracer(name)
	meter                = otel.Meter(name)
	logger               = otelslog.NewLogger(name)
	fibonacciInvocations metric.Int64Counter
)

type responseObject struct {
	Message string `json:"message"`
	Input   *int64 `json:"input"`
	Output  *int64 `json:"output"`
}

func init() {
	var err error
	fibonacciInvocations, err = meter.Int64Counter(
		"fibonacci.invocations",
		metric.WithDescription("Measures the number of times the fibonacci method is invoked."),
	)
	if err != nil {
		panic(err)
	}
}

func fibonacci(w http.ResponseWriter, r *http.Request) {
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

func parseNum(r *http.Request) (int64, error) {
	num, err := strconv.ParseInt(r.URL.Query().Get("n"), 10, 64)
	if err != nil {
		log.Print(err.Error())
	}
	return num, err
}

func calculateFibonacci(r *http.Request, n int64) (int64, error) {
	ctx, span := tracer.Start(r.Context(), "fibonacci")
	defer span.End()

	fibonacciSpanAttrs := []attribute.KeyValue{
		attribute.Int64("fibonacci.n", n),
	}

	if n <= 1 || n > 90 {
		log.Print(INPUT_IS_OUTSIDE_OF_RANGE)

		// Set error span attributes
		fibonacciSpanAttrs = append(fibonacciSpanAttrs,
			semconv.OtelStatusCodeError,
			semconv.OtelStatusDescriptionKey.String(INPUT_IS_OUTSIDE_OF_RANGE),
		)
		span.SetAttributes(fibonacciSpanAttrs...)

		fibonacciInvocations.Add(ctx, 1, metric.WithAttributes(attribute.Bool("fibonacci.valid.n", false)))

		return 0, errors.New("invalid input")
	}

	var n2, n1 int64 = 0, 1
	for i := int64(2); i < n; i++ {
		n2, n1 = n1, n1+n2
	}
	res := n2 + n1

	fibonacciSpanAttrs = append(fibonacciSpanAttrs,
		attribute.Int64("fibonacci.result", res),
	)
	span.SetAttributes(fibonacciSpanAttrs...)

	msg := fmt.Sprintf("Computed fib({%d}) = {%d}.", n, res)
	logger.InfoContext(ctx, msg, "fibonacci.n", n, "fibonacci.result", res)
	fibonacciInvocations.Add(ctx, 1, metric.WithAttributes(attribute.Bool("fibonacci.valid.n", true)))

	return res, nil
}

func createHttpResponse(w http.ResponseWriter, statusCode int, res *responseObject) {
	w.WriteHeader(statusCode)
	payload, _ := json.Marshal(res)
	w.Write(payload)
}
