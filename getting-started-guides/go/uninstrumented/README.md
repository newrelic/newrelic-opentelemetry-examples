# Uninstrumented Go Demo App

This is the raw (not instrumented with OpenTelemetry) Go demo application used in the Getting Started Guide - Go doc. You will be implementing the instrumentation by yourself!

## Prerequisites

- Go 1.18+ (1.20 is used in this example)
- [A New Relic account](https://one.newrelic.com/)

## Setting up environment variables

Before you begin with the instrumentation, set these env vars up.

```
export OTEL_SERVICE_NAME=getting-started-go
export OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_license_key>
export OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317
export OTEL_ATTRIBUTE_VALUE_LENGTH_LIMIT=4095
```

### Remarks

- Make sure to use your [ingest license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)
- If your account is based in the EU, set the endpoint to: `https://otlp.eu01.nr-data.net:4317`

## Instrumentation

You will be collecting the metrics and traces from your application and forward them to your New Relic account. Start with creating a `otel.go` file in the [root directory](./) of your application.

### Meter Provider

In order to gather metrics, you will need to initialize a `Meter Provider` as follows in the `otel.go`:

```go
func newRelicTemporalitySelector(kind sdkmetric.InstrumentKind) metricdata.Temporality {
	if kind == sdkmetric.InstrumentKindUpDownCounter || kind == sdkmetric.InstrumentKindObservableUpDownCounter {
		return metricdata.CumulativeTemporality
	}
	return metricdata.DeltaTemporality
}

func newMetricProvider(
	ctx context.Context,
) *sdkmetric.MeterProvider {
	var exp sdkmetric.Exporter
	var err error

	exp, err = otlpmetricgrpc.New(
		ctx,
		otlpmetricgrpc.WithTemporalitySelector(newRelicTemporalitySelector),
	)
	if err != nil {
		panic(err)
	}

	mp := sdkmetric.NewMeterProvider(
		sdkmetric.WithReader(
			sdkmetric.NewPeriodicReader(
				exp,
				sdkmetric.WithInterval(2*time.Second),
			)))
	otel.SetMeterProvider(mp)
	return mp
}
```

The method `newMetricProvider` instantiates a new `Meter Provider` with the metric temporality of `delta` for all of the `cumulative` metrics except the `UpDownCounter` and `ObservableUpDownCounter`. The reason for that is that New Relic currently does not support cumulative but only delta metrics.

Moreover, the `Meter Provider` will be reading the metrics every `2` seconds per the option:

```go
...
sdkmetric.WithInterval(2*time.Second),
...
```

Next, you will need to add the following snippet to shutdown the `Meter Provider` gracefully:

```go
func shutdownMetricProvider(
	ctx context.Context,
	mp *sdkmetric.MeterProvider,
) {
	// Do not make the application hang when it is shutdown.
	ctx, cancel := context.WithTimeout(ctx, time.Second*5)
	defer cancel()
	if err := mp.Shutdown(ctx); err != nil {
		panic(err)
	}
}
```

You can start your `Meter Provider` in your `main.go` as follows:

```go
// Get context
ctx := context.Background()

// Create metric provider
mp := newMetricProvider(ctx)
defer shutdownMetricProvider(ctx, mp)
```

### Trace Provider

In order to track traces, you will need to initialize a `Trace Provider` as follows in the `otel.go`:

```go
func newTraceProvider(
	ctx context.Context,
) *sdktrace.TracerProvider {

	var exp sdktrace.SpanExporter
	var err error

	exp, err = otlptracegrpc.New(ctx)
	if err != nil {
		panic(err)
	}

	// Instantiate a default resource with environment variables
	r := resource.Default()

	// Create trace provider
	tp := sdktrace.NewTracerProvider(
		sdktrace.WithSampler(sdktrace.AlwaysSample()),
		sdktrace.WithBatcher(exp),
		sdktrace.WithResource(r),
	)

	// Set global trace provider
	otel.SetTracerProvider(tp)

	// Set trace propagator
	otel.SetTextMapPropagator(
		propagation.NewCompositeTextMapPropagator(
			propagation.TraceContext{},
			propagation.Baggage{},
		))

	return tp
}
```

The method `newTraceProvider` instantiates a new `Trace Provider` with:

- _sampler_ of `AlwaysSample`
- _batcher_ in order to flush the traces in batches

Next, you will need to add the following snippet to shutdown the `Trace Provider` gracefully:

```go
func shutdownTraceProvider(
	ctx context.Context,
	tp *sdktrace.TracerProvider,
) {
	// Do not make the application hang when it is shutdown.
	ctx, cancel := context.WithTimeout(ctx, time.Second*5)
	defer cancel()
	if err := tp.Shutdown(ctx); err != nil {
		panic(err)
	}
}
```

You can start your `Trace Provider` in your `main.go` as follows:

```go
// Create tracer provider
tp := newTraceProvider(ctx)
defer shutdownTraceProvider(ctx, tp)
```

### HTTP Wrapper

In order to separate your instrumentation code from your actual application code, you can prepare a wrapper around your HTTP handler. Go `net/http` package allows you to write custom wrappers by implementing the http interface.

Create a struct in your `otel.go` which will implement the `http.Handler` interface:

```go
type HttpWrapper struct {
	operation            string
	serverName           string
	handler              http.Handler
	httpServerDuration   metric.Float64Histogram
	fibonacciInvocations metric.Int64Counter
}
```

**Metrics**

Since you are willing to track your HTTP server latency and your Fibonacci invocations, add these metrics as struct objects in advance as `httpServerDuration` and `fibonacciInvocations`, respectively.

Write the following `NewHttpWrapper` method to initialize your wrapper:

```go
func NewHttpWrapper(
	handler http.Handler,
	operation string,
) http.Handler {

	// Get service name from environment variables
	serverName := os.Getenv("OTEL_SERVICE_NAME")

	// Create HTTP server duration histogram
	httpServerDuration, err := otel.GetMeterProvider().
		Meter(serverName).
		Float64Histogram("http.server.duration")
	if err != nil {
		log.Print(err.Error())
		panic(err.Error())
	}

	// Create Fibonacci invocation counter
	fibonacciInvocations, err := otel.GetMeterProvider().
		Meter(serverName).
		Int64Counter("fibonacci.invocations")
	if err != nil {
		log.Print(err.Error())
		panic(err.Error())
	}

	// Initialize custom HTTP handler wrapper
	w := HttpWrapper{
		serverName:           serverName,
		handler:              handler,
		operation:            operation,
		httpServerDuration:   httpServerDuration,
		fibonacciInvocations: fibonacciInvocations,
	}

	return &w
}
```

Here, you get & set your server name from the environment variable `OTEL_SERVICE_NAME` which you have defined earlier.

Then, you will get the `Meter Provider` which you have globally instantiated in the previous section and assign 2 metrics to track:

- `http.server.duration` (float64 histogram)
- `fibonacci.invocations` (int64 counter)

Now you need to create your own `ServeHTTP` method to successfully implement the HTTP interface. Since this method will act as if it is the handler, it will intercept the actual handler when a request is captured.

For starters, you can start your timer because the moment you enter to your wrapper function, the request has started:

```go
requestStartTime := time.Now()
```

Next, you can define some attributes which you can attach to your metrics so that they contain meaningful context:

```go
// Set up metric attributes
httpServerMetricAttributes := httpconv.ServerRequest(h.serverName, r)
fibonacciInvocationMetricAttributes := []attribute.KeyValue{}
```

The snippet above initializes an array of key value pairs for the your `fibonacci.invocations` metric and uses the `httpconv.ServerRequest` to enrich the attributes for the `http.server.duration` metric (like `http.flavor`, `http.scheme`, `http.method` etc.) per extracting information out of the HTTP request object.

As the request ends, you can record the metrics:

```go
// Use floating point division here for higher precision (instead of Millisecond method).
elapsedTime := float64(time.Since(requestStartTime)) / float64(time.Millisecond)

h.fibonacciInvocations.Add(ctx, 1, metric.WithAttributes(fibonacciInvocationMetricAttributes...))
h.httpServerDuration.Record(ctx, elapsedTime, metric.WithAttributes(httpServerMetricAttributes...))
```

Now, you need to call the actual handler which will execute the application code. You can directly call it like this:

```go
h.handler.ServeHTTP(w, r)
```

but you wouldn't be able to extract the end result of the call (HTTP status code). To that, you can create another wrapper and implement the `http.ResponseWriter` and customize the `WriteHeader` method:

```go
// Wrapper for response writer in order to retrieve the status code of the HTTP call
type responseWriterWrapper struct {
	http.ResponseWriter
	statusCode int
}

// Initialize HTTP response writer wrapper
func NewResponseWriterWrapper(w http.ResponseWriter) *responseWriterWrapper {
	return &responseWriterWrapper{w, http.StatusOK}
}

// Wrapper method to intercept & store the HTTP status code
func (rww *responseWriterWrapper) WriteHeader(code int) {
	rww.statusCode = code
	rww.ResponseWriter.WriteHeader(code)
}
```

Now you can pass your custom `http.ResponseWriter` to the actual handler instead of the default one:

```go
h.handler.ServeHTTP(rww, r)
```

This way, you will be able to extract the HTTP status code from the execution of your application code and put it as another attribute to your metrics:

```go
// Add status code to metric attributes
httpServerMetricAttributes = append(
  httpServerMetricAttributes,
  semconv.HTTPStatusCode(rww.statusCode),
)
if rww.statusCode == 200 {
  fibonacciInvocationMetricAttributes = append(
    fibonacciInvocationMetricAttributes,
    attribute.Bool("fibonacci.valid.n", true),
  )
} else {
  fibonacciInvocationMetricAttributes = append(
    fibonacciInvocationMetricAttributes,
    attribute.Bool("fibonacci.valid.n", false),
  )
}
```

**Traces**

You are now successfully collecting & forwarding your metrics to New Relic. Now, you need traces. Within your Go application, the trace context is propagated per the `context.Context`. So you can start by getting it out of the request:

```go
// Get context from request
ctx := r.Context()
```

Next, you can enrich your span attributes with the information out of the request object itself:

```go
// Set up trace attributes
startSpanAttributes := []trace.SpanStartOption{
  trace.WithSpanKind(trace.SpanKindServer),
  trace.WithAttributes(httpconv.ServerRequest(h.serverName, r)...),
  trace.WithAttributes(semconv.NetHostName(h.serverName)),
}
```

Since, this is the entrypoint to your server, the `span.kind` is to set as `server`.

Then, you can start your span:

```go
// Start the server span
ctx, span := otel.GetTracerProvider().
  Tracer("Fibonacci").
  Start(ctx, r.Method+" /fibonacci", startSpanAttributes...)
defer span.End()
```

where you refer to the `Trace Provider` which you have instantiated globally in the previous section.

As mentioned before, the context should be passed to your application code! That's why, you need to add this to your call to HTTP handler:

```go
h.handler.ServeHTTP(rww, r.WithContext(ctx))
```

Just like you have done with metrics, you can also add the HTTP status code to your server span:

```go
// Add status code to span attributes
endSpanAttributes := []attribute.KeyValue{semconv.HTTPStatusCode(rww.statusCode)}
span.SetAttributes(endSpanAttributes...)
```

Finally, you need to wrap your HTTP handler with your brand new custom wrapper in `main.go`:

```go
http.Handle("/fibonacci", NewHttpWrapper(http.HandlerFunc(handler), "fibonacci"))
```

Now, you can extend your instrumentation to your application code which is the Fibonacci calculation (method `calculateFibonacci`in `app.go`). Since your calculation is an inner step within the entire server request, it corresponds to a `span.kind` of `internal`:

```go
// Start an internal child span for Fibonacci calculation
_, span := trace.SpanFromContext(r.Context()).
  TracerProvider().
  Tracer("Fibonacci").
  Start(
    r.Context(),
    "fibonacci",
    trace.WithSpanKind(trace.SpanKindInternal),
  )
defer span.End()
```

You can set the given input for your calculation as an attribute to your inner span:

```go
fibonacciSpanAttrs := []attribute.KeyValue{
  attribute.Int64("fibonacci.n", n),
}
```

Since you allow only values from 1 to 90 as input, you can enrich your span with some errors in case the condition is violated:

```go
// Check input
if n <= 1 || n > 90 {
  log.Print(INPUT_IS_OUTSIDE_OF_RANGE)

  // Set error span attributes
  fibonacciSpanAttrs = append(fibonacciSpanAttrs,
    semconv.OtelStatusCodeError,
    semconv.OtelStatusDescriptionKey.String(INPUT_IS_OUTSIDE_OF_RANGE),
  )
  span.SetAttributes(fibonacciSpanAttrs...)

  return 0, errors.New("invalid input")
}
```

Lastly, if everything goes well, you can put the result of your calculation also as an attribute:

```go
// Set calculation result into span
fibonacciSpanAttrs = append(fibonacciSpanAttrs,
  attribute.Int64("fibonacci.result", res),
)
span.SetAttributes(fibonacciSpanAttrs...)
```

You are now good to go!

## Running the demo application

Run the following command:

```go
go run *.go
```

## Generate traffic

Run the following command in a new terminal tab:

```shell
bash ./load-generator.sh
```

## Clean up

To shut down the program, run the following in both shells or terminal tabs: `ctrl + c`.
