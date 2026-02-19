package main

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"math/rand"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"time"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/metric"
	"go.opentelemetry.io/otel/propagation"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.21.0"
	"go.opentelemetry.io/otel/trace"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

var (
	tracer         trace.Tracer
	meter          metric.Meter
	logger         *zap.Logger
	errorCounter   metric.Int64Counter
	requestCounter metric.Int64Counter

	// Configuration from environment variables
	autoGenerateEnabled bool
	requestInterval     time.Duration
)

type ErrorResponse struct {
	Error   string `json:"error"`
	Code    int    `json:"code"`
	Message string `json:"message"`
	TraceID string `json:"trace_id,omitempty"`
}

type SuccessResponse struct {
	Status  string `json:"status"`
	Message string `json:"message"`
	Data    any    `json:"data,omitempty"`
	TraceID string `json:"trace_id,omitempty"`
}

func init() {
	rand.Seed(time.Now().UnixNano())
	loadConfig()
}

func loadConfig() {
	// Auto-generate traffic (optional background traffic generator)
	autoGenerateEnabled = getEnvBool("AUTO_GENERATE_TRAFFIC", false)

	// Request interval for auto-generator
	if val := os.Getenv("REQUEST_INTERVAL_MS"); val != "" {
		if ms, err := strconv.Atoi(val); err == nil {
			requestInterval = time.Duration(ms) * time.Millisecond
		}
	} else {
		requestInterval = 5000 * time.Millisecond // Default 5 seconds
	}
}

func getEnvBool(key string, defaultVal bool) bool {
	if val := os.Getenv(key); val != "" {
		if b, err := strconv.ParseBool(val); err == nil {
			return b
		}
	}
	return defaultVal
}

func main() {
	if err := run(); err != nil {
		log.Fatalln(err)
	}
}

func run() (err error) {
	// Handle SIGINT (CTRL+C) gracefully
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
	defer stop()

	// Initialize OpenTelemetry
	otelShutdown, err := initOTel(ctx)
	if err != nil {
		return
	}
	defer func() {
		err = errors.Join(err, otelShutdown(context.Background()))
	}()

	// Start HTTP server
	srv := &http.Server{
		Addr:         ":8080",
		BaseContext:  func(_ net.Listener) context.Context { return ctx },
		ReadTimeout:  5 * time.Second,
		WriteTimeout: 10 * time.Second,
		Handler:      newHTTPHandler(),
	}

	// Start background traffic generator if enabled
	if autoGenerateEnabled {
		go startAutoGenerator(ctx)
	}

	srvErr := make(chan error, 1)
	go func() {
		logger.Info("HTTP server started", zap.String("addr", srv.Addr))
		srvErr <- srv.ListenAndServe()
	}()

	// Wait for interruption
	select {
	case err = <-srvErr:
		return
	case <-ctx.Done():
		stop()
	}

	err = srv.Shutdown(context.Background())
	return
}

func initOTel(ctx context.Context) (func(context.Context) error, error) {
	res, err := resource.New(ctx,
		resource.WithAttributes(
			semconv.ServiceName("error-generator"),
			semconv.ServiceVersion("1.0.0"),
		),
	)
	if err != nil {
		return nil, err
	}

	// Initialize trace provider
	traceExporter, err := otlptracegrpc.New(ctx,
		otlptracegrpc.WithEndpoint(getOTLPEndpoint()),
		otlptracegrpc.WithInsecure(),
	)
	if err != nil {
		return nil, err
	}

	tp := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(traceExporter),
		sdktrace.WithResource(res),
	)
	otel.SetTracerProvider(tp)
	otel.SetTextMapPropagator(propagation.TraceContext{})

	tracer = tp.Tracer("error-generator")

	// Initialize metric provider
	metricExporter, err := otlpmetricgrpc.New(ctx,
		otlpmetricgrpc.WithEndpoint(getOTLPEndpoint()),
		otlpmetricgrpc.WithInsecure(),
	)
	if err != nil {
		return nil, err
	}

	mp := sdkmetric.NewMeterProvider(
		sdkmetric.WithReader(sdkmetric.NewPeriodicReader(metricExporter, sdkmetric.WithInterval(10*time.Second))),
		sdkmetric.WithResource(res),
	)
	otel.SetMeterProvider(mp)

	meter = mp.Meter("error-generator")

	// Create metrics
	errorCounter, err = meter.Int64Counter(
		"errors.total",
		metric.WithDescription("Total number of errors by type"),
	)
	if err != nil {
		return nil, err
	}

	requestCounter, err = meter.Int64Counter(
		"requests.total",
		metric.WithDescription("Total number of requests"),
	)
	if err != nil {
		return nil, err
	}

	// Initialize logger
	config := zap.NewProductionConfig()
	config.EncoderConfig.TimeKey = "timestamp"
	config.EncoderConfig.MessageKey = "message"
	config.EncoderConfig.LevelKey = "severity"
	logger, err = config.Build()
	if err != nil {
		return nil, err
	}

	return func(ctx context.Context) error {
		err := errors.Join(
			tp.Shutdown(ctx),
			mp.Shutdown(ctx),
			logger.Sync(),
		)
		return err
	}, nil
}

