# Micrometer Shim Example

This is a modified version of the example from [opentelemetry-java-examples/micrometer-shim](https://github.com/open-telemetry/opentelemetry-java-examples/tree/main/micrometer-shim), configured to export data to New Relic via OTLP. 

This example demonstrates a typical use case
of [micrometer shim](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/micrometer/micrometer-1.5/library).

It consists of a spring boot application with:

- A simple web API available at `GET http://localhost:8080/ping`
- Instrumented with the spring boot actuator and micrometer
- Micrometer metrics bridged to OpenTelemetry using the micrometer shim
- OpenTelemetry metrics exported to New Relic via OTLP

# How to run

Run the application from a shell in the [java root](../) via:

```shell
# Set New Relic license key as environment variable
export NEW_RELIC_LICENSE_KEY=<your_license_key>

./gradlew micrometer-shim:bootRun
```

Exercise the application by calling its endpoint

```shell
curl http://localhost:8080/ping
```

Check New Relic to confirm data is flowing.
