from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.sdk.resources import Resource
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter

from metrics import generate_metrics

def get_large_value():
    result = ""
    for _ in range(0,4095):
        result += "A"
    result += "BBBBBBBBBBBBB"
    return result

largeValue = get_large_value()

# Pass in the service we're creating into the tracer provider
trace.set_tracer_provider(
    TracerProvider(resource=Resource.create({"service.name": "python-app"}))
)

# Create a BatchSpanProcessor and add the exporter to it
# Add to the tracer
trace.get_tracer_provider().add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))

tracer = trace.get_tracer(__name__)
with tracer.start_as_current_span("foo") as span1:
    # New Relic only accepts attributes values that are less than 4096 characters.
    # When viewing this span in New Relic, the value of the "truncate" attribute will contain no Bs
    span1.set_attribute("truncated", largeValue)
    generate_metrics()
    with tracer.start_as_current_span("bar"):
        with tracer.start_as_current_span("baz"):
            print("Hello world from OpenTelemetry Python!")
