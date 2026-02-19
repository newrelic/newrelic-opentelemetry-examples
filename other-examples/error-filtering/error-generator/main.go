package main

import (
	"context"
	"fmt"
	"log"
	"math/rand"
	"net/http"
	"os"
	"strconv"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/exporters/otlp/otlplog/otlploggrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	otellog "go.opentelemetry.io/otel/log"
	"go.opentelemetry.io/otel/log/global"
	sdklog "go.opentelemetry.io/otel/sdk/log"
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
	otlpLogger     otellog.Logger
	errorCounter   metric.Int64Counter
	requestCounter metric.Int64Counter

	// Configuration from environment variables
	errorRate         float64
	enableTimeoutErr  bool
	enableValidationErr bool
	enableDatabaseErr bool
	enableNetworkErr  bool
	enableAuthErr     bool
	requestInterval   time.Duration
)

type ErrorType struct {
	Name       string
	HTTPStatus int
	Severity   zapcore.Level
	Message    string
}

var errorTypes = map[string]ErrorType{
	"timeout": {
		Name:       "timeout",
		HTTPStatus: 504,
		Severity:   zapcore.ErrorLevel,
		Message:    "Request timeout: upstream service did not respond",
	},
	"validation": {
		Name:       "validation",
		HTTPStatus: 400,
		Severity:   zapcore.WarnLevel,
		Message:    "Validation error: invalid input parameters",
	},
	"database": {
		Name:       "database",
		HTTPStatus: 500,
		Severity:   zapcore.ErrorLevel,
		Message:    "Database error: connection pool exhausted",
	},
	"network": {
		Name:       "network",
		HTTPStatus: 503,
		Severity:   zapcore.ErrorLevel,
		Message:    "Network error: unable to reach downstream service",
	},
	"auth": {
		Name:       "auth",
		HTTPStatus: 401,
		Severity:   zapcore.WarnLevel,
		Message:    "Authentication error: invalid or expired token",
	},
}

func init() {
	rand.Seed(time.Now().UnixNano())
	loadConfig()
}

