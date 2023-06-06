##########################
# OpenTelemetry Settings #
##########################
from opentelemetry.sdk.resources import Resource
import uuid

OTEL_RESOURCE_ATTRIBUTES = {
    "service.instance.id": str(uuid.uuid1()), 
    "environment": "local"
}

##########
# Traces #
##########
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.trace.status import Status, StatusCode

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
fib_counter = metrics.get_meter("opentelemetry.instrumentation.custom").create_counter("fibonacci.invocations", unit="1", description="Measures the number of times the fibonacci method is invoked.")

########
# Logs # - OpenTelemetry Logs are still in the "experimental" state, so function names may change in the future
########
import logging
logging.basicConfig(level=logging.DEBUG)

from opentelemetry import _logs
from opentelemetry.sdk._logs import LoggerProvider, LoggingHandler
from opentelemetry.sdk._logs.export import BatchLogRecordProcessor
from opentelemetry.exporter.otlp.proto.grpc._log_exporter import OTLPLogExporter

# Initialize logging and an exporter that can send data to an OTLP endpoint by attaching OTLP handler to root logger
# SELECT * FROM Log WHERE instrumentation.provider='opentelemetry'
_logs.set_logger_provider(LoggerProvider(resource=Resource.create(OTEL_RESOURCE_ATTRIBUTES)))
logging.getLogger().addHandler(LoggingHandler(logger_provider=_logs.get_logger_provider().add_log_record_processor(BatchLogRecordProcessor(OTLPLogExporter()))))

#####################
# Flask Application #
#####################
from flask import Flask, jsonify, request
from opentelemetry.instrumentation.flask import FlaskInstrumentor

app = Flask(__name__)
FlaskInstrumentor().instrument_app(app)

@app.route("/fibonacci")
@trace.get_tracer("opentelemetry.instrumentation.custom").start_as_current_span("/fibonacci")
def fibonacci():
    args = request.args
    x = int(args.get("n"))

    trace.get_current_span().set_attribute("fibonacci.n", x)
    
    try:
        assert 1 <= x <= 90
        array = [0, 1]
        for n in range(2, x + 1):
            array.append(array[n - 1] + array[n - 2])

        trace.get_current_span().set_attribute("fibonacci.result", array[x])
        fib_counter.add(1, {"fibonacci.valid.n": "true"})
        logging.info("Compute fibonacci(" + str(x) + ") = " + str(array[x]))
        return jsonify(n=x, result=array[x])

    except (ValueError, AssertionError):
        trace.get_current_span().set_status(Status(StatusCode.ERROR, "n must be 1 <= n <= 90."))
        fib_counter.add(1, {"fibonacci.valid.n": "false"})
        logging.error("Failed to compute fibonacci(" + str(x) + ")")
        return jsonify({"message": "n must be 1 <= n <= 90."})

app.run(host='0.0.0.0', port=8080)
