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
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/metric"
	"go.opentelemetry.io/otel/trace"
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

	span.SetAttributes(attribute.Int64("fibonacci.n", n))

	if n < 1 || n > 90 {
		err := errors.New("n must be between 1 and 90")
		span.SetStatus(codes.Error, err.Error())
		span.RecordError(err, trace.WithStackTrace(true))
		fibonacciInvocations.Add(ctx, 1, metric.WithAttributes(attribute.Bool("fibonacci.valid.n", false)))
		msg := fmt.Sprintf("Failed to compute fib(%d).", n)
		logger.InfoContext(ctx, msg, "fibonacci.n", n)
		return 0, err
	}

	var result = int64(1)
	if n > 2 {
		var a = int64(0)
		var b = int64(1)

		for i := int64(1); i < n; i++ {
			result = a + b
			a = b
			b = result
		}
	}

	span.SetAttributes(attribute.Int64("fibonacci.result", result))
	fibonacciInvocations.Add(ctx, 1, metric.WithAttributes(attribute.Bool("fibonacci.valid.n", true)))
	msg := fmt.Sprintf("Computed fib(%d) = %d.", n, result)
	logger.InfoContext(ctx, msg, "fibonacci.n", n, "fibonacci.result", result)
	return result, nil
}

func createHttpResponse(w http.ResponseWriter, statusCode int, res *responseObject) {
	w.WriteHeader(statusCode)
	payload, _ := json.Marshal(res)
	w.Write(payload)
}
