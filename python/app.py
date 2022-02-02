from flask import Flask
from opentelemetry.instrumentation.flask import FlaskInstrumentor
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.sdk.resources import Resource
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter

# Pass in the service we're creating into the tracer provider
trace.set_tracer_provider(
    TracerProvider(resource=Resource.create({"service.name": "python-flask-app"}))
)

# Create a BatchSpanProcessor and add the exporter to it
# Add to the tracer
trace.get_tracer_provider().add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))

app = Flask(__name__)

FlaskInstrumentor().instrument_app(app)


@app.route("/")
def hello():
    return "Hello world from OpenTelemetry Python!"


if __name__ == "__main__":
    app.run(debug=True)
