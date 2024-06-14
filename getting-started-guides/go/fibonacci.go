package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
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
	name = "fibonacci-calculator"
)

var (
	tracer               = otel.Tracer(name)
	meter                = otel.Meter(name)
	logger               = otelslog.NewLogger(name)
	fibonacciInvocations metric.Int64Counter
)

type fibonacciResponse struct {
	N       int    `json:"n,omitempty"`
	Result  int    `json:"result,omitempty"`
	Message string `json:"message,omitempty"`
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
	n, err := parseNum(r)
	if err != nil {
		createHttpResponse(w, http.StatusBadRequest, fibonacciResponse{Message: err.Error()})
		return
	}

	result, err := calculateFibonacci(r.Context(), n)
	if err != nil {
		createHttpResponse(w, http.StatusBadRequest, fibonacciResponse{Message: err.Error()})
		return
	}

	createHttpResponse(w, http.StatusOK, fibonacciResponse{N: n, Result: result})
}

func parseNum(r *http.Request) (int, error) {
	n, err := strconv.ParseInt(r.URL.Query().Get("n"), 10, 32)
	if err != nil {
		logger.Error(err.Error())
	}
	return int(n), err
}

func calculateFibonacci(ctx context.Context, n int) (int, error) {
	ctx, span := tracer.Start(ctx, "fibonacci")
	defer span.End()

	span.SetAttributes(attribute.Int("fibonacci.n", n))

	if n < 1 || n > 90 {
		err := errors.New("n must be between 1 and 90")
		span.SetStatus(codes.Error, err.Error())
		span.RecordError(err, trace.WithStackTrace(true))
		fibonacciInvocations.Add(ctx, 1, metric.WithAttributes(attribute.Bool("fibonacci.valid.n", false)))
		msg := fmt.Sprintf("Failed to compute fib(%d).", n)
		logger.InfoContext(ctx, msg, "fibonacci.n", n)
		return 0, err
	}

	var result = 1
	if n > 2 {
		var a = 0
		var b = 1

		for i := 1; i < n; i++ {
			result = a + b
			a = b
			b = result
		}
	}

	span.SetAttributes(attribute.Int("fibonacci.result", result))
	fibonacciInvocations.Add(ctx, 1, metric.WithAttributes(attribute.Bool("fibonacci.valid.n", true)))
	msg := fmt.Sprintf("Computed fib(%d) = %d.", n, result)
	logger.InfoContext(ctx, msg, "fibonacci.n", n, "fibonacci.result", result)
	return result, nil
}

func createHttpResponse(w http.ResponseWriter, statusCode int, res fibonacciResponse) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)
	json.NewEncoder(w).Encode(res)
}
