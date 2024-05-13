const express = require('express');
const opentelemetry = require("@opentelemetry/api");

const PORT = parseInt(process.env.PORT || '8080');
const app = express();

const tracer = opentelemetry.trace.getTracer("fibonacci");
const meter = opentelemetry.metrics.getMeter("fibonacci");

const fibonacciInvocations = meter.createCounter("fibonacci.invocations", {
  description: "Measures the number of times the fibonacci method is invoked.",
});

app.get("/fibonacci", (req, res) => {
  try {
    const n = req.query.n
    res.json({ n: n, result: fibonacci(n) });
  } catch (ex) {
    if (ex instanceof RangeError) {
      const span = opentelemetry.trace.getActiveSpan();
      span.recordException(ex);

      // TODO: The opentelemetry-js express instrumentation undoes explicitly setting the span status.
      // For now, we will return a 500 status instead of 400. This forces an error to show up in the error inbox.
      // span.setStatus({ code: opentelemetry.SpanStatusCode.ERROR, message: ex.message });
      // res.status(400).json({ error: ex.message });
      res.status(500).json({ error: ex.message });
    } else {
      throw ex;
    }
  }
});

function fibonacci(n) {
  return tracer.startActiveSpan("fibonacci", (span) => {
    span.setAttribute("fibonacci.n", n);

    try {
      n = parseInt(n);

      if (n < 1 || n > 90 || isNaN(n)) {
        throw new RangeError("n must be between 1 and 90");
      }

      var result = 1;
      if (n > 2) {
          var a = 0;
          var b = 1;

          for (var i = 1; i < n; i++) {
              result = a + b;
              a = b;
              b = result;
          }
      }

      span.setAttribute("fibonacci.result", result);
      fibonacciInvocations.add(1, { "fibonacci.valid.n": true });
      return result;
    } catch (ex) {
      span.setStatus({ code: opentelemetry.SpanStatusCode.ERROR, message: ex.message });
      span.recordException(ex);
      fibonacciInvocations.add(1, { "fibonacci.valid.n": false });
      throw ex;
    } finally {
      span.end();
    }
  });
};

app.listen(PORT, () => {
  console.log(`Listening for requests on http://localhost:${PORT}`);
});
