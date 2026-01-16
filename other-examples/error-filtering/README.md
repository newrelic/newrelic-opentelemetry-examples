# Error Filtering with OpenTelemetry Collector

This example demonstrates how to transform error telemetry (traces, logs, and metrics) using the OpenTelemetry Collector so that expected/ignorable errors can be prevented from contributing to the error count without fully filtering it out before sending data to New Relic. 

This example includes a configurable error generator that produces various types of errors, and a collector configuration that shows different filtering strategies.

## Use Cases

This example demonstrates several common error filtering patterns:

- **Filter noisy errors**: Remove validation errors (400) and auth errors (401) that clutter dashboards
- **Filter by severity**: Only send ERROR-level logs, filtering out WARN logs
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

   You should see output showing:
   - Error generator starting with configured error rates
   - Collector receiving and processing telemetry
   - Filtered telemetry being exported to New Relic

   **Or use the interactive quick start:**

   ```bash
   ./scripts/quick-start.sh
   ```

   This provides an interactive menu to view live data in various ways.

3. **View your data in New Relic**:

   - Navigate to "New Relic -> All Entities -> Services - OpenTelemetry"
   - Find the `error-generator` service
   - Explore traces, logs, and metrics

4. **Stop the example**:

   ```bash
   docker compose down
   ```

## Configuration

All configuration is managed through environment variables in [.env](./.env).

### Error Generation Configuration

Control what errors are generated:

```
# Error rate (0.0 to 1.0)
ERROR_RATE=0.3

# Interval between requests (milliseconds)
REQUEST_INTERVAL_MS=2000

# Enable/disable specific error types
ENABLE_TIMEOUT_ERRORS=true          # 504 Gateway Timeout
ENABLE_VALIDATION_ERRORS=true       # 400 Bad Request
ENABLE_DATABASE_ERRORS=true         # 500 Internal Server Error
ENABLE_NETWORK_ERRORS=true          # 503 Service Unavailable
ENABLE_AUTH_ERRORS=true             # 401 Unauthorized
```

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
- A **log record** with appropriate severity and context
- An **error metric** counter increment

## Filtering Strategies

The collector configuration ([otel-config.yaml](./otel-config.yaml)) demonstrates several filtering patterns:

### 1. Trace Filtering

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

### 2. Log Filtering

Filters logs based on severity and error type:

```yaml
  transform/logs:
    error_mode: ignore
    log_statements:
      - context: log
        conditions:
          - 'severity_text == "WARN"'
          - 'attributes["error.type"] == "validation"'
        statements:
          - ...
```

### 3. Metric Filtering

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