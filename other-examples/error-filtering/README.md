# Error Filtering with OpenTelemetry Collector

This example demonstrates how to transform error telemetry (traces and metrics) using the OpenTelemetry Collector so that expected/ignorable errors can be prevented from contributing to the error count and errors inbox without fully filtering it out before sending data to New Relic.

This example includes an HTTP server with multiple endpoints that demonstrate various error types, and a collector configuration that shows different filtering strategies.

## Use Cases

This example demonstrates several common error filtering patterns:

- **Filter noisy errors**: Remove validation errors (400) and auth errors (401) that clutter dashboards
- **Add metadata**: Tag filtered telemetry for tracking what was processed

## Requirements

- Docker and Docker Compose
- [A New Relic account](https://one.newrelic.com/)
- [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Quick Start

1. **Set your New Relic API key** in [.env](./.env):

   ```bash
   NEW_RELIC_API_KEY=<your_license_key>
   ```

2. **Start the example**:

   ```bash
   docker compose up --build
   ```

   The HTTP server will start on port 8080. You can test the endpoints using curl or your browser:

   ```bash
   # View available endpoints
   curl http://localhost:8080/

   # Test specific error types
   curl http://localhost:8080/success
   curl http://localhost:8080/validation-error
   curl http://localhost:8080/auth-error
   curl http://localhost:8080/server-error
   curl http://localhost:8080/timeout
   curl http://localhost:8080/network-error
   curl http://localhost:8080/random
   ```

3. **View your data in New Relic**:

   - Navigate to "New Relic -> All Entities -> Services - OpenTelemetry"
   - Find the `error-generator` service
   - Explore traces and metrics

4. **Stop the example**:

   ```bash
   docker compose down
   ```

## Configuration

All configuration is managed through environment variables in [.env](./.env).

### Error Generator Configuration

The error generator is an HTTP server with multiple endpoints. By default, it only responds to external requests:

```
# Enable automatic background traffic generation (optional)
# When enabled, the service continuously makes requests to its own endpoints
# Set to "true" to enable automatic traffic, "false" to require manual requests
AUTO_GENERATE_TRAFFIC=false

# Interval between auto-generated requests in milliseconds
# Only applies when AUTO_GENERATE_TRAFFIC=true
REQUEST_INTERVAL_MS=5000
```

**When to use AUTO_GENERATE_TRAFFIC:**

- **`false` (default)** - Manual control mode
  - Service waits for your requests (via curl or browser)
  - Best for: learning the endpoints, testing specific error types, controlled demos

- **`true`** - Automatic traffic mode
  - Service continuously generates its own traffic in the background
  - Hits random endpoints automatically every `REQUEST_INTERVAL_MS`
  - Best for: long-running demos, populating dashboards, hands-off testing

### Available Endpoints

The error generator HTTP server exposes the following endpoints:

| Endpoint | Description | HTTP Status |
|----------|-------------|-------------|
| `/` | Service information and endpoint list | 200 |
| `/health` | Health check endpoint | 200 |
| `/success` | Always returns success | 200 |
| `/validation-error` | Validation error scenario | 400 |
| `/auth-error` | Authentication error scenario | 401 |
| `/server-error` | Internal server error with nested database span | 500 |
| `/timeout` | Gateway timeout with nested upstream service span | 504 |
| `/network-error` | Network unavailability scenario | 503 |
| `/random` | Randomly returns one of the above responses | varies |

## Error Types

The error generator produces five types of errors:

| Error Type | HTTP Status | Log Severity | Description |
|------------|-------------|--------------|-------------|
| timeout | 504 | ERROR | Gateway timeout - upstream service unresponsive |
| validation | 400 | WARN | Invalid input parameters |
| database | 500 | ERROR | Database connection issues |
| network | 503 | ERROR | Downstream service unavailable |
| auth | 401 | WARN | Invalid or expired authentication |

Each error produces:
- A **trace span** with error status and attributes
- An **error metric** counter increment

## Filtering Strategies

The collector configuration ([otel-config.yaml](./otel-config.yaml)) demonstrates several filtering patterns:

### Trace Filtering

Filters error spans based on error type, span status code, and http response status code for http spans:

```yaml
  transform/traces:
    error_mode: ignore
    trace_statements:
      - context: span
        conditions:
          # Remove validation error spans
          - 'attributes["error.type"] == "validation"
          - 'span.status.code == "Error"'
          - 'attributes["http.response.status_code"] == 504'
        statements:
          - ...
```

### Metric Filtering

Filters error metrics by error type:

```yaml
  transform/metrics:
    error_mode: ignore
    metric_statements:
      - context: metric
        conditions:
          - 'name == "errors.total" and attributes["error.type"] == "validation"'
        statements:
          - ...
```

### Compare Filtered vs Unfiltered

To see the impact of filtering, run the example twice:

1. First with all filters disabled (`FILTER_*=false`)
2. Then with filters enabled (`FILTER_*=true`)

Compare the data volume and types in New Relic to see what was filtered out.

## Additional Resources

- [OpenTelemetry Transform Processor](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/transformprocessor)
- [New Relic OTLP Endpoint](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/get-started/opentelemetry-set-up-your-app/)
- [OpenTelemetry Collector Configuration](https://opentelemetry.io/docs/collector/configuration/)

## Extending This Example

### Add Custom Error Types

Edit `error-generator/main.go` and add new error types to the `errorTypes` map:

```go
var errorTypes = map[string]ErrorType{
    "custom": {
        Name:       "custom",
        HTTPStatus: 500,
        Severity:   zapcore.ErrorLevel,
        Message:    "Custom error message",
    },
}
```

### Add Custom Filters

Edit `otel-config.yaml` and add new filter conditions:

```yaml
filter/traces:
  traces:
    span:
      - 'attributes["http.status_code"] > 499'  # Filter all 5xx errors
```

### Export to Multiple Backends

Add additional exporters in `otel-config.yaml`:

```yaml
exporters:
  otlphttp/newrelic:
    endpoint: ${env:NEW_RELIC_OTLP_ENDPOINT}
    # ...

  otlphttp/other:
    endpoint: https://other-backend.example.com
    # ...

service:
  pipelines:
    traces:
      exporters: [otlphttp/newrelic, otlphttp/other]
```