func getOTLPEndpoint() string {
	if endpoint := os.Getenv("OTEL_EXPORTER_OTLP_ENDPOINT"); endpoint != "" {
		return endpoint
	}
	return "collector:4317"
}

func newHTTPHandler() http.Handler {
	mux := http.NewServeMux()

	// Register endpoint handlers with route tags
	handleFunc := func(pattern string, handler http.HandlerFunc) {
		instrumentedHandler := otelhttp.WithRouteTag(pattern, handler)
		mux.Handle(pattern, instrumentedHandler)
	}

	// Register all endpoints
	handleFunc("/", indexHandler)
	handleFunc("/health", healthHandler)
	handleFunc("/success", successHandler)
	handleFunc("/validation-error", validationErrorHandler)
	handleFunc("/auth-error", authErrorHandler)
	handleFunc("/server-error", serverErrorHandler)
	handleFunc("/timeout", timeoutHandler)
	handleFunc("/network-error", networkErrorHandler)
	handleFunc("/random", randomHandler)

	// Add HTTP instrumentation for the whole server
	handler := otelhttp.NewHandler(mux, "/")
	return handler
}

// indexHandler returns information about available endpoints
func indexHandler(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	span := trace.SpanFromContext(ctx)
	traceID := span.SpanContext().TraceID().String()

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]any{
		"service": "error-generator",
		"version": "1.0.0",
		"trace_id": traceID,
		"endpoints": map[string]string{
			"/":                 "This information",
			"/health":           "Health check endpoint",
			"/success":          "Always returns 200 OK",
			"/validation-error": "Returns 400 Bad Request",
			"/auth-error":       "Returns 401 Unauthorized",
			"/server-error":     "Returns 500 Internal Server Error",
			"/timeout":          "Returns 504 Gateway Timeout",
			"/network-error":    "Returns 503 Service Unavailable",
			"/random":           "Randomly returns one of the above responses",
		},
	})
}

// healthHandler returns 200 OK
func healthHandler(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("OK"))
}

// successHandler always returns 200 OK
func successHandler(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	span := trace.SpanFromContext(ctx)
	traceID := span.SpanContext().TraceID().String()

	requestCounter.Add(ctx, 1, metric.WithAttributes(
		attribute.String("http.method", r.Method),
		attribute.String("http.route", "/success"),
		attribute.Int("http.status_code", 200),
	))

	span.SetAttributes(
		attribute.String("handler.type", "success"),
		attribute.Bool("error", false),
	)
	span.SetStatus(codes.Ok, "Request processed successfully")

	logger.Info("Request processed successfully",
		zap.String("route", "/success"),
		zap.String("trace_id", traceID),
	)

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(SuccessResponse{
		Status:  "success",
		Message: "Request processed successfully",
		TraceID: traceID,
		Data:    map[string]any{"timestamp": time.Now().Unix()},
	})
}

