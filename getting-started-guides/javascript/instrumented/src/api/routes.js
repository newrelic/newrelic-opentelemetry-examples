// routes.js
const express = require("express");
const router = express.Router();

const opentelemetry = require("@opentelemetry/api");
const tracer = opentelemetry.trace.getTracer("otel-node-server");

const fibonacci = (n, parentSpan) => {
  const span = tracer.startSpan("fibonacci", {
    parent: parentSpan,
    attributes: { n },
  });

  if (n < 1 || n > 90) {
    span.setStatus({
      code: opentelemetry.SpanStatusCode.ERROR,
      message: "n must be 1 <= n <= 90",
    });
    span.end();
    throw new Error("n must be 1 <= n <= 90");
  }

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

  span.end();
  return sequence.slice(0, n);
};

router.get("/fibonacci/:n", (req, res) => {
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
