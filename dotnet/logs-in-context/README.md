# Logs In Context

## Introduction

This project contains an aspnetcore application which has some built-in logging while the application starts up and while requests are being processed that rely on the Microsoft.Extensions.Logging framework. This sample application can run in two different modes:

1. Using Serilog to manage, enrich, and export your logs.
2. Using OpenTelemetry to manage, enrich, and export your logs.

These modes can be toggled in the sample application by specifying either `1` or `2` respectively in the environment variable `LOGGING_SCENARIO`.

In both scenarios the current `TraceId` and `SpanId` are added to the log messages if they are available. The log messages are written to the `STDOUT` as structured JSON log messages, with one JSON object per line.

```json
{
  "timestamp": "2021-07-07T00:07:41.6405703Z",
  "log.level": "Information",
  "message": "Executed endpoint \u0027logs_in_context.Controllers.WeatherForecastController.Get (logs-in-context)\u0027",
  "trace.id": "381eab3a136f72479bcc2f68c8ee08e8",
  "span.id": "48650fdc8795274d",
  "ConnectionId": "0HMA0RCUFT4QE",
  "RequestId": "0HMA0RCUFT4QE:00000002",
  "RequestPath": "/WeatherForecast"
}
```

When running the application using the provided [docker-compose.yaml](./docker-compose.yaml), the log messages will be forwarded to a local OpenTelemetry Collector, and then the collector will forward the trace and log data to New Relic.

## Using Serilog

[LogBootstrapper.cs](./LogBootstrapper.cs) programmatically configures Serilog to integrate with Microsoft.Extensions.Logging and aspnetcore and to enable the custom [OTelEnricher](./OTelEnricher.cs) and [OTelFormatter](./OTelFormatter.cs) which will inject the current trace context (`span.id` and `trace.id`) and format the output as JSON respectively.

## Using OpenTelemetry

[LogBootstrapper.cs](/.LogBootstrapper.cs) programmatically configures OpenTelemetry to capture and include trace context on each log message. A custom OpenTelemetry log exporter is defined in [OTelLogExporter.cs](./OTelLogExporter.cs) which is used to export the log messages as JSON objects to `STDOUT`.

## Run

The application runs with Docker, and the [docker-compose.yaml](./docker-compose.yaml) is configured to use the [Fluentd logging driver](https://docs.docker.com/config/containers/logging/fluentd/) to forward logs to an [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/) running as an agent next to the service configured to receive Fluentd logs and forward them to New Relic.

Similar example using FluentBit:

![](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/logs/img/app-to-file-logs-fb.png?raw=true)

Run the application with docker compose:

```shell
docker-compose up --build
```

Exercise logs in context by calling the `GET /WeatherForecast`, which generates log messages inside the context of a trace:

```shell
curl http://localhost:8080/WeatherForecast
```

You should be able to see a mix of trace and log data flowing through the collector. If you navigate to the distributed traces of the application in [New Relic One](https://one.newrelic.com/), you should be able to find traces related to the call to `GET /WeatherForecast`, and see the logs in context:
