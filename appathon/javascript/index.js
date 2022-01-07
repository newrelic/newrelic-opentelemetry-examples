const { trace, context } = require('@opentelemetry/api');

const express = require("express");
const app = express();

const PORT = process.env.PORT || 8080;

app.get("/fibonacci", (req, res) => {
  let n = Number(req.query.n);
  
  if (!n || n < 1|| n > 90) {
    try {
      throw new Error("n must be 1 <= n <= 90");
    } catch (error) {
      res.status(400).send({"message": error.message });
    }
  } else {
    res.status(200).send({
      "n": n, 
      "result": fibonacci(n)
    });
  }
});

function fibonacci(element) {
  let currentSpan = trace.getSpan(context.active());
  let tracer = trace.getTracer("fibonacci");
  let ctx = trace.setSpan(context.active(), currentSpan);
  let span = tracer.startSpan("fibonacci", undefined, ctx);

  let sequence = [0, 1];

  for (i = 2; i <= element; i++) {
      sequence[i] = sequence[i - 2] + sequence[i - 1];
  }

  let result = sequence[element];

  span.setAttribute("n", element)
  span.setAttribute("result", result);
  span.end();

  return result;
}

app.listen(parseInt(PORT, 10), () => {
  console.log(`Listening for requests on http://localhost:${PORT}`);
})