module github.com/newrelic/newrelic-opentelemetry-examples

go 1.16

require (
	github.com/google/uuid v1.3.0
	go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp v0.27.0
	go.opentelemetry.io/otel v1.3.0
	go.opentelemetry.io/otel/exporters/stdout/stdouttrace v1.3.0
	go.opentelemetry.io/otel/sdk v1.3.0
	go.opentelemetry.io/otel/trace v1.3.0
)