func loadConfig() {
	// Error rate (0.0 to 1.0)
	if val := os.Getenv("ERROR_RATE"); val != "" {
		if rate, err := strconv.ParseFloat(val, 64); err == nil {
			errorRate = rate
		}
	} else {
		errorRate = 0.3 // Default 30% error rate
	}

	// Enable/disable specific error types
	enableTimeoutErr = getEnvBool("ENABLE_TIMEOUT_ERRORS", true)
	enableValidationErr = getEnvBool("ENABLE_VALIDATION_ERRORS", true)
	enableDatabaseErr = getEnvBool("ENABLE_DATABASE_ERRORS", true)
	enableNetworkErr = getEnvBool("ENABLE_NETWORK_ERRORS", true)
	enableAuthErr = getEnvBool("ENABLE_AUTH_ERRORS", true)

	// Request interval
	if val := os.Getenv("REQUEST_INTERVAL_MS"); val != "" {
		if ms, err := strconv.Atoi(val); err == nil {
			requestInterval = time.Duration(ms) * time.Millisecond
		}
	} else {
		requestInterval = 2000 * time.Millisecond // Default 2 seconds
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

func initOTel(ctx context.Context) func() {
	res, err := resource.New(ctx,
		resource.WithAttributes(
			semconv.ServiceName("error-generator"),
			semconv.ServiceVersion("1.0.0"),
		),
	)
	if err != nil {
		log.Fatalf("failed to create resource: %v", err)
	}

	// Initialize trace provider
	traceExporter, err := otlptracegrpc.New(ctx,
		otlptracegrpc.WithEndpoint(getOTLPEndpoint()),
		otlptracegrpc.WithInsecure(),
	)
	if err != nil {
		log.Fatalf("failed to create trace exporter: %v", err)
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
		log.Fatalf("failed to create metric exporter: %v", err)
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
		log.Fatalf("failed to create error counter: %v", err)
	}

	requestCounter, err = meter.Int64Counter(
		"requests.total",
		metric.WithDescription("Total number of requests"),
	)
	if err != nil {
		log.Fatalf("failed to create request counter: %v", err)
	}

	// Initialize log provider
	logExporter, err := otlploggrpc.New(ctx,
		otlploggrpc.WithEndpoint(getOTLPEndpoint()),
		otlploggrpc.WithInsecure(),
	)
	if err != nil {
		log.Fatalf("failed to create log exporter: %v", err)
	}

	lp := sdklog.NewLoggerProvider(
		sdklog.WithProcessor(sdklog.NewBatchProcessor(logExporter)),
		sdklog.WithResource(res),
	)
	global.SetLoggerProvider(lp)

	// Get OTLP logger
	otlpLogger = lp.Logger("error-generator")

	// Initialize logger with JSON output
	config := zap.NewProductionConfig()
	config.EncoderConfig.TimeKey = "timestamp"
	config.EncoderConfig.MessageKey = "message"
	config.EncoderConfig.LevelKey = "severity"
	logger, err = config.Build()
	if err != nil {
		log.Fatalf("failed to create logger: %v", err)
	}

	return func() {
		_ = tp.Shutdown(ctx)
		_ = mp.Shutdown(ctx)
		_ = lp.Shutdown(ctx)
		_ = logger.Sync()
	}
}

func getOTLPEndpoint() string {
	if endpoint := os.Getenv("OTEL_EXPORTER_OTLP_ENDPOINT"); endpoint != "" {
		return endpoint
	}
	return "collector:4317"
}

func selectRandomError() *ErrorType {
	enabledErrors := []ErrorType{}

	if enableTimeoutErr {
		enabledErrors = append(enabledErrors, errorTypes["timeout"])
	}
	if enableValidationErr {
		enabledErrors = append(enabledErrors, errorTypes["validation"])
	}
	if enableDatabaseErr {
		enabledErrors = append(enabledErrors, errorTypes["database"])
	}
	if enableNetworkErr {
		enabledErrors = append(enabledErrors, errorTypes["network"])
	}
	if enableAuthErr {
		enabledErrors = append(enabledErrors, errorTypes["auth"])
	}

	if len(enabledErrors) == 0 {
		return nil
	}

	return &enabledErrors[rand.Intn(len(enabledErrors))]
}

func simulateRequest(ctx context.Context, requestNum int) {
	ctx, span := tracer.Start(ctx, "handle_request")
	defer span.End()

	span.SetAttributes(
		attribute.Int("request.number", requestNum),
	)

	requestCounter.Add(ctx, 1)

	// Determine if this request should error
	shouldError := rand.Float64() < errorRate

	if shouldError {
		errType := selectRandomError()
		if errType != nil {
			handleError(ctx, span, errType, requestNum)
		} else {
			handleSuccess(ctx, span, requestNum)
		}
	} else {
		handleSuccess(ctx, span, requestNum)
	}
}

func handleError(ctx context.Context, span trace.Span, errType *ErrorType, requestNum int) {
	span.SetAttributes(
		attribute.String("error.type", errType.Name),
		attribute.Int("http.status_code", errType.HTTPStatus),
		attribute.Bool("error", true),
	)
	span.SetStatus(codes.Error, errType.Message)

	// Log the error to stdout (zap)
	logger.Log(errType.Severity, errType.Message,
		zap.String("error.type", errType.Name),
		zap.Int("http.status_code", errType.HTTPStatus),
		zap.Int("request.number", requestNum),
		zap.String("trace.id", span.SpanContext().TraceID().String()),
		zap.String("span.id", span.SpanContext().SpanID().String()),
	)

	// Send log via OTLP
	var severity otellog.Severity
	if errType.Severity == zapcore.ErrorLevel {
		severity = otellog.SeverityError
	} else {
		severity = otellog.SeverityWarn
	}

	logRecord := otellog.Record{}
	logRecord.SetTimestamp(time.Now())
	logRecord.SetBody(otellog.StringValue(errType.Message))
	logRecord.SetSeverity(severity)
	logRecord.AddAttributes(
		otellog.String("error.type", errType.Name),
		otellog.Int("http.status_code", errType.HTTPStatus),
		otellog.Int("request.number", requestNum),
		otellog.String("trace.id", span.SpanContext().TraceID().String()),
		otellog.String("span.id", span.SpanContext().SpanID().String()),
	)
	otlpLogger.Emit(ctx, logRecord)

	// Increment error counter
	errorCounter.Add(ctx, 1,
		metric.WithAttributes(
			attribute.String("error.type", errType.Name),
			attribute.Int("http.status_code", errType.HTTPStatus),
		),
	)
}

func handleSuccess(ctx context.Context, span trace.Span, requestNum int) {
	span.SetAttributes(
		attribute.Int("http.status_code", 200),
		attribute.Bool("error", false),
	)
	span.SetStatus(codes.Ok, "success")

	// Log to stdout (zap)
	logger.Info("Request processed successfully",
		zap.Int("request.number", requestNum),
		zap.Int("http.status_code", 200),
		zap.String("trace.id", span.SpanContext().TraceID().String()),
		zap.String("span.id", span.SpanContext().SpanID().String()),
	)

	// Send log via OTLP
	logRecord := otellog.Record{}
	logRecord.SetTimestamp(time.Now())
	logRecord.SetBody(otellog.StringValue("Request processed successfully"))
	logRecord.SetSeverity(otellog.SeverityInfo)
	logRecord.AddAttributes(
		otellog.Int("request.number", requestNum),
		otellog.Int("http.status_code", 200),
		otellog.String("trace.id", span.SpanContext().TraceID().String()),
		otellog.String("span.id", span.SpanContext().SpanID().String()),
	)
	otlpLogger.Emit(ctx, logRecord)
}

func startAutoGenerator(ctx context.Context) {
	logger.Info("Starting auto-generator",
		zap.Float64("error_rate", errorRate),
		zap.Duration("request_interval", requestInterval),
		zap.Bool("timeout_errors", enableTimeoutErr),
		zap.Bool("validation_errors", enableValidationErr),
		zap.Bool("database_errors", enableDatabaseErr),
		zap.Bool("network_errors", enableNetworkErr),
		zap.Bool("auth_errors", enableAuthErr),
	)

	requestNum := 0
	ticker := time.NewTicker(requestInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			requestNum++
			simulateRequest(ctx, requestNum)
		}
	}
}

func healthHandler(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	fmt.Fprintf(w, "OK")
}

func main() {
	ctx := context.Background()
	shutdown := initOTel(ctx)
	defer shutdown()

	// Start health check server
	go func() {
		http.HandleFunc("/health", healthHandler)
		log.Println("Health check server listening on :8080")
		if err := http.ListenAndServe(":8080", nil); err != nil {
			log.Fatalf("failed to start health server: %v", err)
		}
	}()

	// Start auto-generator
	startAutoGenerator(ctx)
}