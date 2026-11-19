use actix_web::{web, App, HttpServer};
use opentelemetry_instrumentation_actix_web::{RequestMetrics, RequestTracing};
use opentelemetry::{global, KeyValue};
use opentelemetry::trace::{Span, Status, Tracer};
use opentelemetry_otlp::WithTonicConfig;
use opentelemetry_sdk::Resource;
use opentelemetry_sdk::metrics::{SdkMeterProvider, Temporality};
use opentelemetry_sdk::propagation::TraceContextPropagator;
use opentelemetry_sdk::trace::SdkTracerProvider;
use serde::{Serialize, Deserialize};

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

    let mut span = tracer
        .span_builder("fibonacci")
        .start(&tracer);

    span.set_attribute(KeyValue::new("fibonacci.n", n));

    if n < 1 || n > 90 {
        let err_msg = "n must be between 1 and 90";
        span.set_status(Status::error(err_msg));

        // TODO: Figure out how to record the error
        // span.record_error(err);

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
    Ok(result)
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
        .with_resource(resource)
        .with_periodic_exporter(metric_exporter)
        .build();

    global::set_meter_provider(meter_provider.clone());

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

    Ok({})
}
