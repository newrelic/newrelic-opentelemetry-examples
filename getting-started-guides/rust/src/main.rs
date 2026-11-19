use actix_web::{web, App, HttpServer};
use opentelemetry_instrumentation_actix_web::{RequestMetrics, RequestTracing};
use opentelemetry::{global, KeyValue};
use opentelemetry::metrics::Counter;
use opentelemetry::trace::{Status, TraceContextExt, Tracer};
use opentelemetry_appender_tracing::layer::OpenTelemetryTracingBridge;
use opentelemetry_otlp::WithTonicConfig;
use opentelemetry_sdk::Resource;
use opentelemetry_sdk::logs::SdkLoggerProvider;
use opentelemetry_sdk::metrics::{SdkMeterProvider, Temporality};
use opentelemetry_sdk::propagation::TraceContextPropagator;
use opentelemetry_sdk::trace::SdkTracerProvider;
use serde::{Serialize, Deserialize};
use std::sync::LazyLock;
use tracing_subscriber::EnvFilter;
use tracing_subscriber::prelude::*;

static FIBONACCI_INVOCATIONS: LazyLock<Counter<u64>> = LazyLock::new(|| {
    global::meter("fibonacci_server")
        .u64_counter("fibonacci.invocations")
        .with_description("Measures the number of times the fibonacci method is invoked.")
        .build()
});

#[derive(Deserialize)]
struct FibonacciRequest {
    n: i64
}

#[derive(Serialize)]
struct FibonacciResult {
    #[serde(skip_serializing_if = "is_zero")]
    #[serde(default)]
    n: i64,
    #[serde(skip_serializing_if = "is_zero")]
    #[serde(default)]
    result: i64,
    #[serde(skip_serializing_if = "is_empty")]
    #[serde(default)]
    message: String,
}

fn is_zero(num: &i64) -> bool {
    *num == 0
}

fn is_empty(str: &String) -> bool {
    str.is_empty()
}

async fn fibonacci(req: web::Query<FibonacciRequest>) -> web::Json<FibonacciResult> {
    let result_or_error = compute_fibonacci(req.n);

    match result_or_error {
        Ok(result) => web::Json(FibonacciResult { n: req.n, result: result, message: String::new()}),
        Err(error) => web::Json(FibonacciResult { n: 0, result: 0, message: format!("{}", error)}),
    }
}

fn compute_fibonacci(n: i64) -> Result<i64, Box<dyn std::error::Error>> {
    let tracer = global::tracer("fibonacci_server");

    // in_span makes the span current, so logs emitted inside it carry its trace context.
    tracer.in_span("fibonacci", |cx| {
        let span = cx.span();
        span.set_attribute(KeyValue::new("fibonacci.n", n));

        if n < 1 || n > 90 {
            let err_msg = "n must be between 1 and 90";
            span.set_status(Status::error(err_msg));

            // TODO: Figure out how to record the error
            // span.record_error(err);

            FIBONACCI_INVOCATIONS.add(1, &[KeyValue::new("fibonacci.valid.n", false)]);
            tracing::error!("Failed to compute fibonacci({n})");
            return Err(Box::from(err_msg));
        }

        let mut result: i64 = 1;
        if n > 2 {
            let mut a: i64 = 0;
            let mut b: i64 = 1;

            let mut i: i64 = 1;
            while i < n {
                result = a + b;
                a = b;
                b = result;
                i = i + 1;
            }
        }
        span.set_attribute(KeyValue::new("fibonacci.result", result));
        FIBONACCI_INVOCATIONS.add(1, &[KeyValue::new("fibonacci.valid.n", true)]);
        tracing::info!("Compute fibonacci({n}) = {result}");
        Ok(result)
    })
}

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    global::set_text_map_propagator(TraceContextPropagator::new());

    // Includes the SDK, telemetry, and OTEL_RESOURCE_ATTRIBUTES / OTEL_SERVICE_NAME detectors.
    let resource = Resource::builder().build();

    let span_exporter = opentelemetry_otlp::SpanExporter::builder()
        .with_tonic()
        .with_tls_config(tonic::transport::ClientTlsConfig::new().with_native_roots())
        .build()
        .expect("failed to initialize the span exporter");

    let tracer_provider = SdkTracerProvider::builder()
        .with_resource(resource.clone())
        .with_batch_exporter(span_exporter)
        .build();

    global::set_tracer_provider(tracer_provider.clone());

    let metric_exporter = opentelemetry_otlp::MetricExporter::builder()
        .with_tonic()
        .with_tls_config(tonic::transport::ClientTlsConfig::new().with_native_roots())
        .with_temporality(Temporality::Delta)
        .build()
        .expect("failed to initialize the metric exporter");

    let meter_provider = SdkMeterProvider::builder()
        .with_resource(resource.clone())
        .with_periodic_exporter(metric_exporter)
        .build();

    global::set_meter_provider(meter_provider.clone());

    let log_exporter = opentelemetry_otlp::LogExporter::builder()
        .with_tonic()
        .with_tls_config(tonic::transport::ClientTlsConfig::new().with_native_roots())
        .build()
        .expect("failed to initialize the log exporter");

    let logger_provider = SdkLoggerProvider::builder()
        .with_resource(resource)
        .with_batch_exporter(log_exporter)
        .build();

    // Send tracing events to OpenTelemetry, except those from the exporter's own
    // HTTP/gRPC stack, which would otherwise export logs about exporting logs.
    let otel_filter = EnvFilter::new("info")
        .add_directive("hyper=off".parse().unwrap())
        .add_directive("tonic=off".parse().unwrap())
        .add_directive("h2=off".parse().unwrap())
        .add_directive("reqwest=off".parse().unwrap());
    tracing_subscriber::registry()
        .with(OpenTelemetryTracingBridge::new(&logger_provider).with_filter(otel_filter))
        .with(tracing_subscriber::fmt::layer().with_filter(EnvFilter::new("info")))
        .init();

    HttpServer::new(|| {
        App::new()
            .wrap(RequestTracing::new())
            .wrap(RequestMetrics::default())
            .route("/fibonacci", web::get().to(fibonacci))
    })
    .bind(("0.0.0.0", 8080))?
    .run()
    .await?;

    // Flush any buffered telemetry before exiting.
    if let Err(err) = tracer_provider.shutdown() {
        eprintln!("failed to shut down the tracer provider: {err}");
    }
    if let Err(err) = meter_provider.shutdown() {
        eprintln!("failed to shut down the meter provider: {err}");
    }
    if let Err(err) = logger_provider.shutdown() {
        eprintln!("failed to shut down the logger provider: {err}");
    }

    Ok({})
}
