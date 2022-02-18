const { trace, SpanStatusCode } = require('@opentelemetry/api');

const express = require("express");
const app = express();

const PORT = process.env.PORT || 8080;

app.get("/fibonacci", (req, res) => {
  let n = Number(req.query.n);

  try {
    let resultOfFib = fibonacci(n);

    res.status(200).send({
      "n": n, 
      "result": resultOfFib
    });
  } catch (error) {
    res.status(400).send({"message": error.message });
  }
});

function fibonacci(input) {
  let tracer = trace.getTracer("fibonacci");
  let span = tracer.startSpan("fibonacci");
  
  span.setAttribute("oteldemo.n", input);

  try {
    if (!input || input < 1|| input > 90) {
        throw new Error("n must be 1 <= n <= 90");
    } else {
      let sequence = [0, 1];

      for (let i = 2; i <= input; i++) {
          sequence[i] = sequence[i - 2] + sequence[i - 1];
      }

      let result = sequence[input];

      span.setStatus({ code: SpanStatusCode.OK });
      span.setAttribute("oteldemo.result", result);
      return result;
    }
  } catch(error) {
    span.setStatus({
      code: SpanStatusCode.ERROR,
      message: error.message
    });

    span.recordException(error);
    throw error;
  } finally {
    span.end();
  }
}

app.listen(parseInt(PORT, 10), () => {
  console.log(`Listening for requests on http://localhost:${PORT}`);
})