from flask import Flask, jsonify, request
import logging
from opentelemetry import metrics
from opentelemetry import trace
from opentelemetry.trace.status import Status, StatusCode

fib_counter = metrics.get_meter("opentelemetry.instrumentation.custom").create_counter("fibonacci.invocations", unit="1", description="Measures the number of times the fibonacci method is invoked.")

logging.basicConfig(level=logging.DEBUG)

app = Flask(__name__)

@app.route("/fibonacci")
@trace.get_tracer("opentelemetry.instrumentation.custom").start_as_current_span("/fibonacci")
def fibonacci():
    args = request.args
    x = int(args.get("n"))
    error_message = "n must be 1 <= n <= 90."
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

    except AssertionError:
        trace.get_current_span().record_exception(exception=Exception, attributes={"exception.type": "AssertionError", "exception.message": error_message})
        trace.get_current_span().set_status(Status(StatusCode.ERROR, error_message))
        fib_counter.add(1, {"fibonacci.valid.n": "false"})
        logging.error("Failed to compute fibonacci(" + str(x) + ")")
        return jsonify({"message": error_message})
    
app.run(host='0.0.0.0', port=8080)
