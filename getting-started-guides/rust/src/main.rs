use std::time::Duration;

use actix_web::{web, App, HttpServer};
use actix_web_opentelemetry::RequestTracing;
use opentelemetry::{global, KeyValue};
use opentelemetry::trace::{Span, Status, Tracer};
use opentelemetry_sdk::runtime;
use opentelemetry_sdk::propagation::TraceContextPropagator;
use opentelemetry_sdk::resource::{Resource, ResourceDetector, TelemetryResourceDetector};
use opentelemetry_sdk::trace::Config;
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

    let telemetry_resource = TelemetryResourceDetector.detect(Duration::from_secs(0));
    let service_name_resource = Resource::new(vec![KeyValue::new(
        "service.name",
        "getting-started-rust"
    )]);

    let resource = service_name_resource.merge(&telemetry_resource);

    let _tracer = opentelemetry_otlp::new_pipeline()
        .tracing()
        .with_exporter(opentelemetry_otlp::new_exporter().tonic())
        .with_trace_config(Config::default().with_resource(resource))
        .install_batch(runtime::Tokio)
        .expect("failed to initialize the trace pipeline");

    HttpServer::new(|| {
        App::new()
            .wrap(RequestTracing::new())
            .route("/fibonacci", web::get().to(fibonacci))
    })
    .bind(("127.0.0.1", 8080))?
    .run()
    .await?;

    global::shutdown_tracer_provider();

    Ok({})
}
