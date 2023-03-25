module github.com/newrelic/newrelic-opentelemetry-examples/getting-started-guides/go/instrumented

go 1.20

require (
	go.opentelemetry.io/otel v1.15.0-rc.2
	go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc v0.38.0-rc.2
	go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc v1.15.0-rc.2
	go.opentelemetry.io/otel/metric v1.15.0-rc.2
	go.opentelemetry.io/otel/sdk v1.15.0-rc.2
	go.opentelemetry.io/otel/sdk/metric v0.38.0-rc.2
	go.opentelemetry.io/otel/trace v1.15.0-rc.2
)

require (
	github.com/cenkalti/backoff/v4 v4.2.0 // indirect
	github.com/go-logr/logr v1.2.3 // indirect
	github.com/go-logr/stdr v1.2.2 // indirect
	github.com/golang/protobuf v1.5.2 // indirect
	github.com/grpc-ecosystem/grpc-gateway/v2 v2.7.0 // indirect
	go.opentelemetry.io/otel/exporters/otlp/internal/retry v1.15.0-rc.2 // indirect
	go.opentelemetry.io/otel/exporters/otlp/otlpmetric v0.38.0-rc.2 // indirect
	go.opentelemetry.io/otel/exporters/otlp/otlptrace v1.15.0-rc.2 // indirect
	go.opentelemetry.io/proto/otlp v0.19.0 // indirect
	golang.org/x/net v0.7.0 // indirect
	golang.org/x/sys v0.6.0 // indirect
	golang.org/x/text v0.7.0 // indirect
	google.golang.org/genproto v0.0.0-20230110181048-76db0878b65f // indirect
	google.golang.org/grpc v1.53.0 // indirect
	google.golang.org/protobuf v1.30.0 // indirect
)
