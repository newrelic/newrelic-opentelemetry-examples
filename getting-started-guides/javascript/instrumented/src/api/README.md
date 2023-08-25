## OpenTelemetry Tracing Enhancements in Backend Fibonacci Route Observability

This README file describes the changes made to an Express.js API server [routes.js](./routes.js) file (compared to [uninstrumented routes.js]('./../../../../uninstrumented/src/api/routes.js)) to introduce OpenTelemetry tracing capabilities.

It showcases custom instrumentation capabilities with granular control over the spans and traces created.

#### Added OpenTelemetry Module
```javascript
const opentelemetry = require("@opentelemetry/api");
const tracer = opentelemetry.trace.getTracer("otel-node-server");
```

The OpenTelemetry API module was added to the code. It provides interfaces and classes necessary for implementing tracing. A tracer object was also instantiated with the name "otel-node-server".
(the name is arbitrary, but should be descriptive of the service being instrumented, it also matches the name of our service so it'll show up in the observability tool as one service).

#### Modified fibonacci Function
The fibonacci function now accepts a second parameter, parentSpan. This allows the function to link its operations to the trace of the incoming HTTP request that invoked it.

```javascript
const fibonacci = (n, parentSpan) => {
```

A new span is started at the beginning of the function. This span is set as the child of the passed parentSpan, and an attribute n is added to it.

```javascript
const span = tracer.startSpan("fibonacci", {
  parent: parentSpan,
  attributes: { n },
});
```

If an error occurs (when n is not in the range 1 to 90), the status of the span is updated to reflect the error before the span is ended and the error is thrown.

```javascript
if (n < 1 || n > 90) {
  span.setStatus({
    code: opentelemetry.SpanStatusCode.ERROR,
    message: "n must be 1 <= n <= 90",
  });
  span.end();
  throw new Error("n must be 1 <= n <= 90");
}
```

Within the calculation loop of the Fibonacci sequence, a new span is created for each number pushed to the sequence array. This span is named push sequence ${i}, where i is the current index. This span is also linked to the main Fibonacci function span, establishing a hierarchy of spans. Once the number is added to the sequence, the span is ended.

```javascript
if (n > 2) {
  for (let i = 2; i < n; i++) {
    const pushSpan = tracer.startSpan(push sequence ${i}, {
      parent: span,
    });
    sequence.push(sequence[i - 1] + sequence[i - 2]);
    pushSpan.end();
  }
}
```

Finally, once the Fibonacci calculation is complete, the main Fibonacci span is ended.

```javascript
span.end();
```

#### Modified /fibonacci/:n Route Handler
The route handler for GET /fibonacci/:n now creates a parent span for the incoming request. This span is named "GET /fibonacci/:n".

```javascript
const parentSpan = tracer.startSpan("GET /fibonacci/:n");
```

The parentSpan is then passed to the fibonacci function when it is invoked.

```javascript
const result = fibonacci(n, parentSpan);
```

Once the response has been sent, the parent span is ended.

```javascript
parentSpan.end();
```

These changes allow for detailed tracing of the operations of the Fibonacci server. Each incoming request, the Fibonacci calculation, and the individual sequence number calculations now have their own spans, which are linked in a parent-child relationship, providing visibility into the operation and performance of the server.