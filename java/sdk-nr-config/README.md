# SDK New Relic Config

This project demonstrates a simple Java application with custom OpenTelemetry instrumentation configured to write data to New Relic. New Relic expects metric data to delta aggregation temporality, whereas the default for OpenTelemetry is cumulative.

The application is configured to export data via OTLP to a collector running at `http://localhost:4317`.

You can adjust where data is exported to, or you can run a collector instance locally via docker by following the [nr-exporter-docker](../../collector/nr-exporter-docker/README.md) example.

After running the collector, run the application from a shell in the [java root](../) via:
```
./gradlew sdk-nr-config:bootRun
```

The application exposes a simple endpoint at `http://localhost:8080/ping`.

Invoke it via: `curl http://localhost:8000/ping` to generate trace and metric data.

Check your collector logs to confirm data is flowing.