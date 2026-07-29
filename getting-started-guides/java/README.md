# Getting Started Guide - Java

This is a simple application instrumented
with [OpenTelemetry Java's](https://github.com/open-telemetry/opentelemetry-java) [automatic instrumentation javaagent](https://opentelemetry.io/docs/languages/java/automatic/).
It demonstrates how to configure OpenTelemetry Java to send data to New Relic.

This branch additionally uses the app's built-in error paths to explore how New
Relic's Errors Inbox groups OpenTelemetry errors by default, and how the
`error.group.name` / `error.group.message` span attributes can be used to
override that grouping. See [Exploring error grouping](#exploring-error-grouping)
below.

## Requirements

* Java JDK 21+, due to the use of Spring Boot 4; [Java 8+ otherwise](https://github.com/open-telemetry/opentelemetry-java/blob/main/VERSIONING.md#language-version-compatibility)
* [A New Relic account](https://one.newrelic.com/)
* [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Running the application

1. Set the following environment variables to configure OpenTelemetry to send
   data to New Relic:

    ```shell
    export OTEL_SERVICE_NAME=getting-started-java
    export OTEL_EXPERIMENTAL_EXPORTER_OTLP_RETRY_ENABLED=true
    export OTEL_EXPORTER_OTLP_METRICS_DEFAULT_HISTOGRAM_AGGREGATION=BASE2_EXPONENTIAL_BUCKET_HISTOGRAM
    export OTEL_EXPERIMENTAL_RESOURCE_DISABLED_KEYS=process.command_args
    export OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net
    export OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_license_key>
    export OTEL_ATTRIBUTE_VALUE_LENGTH_LIMIT=4095
    export OTEL_EXPORTER_OTLP_COMPRESSION=gzip
    export OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
    export OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE=delta
    ```

    * If your account is based in the EU, set the endpoint to: [https://otlp.eu01.nr-data.net](https://otlp.eu01.nr-data.net)

2. Run the application with the following command and open
   [http://localhost:8080/fibonacci?n=1](http://localhost:8080/fibonacci?n=1)
   in your web browser to ensure it is working.

    ```shell
    ./gradlew bootRun
    ```

3. Experiment with providing different values for `n` in the query string.
   Valid values are between 1 and 90. Values outside this range cause an error
   which will show up in New Relic.

## Exploring error grouping

With the application running (see above), the `/fibonacci` endpoint has three
built-in ways to produce an `ERROR`-status span, each with a different status
description:

```shell
# Out-of-range n -> IllegalArgumentException
curl "http://localhost:8080/fibonacci?n=999"

# Missing n param -> MissingServletRequestParameterException
curl "http://localhost:8080/fibonacci"

# Wrong HTTP method -> HttpRequestMethodNotSupportedException
curl -X POST "http://localhost:8080/fibonacci?n=10"
```

`Controller.java`'s `@ControllerAdvice` exception handler additionally sets the
`error.group.name` and, in some branches, `error.group.message` span attributes
to override New Relic's default error grouping. You can inspect the resulting
fields with NRQL:

```sql
SELECT name, error.group.name, error.group.message, span.kind, otel.status_description
FROM Span
WHERE otel.status_code = 'ERROR' AND service.name = 'getting-started-java'
SINCE 30 minutes ago
```

### Observations

| Test case | Request | Override applied | Current behavior | Desired behavior |
|---|---|---|---|---|
| Default grouping, out-of-range `n` | `GET /fibonacci?n=999` and `GET /fibonacci?n=0` | none | Both collapse into **one** group (`GET /fibonacci` / `n must be 1 <= n <= 90.`) — the message has no volatile tokens for New Relic to normalize away | Same (working as intended) |
| Default grouping, missing `n` | `GET /fibonacci` | none | Separate group: same span name (`GET /fibonacci`), different message | Same (working as intended) |
| Default grouping, wrong method | `POST /fibonacci?n=10` | none | Own group: `POST /*` | Same (working as intended) |
| Custom **name** only | `GET /fibonacci?n=10` (POST) | `error.group.name` set; `error.group.message` left unset | `error.group.name` is honored verbatim (not normalized); `error.group.message` comes back **`null`** — no fallback to the span's real `otel.status_description` | `error.group.message` should fall back to the span's real message when not explicitly overridden |
| Custom **message** only | `GET /fibonacci` (missing `n`) | `error.group.message` set; `error.group.name` left unset | The override is **silently ignored entirely** — both `error.group.name` and `error.group.message` revert to the fully-default derivation, as if neither attribute existed | `error.group.message` should still take effect, using the default span-name-derived group name |
| Custom **name + message** | `GET /fibonacci?n=999` vs `GET /fibonacci?n=1000` | both set, message embeds the raw `n` | Both fields honored verbatim; the two requests land in **distinct** groups despite sharing an identical default message (`n must be 1 <= n <= 90.`) | Same (working as intended — this is the one case that behaves correctly today) |

The upshot: `error.group.name` and `error.group.message` are not independent
overrides today. Setting one without the other doesn't give you "default value
for the field you didn't touch" — it either drops the message (name-only) or
drops your override outright (message-only). This mirrors a known issue on the
APM side, where the `set_error_group_callback` agent APIs (Ruby/Python/PHP)
have no message parameter at all, so setting a custom group name there always
blanks the message in the Errors Inbox list view (see New Relic Support:
["Using a custom error group removes the error message"](https://support.newrelic.com/s/hubtopic/aAXPh00000023wHOAQ/using-a-custom-error-group-removes-the-error-message)).
