# Getting Started Guide - Java

This is a simple application that adds manual instrumentation using the
[OpenTelemetry Java API and SDK](https://github.com/open-telemetry/opentelemetry-java)
and runs it with the [New Relic Java agent](https://github.com/newrelic/newrelic-java-agent)
in [hybrid mode](https://docs.newrelic.com/docs/opentelemetry/get-started/opentelemetry-hybrid-agent/).
In hybrid mode the New Relic agent instruments the OpenTelemetry SDK, so the
spans, metrics, and logs/events produced by the OpenTelemetry API in this example
are incorporated into the monitored New Relic APM entity alongside the agent's own
automatic instrumentation. The agent routes this telemetry to New Relic, so you do
not need to run an OpenTelemetry Collector — but the application must include the
OpenTelemetry SDK autoconfigure and OTLP exporter dependencies (see `build.gradle`)
and enable SDK autoconfiguration (set for you in `build.gradle` via
`-Dotel.java.global-autoconfigure.enabled=true`).

## Requirements

* Java JDK 21+, due to the use of Spring Boot 4; [Java 8+ otherwise](https://github.com/open-telemetry/opentelemetry-java/blob/main/VERSIONING.md#language-version-compatibility)
* [A New Relic account](https://one.newrelic.com/)
* [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Running the application

1. Set the following environment variables to configure the New Relic Java
   agent and enable hybrid (OpenTelemetry) mode:

    ```shell
    export NEW_RELIC_APP_NAME=getting-started-java
    export NEW_RELIC_LICENSE_KEY=<your_license_key>
    # Enable hybrid mode so the agent captures the OpenTelemetry API instrumentation.
    export NEW_RELIC_OPENTELEMETRY_ENABLED=true
    # Forward log context data so the event attributes (e.g. fibonacci.n) are sent with the events.
    export NEW_RELIC_APPLICATION_LOGGING_FORWARDING_CONTEXT_DATA_ENABLED=true
    ```

    * The agent automatically reports to the correct region (US or EU) based on
      your license key, so no endpoint configuration is needed.

2. Run the application with the following command and open
   [http://localhost:8080/fibonacci?n=1](http://localhost:8080/fibonacci?n=1)
   in your web browser to ensure it is working.

    ```shell
    ./gradlew bootRun
    ```

3. Experiment with providing different values for `n` in the query string.
   Valid values are between 1 and 90. Values outside this range cause an error
   which will show up in New Relic.

   The app also emits events via the OpenTelemetry Java logs/events API:
   `application.started` on startup, `fibonacci.computed` on each successful
   computation (with the `fibonacci.n` and `fibonacci.result` attributes), and
   `fibonacci.invalid_input` when `n` is out of range.

   In hybrid mode the New Relic agent forwards these as New Relic log events: the
   event name becomes the log `message`, and the event attributes are forwarded as
   `context.*` attributes (which is why context-data forwarding is enabled above).
   Query them in NRQL by message:

    ```sql
    SELECT * FROM Log WHERE message = 'fibonacci.computed'
    ```

   or by the event-type attribute each event carries
   (`context.newrelic.event.type`, the PascalCased event name):

    ```sql
    SELECT * FROM Log WHERE `context.newrelic.event.type` = 'FibonacciComputed'
    ```

   Note: unlike OTLP ingestion, the hybrid agent does not turn these into custom
   event types — `newrelic.event.type` is forwarded as a plain log attribute.
