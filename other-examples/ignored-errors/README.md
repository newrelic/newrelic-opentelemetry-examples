# Ignoring Errors Using the OpenTelemetry Collector

This example demonstrates how to prevent specific HTTP errors from appearing in New Relic's Errors Inbox while still sending the telemetry data to New Relic for visibility. This is useful for filtering out expected errors that should not trigger alerts or inflate error rates.

This example uses a Java Spring Boot application with OpenTelemetry auto-instrumentation and shows how to configure the OpenTelemetry Collector to transform error spans and metrics based on HTTP status codes.

## Background

In production applications, not all errors are equal. Some HTTP errors are expected and shouldn't be treated as problems:

- **500 Internal Server Error** - In some architectures, these might be expected for certain operations (e.g., controlled failures in chaos engineering, expected database constraint violations)

However, by default, OpenTelemetry instrumentation marks all HTTP error status codes as errors, which:
- Inflates your error rate metrics
- Clutters your Errors Inbox with noise
- Can trigger false alerts
- Makes it harder to identify real issues

This example shows how to use the OpenTelemetry Collector's **transform processor** to selectively mark certain errors as "expected" so they don't contribute to error counts while still preserving the telemetry data for analysis.

## Requirements

- Docker and Docker Compose
- [A New Relic account](https://one.newrelic.com/)
- [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Set Up and Running

1. **Set your New Relic API key** in [.env](./.env):

   ```bash
   NEW_RELIC_API_KEY=<your_license_key>
   ```

   For EU accounts, also update the endpoint:
   ```bash
   NEW_RELIC_OTLP_ENDPOINT=https://otlp.eu01.nr-data.net
   ```

2. **Start the example**:

   ```bash
   docker compose up --build
   ```

   The Spring Boot application will start on port `8080`. Visit http://localhost:8080 to view running application.

3. **Test the endpoint**:

   The application exposes a single dynamic endpoint that returns any HTTP status code you specify:

   ```bash
   # Success - will NOT appear as error
   curl http://localhost:8080/httpStatusCode/200

   # Various error status codes (only 500 is filtered):
   curl http://localhost:8080/httpStatusCode/500  # Internal Server Error (FILTERED)
   curl http://localhost:8080/httpStatusCode/503  # Service Unavailable (not filtered)
   ```

4. **View your data in New Relic**:

   - Navigate to "All Entities → Services - OpenTelemetry"
   - Find the `ignored-errors` service
   - Explore traces and metrics
   - Notice that 500 errors appear in traces but NOT in the Errors Inbox

5. **Stop the example**:

   ```bash
   docker compose down
   ```

## How It Works

The OpenTelemetry Collector uses the **transform processor** to modify telemetry data before exporting it to New Relic.

### Trace Transformation

The collector transforms error spans based on HTTP status code (from [otel-config.yaml](./otel-config.yaml)):

```yaml
transform/traces:
  error_mode: ignore
  trace_statements:
    - context: span
      conditions:
        # Match spans from this service
        - resource.attributes["service.name"] == "ignored-errors"
          # With HTTP 500 status code
          and attributes["http.response.status_code"] == 500
          # That are server spans (span.kind == 2 is SERVER)
          and span.kind == 2
      statements:
        # Change status from ERROR to UNSET (not an error)
        - set(status.code, STATUS_CODE_UNSET)
        # Remove error.type attribute
        - delete_key(attributes, "error.type")
```

### Metric Transformation

The collector also transforms metrics to prevent error metrics from being recorded:

```yaml
transform/metrics:
  error_mode: ignore
  metric_statements:
    - context: datapoint
      conditions:
        - resource.attributes["service.name"] == "ignored-errors"
          and metric.name == "http.server.request.duration"
          and attributes["http.response.status_code"] == 500
      statements:
        - delete_key(attributes, "error.type")
```

## Additional Resources

- [OpenTelemetry Transform Processor](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/transformprocessor)
- [OpenTelemetry Java Agent](https://github.com/open-telemetry/opentelemetry-java-instrumentation)
- [New Relic OTLP Endpoint](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/get-started/opentelemetry-set-up-your-app/)
- [OpenTelemetry Semantic Conventions for HTTP](https://opentelemetry.io/docs/specs/semconv/http/http-spans/)
- [Spring Boot with OpenTelemetry](https://opentelemetry.io/docs/languages/java/automatic/)
