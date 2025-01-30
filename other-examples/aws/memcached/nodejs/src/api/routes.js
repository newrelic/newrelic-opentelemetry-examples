// routes.js
const express = require("express");
const router = express.Router();

const opentelemetry = require("@opentelemetry/api");

const tracer = opentelemetry.trace.getTracer(
  process.env.OTEL_SERVICE_NAME_NODE
);

const meter = opentelemetry.metrics.getMeter(
  `${process.env.OTEL_SERVICE_NAME_NODE}`
);
const fibonacciInvocations = meter.createCounter("fibonacci.invocations", {
  description: "Measures the number of times the fibonacci method is invoked.",
});

const fibonacci = (n, parentSpan) => {
  const span = tracer.startSpan("fibonacci", {
    parent: parentSpan,
    attributes: { "fibonacci.n": n },
    kind: opentelemetry.SpanKind.INTERNAL,
  });


 

  let isValidN = true;
  if (n < 1 || n > 90) {
    isValidN = false;
    span.setStatus({
      code: opentelemetry.SpanStatusCode.ERROR,
      message: "n must be 1 <= n <= 90",
    });
    span.recordException(new Error("n must be 1 <= n <= 90"));
    span.end();
    fibonacciInvocations.add(1, { "fibonacci.valid.n": isValidN });
    throw new Error("n must be 1 <= n <= 90");
  }

  fibonacciInvocations.add(1, { "fibonacci.valid.n": isValidN });

  const sequence = [1, 1];
  if (n > 2) {
    for (let i = 2; i < n; i++) {
      const pushSpan = tracer.startSpan(`push sequence ${i}`, {
        parent: span,
      });
      sequence.push(sequence[i - 1] + sequence[i - 2]);
      pushSpan.end();
    }
  }

  span.setAttribute("fibonacci.result", sequence.slice(0, n));
  span.end();
  return sequence.slice(0, n);
};

router.get("/fibonacci/:n", (req, res) => {
  console.log('license key',process.env.NEW_RELIC_LICENSE_INGEST_KEY)


  const parentSpan = tracer.startSpan("GET /fibonacci/:n");
  const n = parseInt(req.params.n);
  try {
    const result = fibonacci(n, parentSpan);
    res.json({ result });
  } catch (error) {
    res.status(400).json({ error: error.message });
  } finally {
    parentSpan.end();
  }
});

// Catch-all route handler
router.use("*", (req, res) => {
  res.status(404).json({ error: "Route not found!" });
});

module.exports = router;
