from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.sdk.resources import Resource
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter

# Pass in the service we're creating into the tracer provider
trace.set_tracer_provider(
    TracerProvider(resource=Resource.create(
        {"service.name": "python-app"})
    )
)

# Create a BatchSpanProcessor and add the exporter to it
# Add to the tracer
trace.get_tracer_provider().add_span_processor(
    BatchSpanProcessor(OTLPSpanExporter(
        endpoint="https://otlp.nr-data.net:4317")
    )
)

tracer = trace.get_tracer(__name__)
with tracer.start_as_current_span("foo"):
    with tracer.start_as_current_span("bar"):
        with tracer.start_as_current_span("baz"):
            print("Hello world from OpenTelemetry Python!")