# OpenTelemetry SDK New Relic Config

## Introduction

This project demonstrates a simple Java application with custom OpenTelemetry instrumentation configured to write data to New Relic. New Relic expects metric data to delta aggregation temporality, whereas the default for OpenTelemetry is cumulative.

## Run

The application is configured to export data via OTLP to a collector running at `http://localhost:4317`. This can be changed by specifying an alternative via `OTLP_HOST` environment variable:
```shell
export OTLP_HOST=http://my-collector-host:4317
```

You can adjust where data is exported to, or you can run a collector instance locally via docker by following the [nr-otlp-export](../../collector/nr-otlp-export/README.md) example.

After running the collector, run the application from a shell in the [java root](../) via:
```
./gradlew sdk-nr-config:bootRun
```

The application exposes a simple endpoint at `http://localhost:8080/ping`.

Invoke it via: `curl http://localhost:8080/ping` to generate trace and metric data.

Check your collector logs to confirm data is flowing.