// validationErrorHandler returns 400 Bad Request
func validationErrorHandler(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	span := trace.SpanFromContext(ctx)
	traceID := span.SpanContext().TraceID().String()

	errorCounter.Add(ctx, 1, metric.WithAttributes(
		attribute.String("error.type", "validation"),
		attribute.Int("http.status_code", 400),
	))

	requestCounter.Add(ctx, 1, metric.WithAttributes(
		attribute.String("http.method", r.Method),
		attribute.String("http.route", "/validation-error"),
		attribute.Int("http.status_code", 400),
	))

	span.SetAttributes(
		attribute.String("error.type", "validation"),
		attribute.String("handler.type", "error"),
		attribute.Bool("error", true),
	)
	span.SetStatus(codes.Error, "Validation error: invalid input parameters")

	logger.Log(zapcore.WarnLevel, "Validation error: invalid input parameters",
		zap.String("error.type", "validation"),
		zap.Int("http.status_code", 400),
		zap.String("trace_id", traceID),
	)

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusBadRequest)
	json.NewEncoder(w).Encode(ErrorResponse{
		Error:   "validation_error",
		Code:    400,
		Message: "Invalid input parameters",
		TraceID: traceID,
	})
}

// authErrorHandler returns 401 Unauthorized
func authErrorHandler(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	span := trace.SpanFromContext(ctx)
	traceID := span.SpanContext().TraceID().String()

	errorCounter.Add(ctx, 1, metric.WithAttributes(
		attribute.String("error.type", "auth"),
		attribute.Int("http.status_code", 401),
	))

	requestCounter.Add(ctx, 1, metric.WithAttributes(
		attribute.String("http.method", r.Method),
		attribute.String("http.route", "/auth-error"),
		attribute.Int("http.status_code", 401),
	))

	span.SetAttributes(
		attribute.String("error.type", "auth"),
		attribute.String("handler.type", "error"),
		attribute.Bool("error", true),
	)
	span.SetStatus(codes.Error, "Authentication error: invalid or expired token")

	logger.Log(zapcore.WarnLevel, "Authentication error: invalid or expired token",
		zap.String("error.type", "auth"),
		zap.Int("http.status_code", 401),
		zap.String("trace_id", traceID),
	)

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusUnauthorized)
	json.NewEncoder(w).Encode(ErrorResponse{
		Error:   "authentication_error",
		Code:    401,
		Message: "Invalid or expired authentication token",
		TraceID: traceID,
	})
}

// serverErrorHandler returns 500 Internal Server Error with nested span
func serverErrorHandler(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	span := trace.SpanFromContext(ctx)
	traceID := span.SpanContext().TraceID().String()

	// Create a child span to simulate database operation
	dbCtx, dbSpan := tracer.Start(ctx, "database.query")
	dbSpan.SetAttributes(
		attribute.String("db.system", "postgresql"),
		attribute.String("db.operation", "SELECT"),
		attribute.String("db.statement", "SELECT * FROM users WHERE id = $1"),
	)
	time.Sleep(50 * time.Millisecond) // Simulate query time
	dbSpan.SetStatus(codes.Error, "Connection pool exhausted")
	dbSpan.End()

	errorCounter.Add(dbCtx, 1, metric.WithAttributes(
		attribute.String("error.type", "database"),
		attribute.Int("http.status_code", 500),
	))

	requestCounter.Add(dbCtx, 1, metric.WithAttributes(
		attribute.String("http.method", r.Method),
		attribute.String("http.route", "/server-error"),
		attribute.Int("http.status_code", 500),
	))

	span.SetAttributes(
		attribute.String("error.type", "database"),
		attribute.String("handler.type", "error"),
		attribute.Bool("error", true),
	)
	span.SetStatus(codes.Error, "Database error: connection pool exhausted")

	logger.Error("Database error: connection pool exhausted",
		zap.String("error.type", "database"),
		zap.Int("http.status_code", 500),
		zap.String("trace_id", traceID),
	)

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusInternalServerError)
	json.NewEncoder(w).Encode(ErrorResponse{
		Error:   "internal_server_error",
		Code:    500,
		Message: "Database connection pool exhausted",
		TraceID: traceID,
	})
}

