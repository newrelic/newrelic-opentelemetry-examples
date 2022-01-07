import flask
from flask import request, jsonify
# import uuid

from grpc import Compression
from opentelemetry import trace
from opentelemetry.instrumentation.flask import FlaskInstrumentor
from opentelemetry.instrumentation.requests import RequestsInstrumentor
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.sdk.resources import Resource
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter

trace.set_tracer_provider(
    TracerProvider(
        resource=Resource.create({
            "service.name": "appathon-python",
            "service.instance.id": "2193801",
            "telemetry.sdk.name": "opentelemetry",
            "telemetry.sdk.language": "python",
            "telemetry.sdk.version": "0.13.dev0"
        }),
    ),
)

trace.get_tracer_provider().add_span_processor(
    BatchSpanProcessor(OTLPSpanExporter(
        compression=Compression.Gzip)
    )
)

app = flask.Flask(__name__)
FlaskInstrumentor().instrument_app(app)
RequestsInstrumentor().instrument()

tracer = trace.get_tracer(__name__)

@app.route("/fibonacci")
def fib():
    n = int(request.args.get('n'))
    return jsonify(
        n=n,
        result=calcfib(n))

def calcfib(x):
    with tracer.start_as_current_span("fibonacci") as span:
        span.set_attribute("oteldemo.n", x)
        if x == 0:
            return 0
        b, a = 0, 1             # b, a initialized as F(0), F(1)
        for i in range(1,x) :
            b, a = a, a+b       # b, a always store F(i-1), F(i) 
        span.set_attribute("oteldemo.result", a)
        return a

if __name__ == '__main__':
    app.run()

app.run(debug=True, port=5000)