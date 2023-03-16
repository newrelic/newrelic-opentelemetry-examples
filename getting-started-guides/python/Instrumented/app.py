from flask import Flask, jsonify
import logging
logging.basicConfig(level=logging.DEBUG)

app = Flask(__name__)

##########################
# OpenTelemetry Settings #
##########################
from opentelemetry.sdk.resources import Resource
import uuid

OTEL_RESOURCE_ATTRIBUTES = {
    "service.name": "otel-python-instrumented", 
    "service.instance.id": str(uuid.uuid1()), 
    "environment": "local",
    "tags.team": "newrelic"
}

##########
# Traces #
##########
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter

# Initialize tracing and an exporter that can send data to an OTLP endpoint
# SELECT * FROM Span WHERE instrumentation.provider='opentelemetry'
trace.set_tracer_provider(TracerProvider(resource=Resource.create(OTEL_RESOURCE_ATTRIBUTES)))
trace.get_tracer_provider().add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))

###########
# Metrics #
###########
from opentelemetry import metrics
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter

# Initialize metering and an exporter that can send data to an OTLP endpoint
# SELECT count(`http.server.active_requests`) FROM Metric FACET `service.name` TIMESERIES
metrics.set_meter_provider(MeterProvider(resource=Resource.create(OTEL_RESOURCE_ATTRIBUTES), metric_readers=[PeriodicExportingMetricReader(OTLPMetricExporter())]))
metrics.get_meter_provider()

########
# Logs # - OpenTelemetry Logs are still in the "experimental" state, so function names may change in the future
########
from opentelemetry import _logs
from opentelemetry.sdk._logs import LoggerProvider, LoggingHandler
from opentelemetry.sdk._logs.export import BatchLogRecordProcessor
from opentelemetry.exporter.otlp.proto.grpc._log_exporter import OTLPLogExporter

# Initialize logging and an exporter that can send data to an OTLP endpoint by attaching OTLP handler to root logger
# SELECT * FROM Log WHERE instrumentation.provider='opentelemetry'
_logs.set_logger_provider(LoggerProvider(resource=Resource.create(OTEL_RESOURCE_ATTRIBUTES)))
logging.getLogger().addHandler(LoggingHandler(logger_provider=_logs.get_logger_provider().add_log_record_processor(BatchLogRecordProcessor(OTLPLogExporter()))))

########################
# Auto-Instrumentation #
########################
from opentelemetry.instrumentation.flask import FlaskInstrumentor
from opentelemetry.instrumentation.logging import LoggingInstrumentor

# No OTEL SpanID/TraceID for spans/logs if you leave this out
FlaskInstrumentor().instrument_app(app)
LoggingInstrumentor().instrument() 

# The rest of the Flask application
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