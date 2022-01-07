module github.com/newrelic/newrelic-opentelemetry-examples

go 1.16

require (
	github.com/go-logr/stdr v1.2.2 // indirect
	github.com/google/uuid v1.3.0
	go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp v0.27.0
	go.opentelemetry.io/otel v1.3.0
	go.opentelemetry.io/otel/exporters/otlp/otlptrace v1.3.0
	go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc v1.3.0
	go.opentelemetry.io/otel/sdk v1.3.0
	go.opentelemetry.io/otel/trace v1.3.0
	golang.org/x/sys v0.0.0-20211216021012-1d35b9e2eb4e // indirect
)
