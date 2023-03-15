

# Traces
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
# from opentelemetry.sdk.resources import Resource
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter

# Metrics

# Logs
from opentelemetry import _logs
from opentelemetry.sdk._logs import LoggerProvider
#from opentelemetry.sdk._logs import LogRecordProcessor
from opentelemetry.sdk._logs.export import BatchLogRecordProcessor
from opentelemetry.exporter.otlp.proto.grpc._log_exporter import OTLPLogExporter
from opentelemetry.sdk._logs import LoggingHandler


from flask import Flask, jsonify

# Auto-Instrumentation Instrumentation
from opentelemetry.instrumentation.flask import FlaskInstrumentor
from opentelemetry.instrumentation.logging import LoggingInstrumentor

import logging
logging.basicConfig(level=logging.DEBUG)

app = Flask(__name__)

# # OpenTelemetry Settings
# import uuid
# serviceId = str(uuid.uuid1())

# OTEL_RESOURCE_ATTRIBUTES = {
#     "service.name": "otel-python-instrumented", 
#     "service.instance.id": serviceId, 
#     "environment": "local",
#     "tags.team": "newrelic"
# }

trace.set_tracer_provider(TracerProvider())
trace.get_tracer_provider().add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))

_logs.set_logger_provider(LoggerProvider())
# _logs.get_logger_provider().add_log_record_processor(BatchLogRecordProcessor(OTLPLogExporter()))
handler = LoggingHandler(level=logging.NOTSET, logger_provider=_logs.get_logger_provider().add_log_record_processor(BatchLogRecordProcessor(OTLPLogExporter())))

# Attach OTLP handler to root logger
logging.getLogger().addHandler(handler)

# Old - No longer used
# log_emitter_provider = LogEmitterProvider(resource=Resource.create(OTEL_RESOURCE_ATTRIBUTES))
# set_log_emitter_provider(log_emitter_provider)

# exporter = OTLPLogExporter(insecure=True)
# log_emitter_provider.add_log_processor(BatchLogProcessor(exporter))
# log_emitter = log_emitter_provider.get_log_emitter(__name__, "0.1")




FlaskInstrumentor().instrument_app(app)
LoggingInstrumentor().instrument() # No OTEL SpanID/TraceID for logs if you leave this out.

@app.route("/fibonacci/<int:x>", strict_slashes=False)
def fibonacci(x):
    array = [0, 1]
    try:
        if x < 1 or x > 90:
            raise ValueError("x must be 1 <= x <= 90.")

        for n in range(2, x + 1):
            array.append(array[n - 1] + array[n - 2])
        logging.info("Compute fibonacci(" + str(x) + ") = " + str(array[x]))
        return jsonify(fibonacci_index=x, fibonacci_number=array[x])
    
    except:
        logging.error("Failed to compute fibonacci(" + str(x) + ")")

app.run(host='0.0.0.0', port=5000)