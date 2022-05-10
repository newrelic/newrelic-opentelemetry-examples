from typing import List
from flask import Flask
from opentelemetry.instrumentation.flask import FlaskInstrumentor
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider, Span
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.sdk.resources import Resource
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter

# New Relic only accepts values that are less than 4096 characters.
# In New Relic, attributes with this value should have no Bs
def get_large_value():
    result = ""
    for _ in range(0,4095):
        result += "A"
    result += "BBBBBBBBBBBBB"
    return result

def response_hook(span: Span, status: str, response_headers: List):
    if span and span.is_recording():
        # New Relic only accepts attributes values that are less than 4096 characters.
        # When viewing this span in New Relic, the value of the "truncate" attribute will contain no Bs
        span.set_attribute("truncated", get_large_value())

# Pass in the service we're creating into the tracer provider
trace.set_tracer_provider(
    TracerProvider(resource=Resource.create({"service.name": "python-flask-app"}))
)

# Create a BatchSpanProcessor and add the exporter to it
# Add to the tracer
trace.get_tracer_provider().add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))

app = Flask(__name__)

FlaskInstrumentor().instrument_app(app, response_hook=response_hook)


@app.route("/")
def hello():
    return "Hello world from OpenTelemetry Python!"


if __name__ == "__main__":
    app.run(debug=True)
