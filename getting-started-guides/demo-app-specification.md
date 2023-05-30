## Specification for the Getting Started Guide demo applications:

### Application
1. Must use port 8080

2. User must be able to access the endpoint http://localhost:8080/fibonacci?n=[input], and endpoint should return the following JSON response:
  * For valid input, `{"n":5,"result":5}`
  * For invalid input, `{"message":"n must be 1 <= n <= 90."}`

3. Must configure the OTLP exporter to send data to NR. Should use the standard environment variables for configuration rather than programmatic or other means:
  * `OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_license_key>`
  * `OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317`
  * `OTEL_SERVICE_NAME=getting-started-java`

4. Must accept input as `n`, with the valid input range as 1 <= n <= 90
5. The application must emit the following telemetry:
  * Traces
    * Root span
      * The root span may be emitted manually when the endpoint is invoked or preferrably by instrumentation if available in the language.
    * Child span
      * This span should represent the fibonacci calculation.
      * Span name = `fibonacci`
      * Span kind = internal
      * Attributes
        * `fibonacci.n`, with the value of `n` representing the user's input
        * `fibonacci.result`, with the value of `result` representing the result of the user’s input
      * Span event
        * When an error occurs, an exception event should be added as follows:
        * Span status = `ERROR`
        * Status description: `n must be 1 <= n <= 90.` 
  * Metrics
    * Counter named`fibonacci.invocations` 
        * Attributes
          * Counter description: `Measures the number of times the fibonacci method is invoked.`
          * `fibonacci.valid.n`, with a boolean value indicating whether `n` was valid or not
  * Logs
    * Output the following when `n` is valid: `Compute fibonacci(n) = result`
    * Output the following when `n` is invalid: `Failed to compute fibonacci(n)`

### File structure
1. A directory named `Instrumented` that includes the following:
  * README (spec to follow; for now, use [Getting Started Guide - Java](https://github.com/newrelic/newrelic-opentelemetry-examples/blob/main/getting-started-guides/java/instrumented/README.md) as an example)
  * Source code
  * Instrumentation files
  * Load generator files
    * `call-app.sh` and `load-generator.sh`
    * `call-app.ps1` and `load-generator.ps1` 
2. A directory named `Uninstrumented` that includes the following:
  * README (spec to follow)
  * Source code
  * Load generator files
    * `call-app.sh` and `load-generator.sh`
    * `call-app.ps1` and `load-generator.ps1` 