// timeoutHandler simulates a timeout and returns 504
func timeoutHandler(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	span := trace.SpanFromContext(ctx)
	traceID := span.SpanContext().TraceID().String()

	// Simulate upstream call that times out
	upstreamCtx, upstreamSpan := tracer.Start(ctx, "upstream.service.call")
	upstreamSpan.SetAttributes(
		attribute.String("peer.service", "upstream-api"),
		attribute.String("http.url", "https://upstream-api.example.com/data"),
		attribute.String("http.method", "GET"),
	)
	time.Sleep(150 * time.Millisecond) // Simulate long wait
	upstreamSpan.SetStatus(codes.Error, "Request timeout")
	upstreamSpan.End()

	errorCounter.Add(upstreamCtx, 1, metric.WithAttributes(
		attribute.String("error.type", "timeout"),
		attribute.Int("http.status_code", 504),
	))

	requestCounter.Add(upstreamCtx, 1, metric.WithAttributes(
		attribute.String("http.method", r.Method),
		attribute.String("http.route", "/timeout"),
		attribute.Int("http.status_code", 504),
	))

	span.SetAttributes(
		attribute.String("error.type", "timeout"),
		attribute.String("handler.type", "error"),
		attribute.Bool("error", true),
	)
	span.SetStatus(codes.Error, "Gateway timeout: upstream service did not respond")

	logger.Error("Gateway timeout: upstream service did not respond",
		zap.String("error.type", "timeout"),
		zap.Int("http.status_code", 504),
		zap.String("trace_id", traceID),
	)

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusGatewayTimeout)
	json.NewEncoder(w).Encode(ErrorResponse{
		Error:   "gateway_timeout",
		Code:    504,
		Message: "Upstream service did not respond in time",
		TraceID: traceID,
	})
}

// networkErrorHandler returns 503 Service Unavailable
func networkErrorHandler(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	span := trace.SpanFromContext(ctx)
	traceID := span.SpanContext().TraceID().String()

	errorCounter.Add(ctx, 1, metric.WithAttributes(
		attribute.String("error.type", "network"),
		attribute.Int("http.status_code", 503),
	))

	requestCounter.Add(ctx, 1, metric.WithAttributes(
		attribute.String("http.method", r.Method),
		attribute.String("http.route", "/network-error"),
		attribute.Int("http.status_code", 503),
	))

	span.SetAttributes(
		attribute.String("error.type", "network"),
		attribute.String("handler.type", "error"),
		attribute.Bool("error", true),
	)
	span.SetStatus(codes.Error, "Network error: unable to reach downstream service")

	logger.Error("Network error: unable to reach downstream service",
		zap.String("error.type", "network"),
		zap.Int("http.status_code", 503),
		zap.String("trace_id", traceID),
	)

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusServiceUnavailable)
	json.NewEncoder(w).Encode(ErrorResponse{
		Error:   "service_unavailable",
		Code:    503,
		Message: "Unable to reach downstream service",
		TraceID: traceID,
	})
}

// randomHandler randomly generates one of the above responses
func randomHandler(w http.ResponseWriter, r *http.Request) {
	handlers := []http.HandlerFunc{
		successHandler,
		validationErrorHandler,
		authErrorHandler,
		serverErrorHandler,
		timeoutHandler,
		networkErrorHandler,
	}

	// Randomly select a handler
	handler := handlers[rand.Intn(len(handlers))]
	handler(w, r)
}

// startAutoGenerator generates background traffic
func startAutoGenerator(ctx context.Context) {
	logger.Info("Auto-generator enabled",
		zap.Duration("request_interval", requestInterval),
	)

	client := &http.Client{
		Timeout: 10 * time.Second,
	}

	ticker := time.NewTicker(requestInterval)
	defer ticker.Stop()

	endpoints := []string{
		"http://localhost:8080/success",
		"http://localhost:8080/validation-error",
		"http://localhost:8080/auth-error",
		"http://localhost:8080/server-error",
		"http://localhost:8080/timeout",
		"http://localhost:8080/network-error",
	}

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			endpoint := endpoints[rand.Intn(len(endpoints))]
			go func(url string) {
				req, _ := http.NewRequestWithContext(ctx, "GET", url, nil)
				_, _ = client.Do(req)
			}(endpoint)
		}
	}
}