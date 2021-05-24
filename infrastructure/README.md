# OpenTelemetry infrastructure monitoring

## Introduction

This project demonstrates how the
[OpenTelemetry collector](https://github.com/open-telemetry/opentelemetry-collector)
can be used to monitor components of your infrastructure.

The example requires that you run Docker and have Docker Compose installed.

When run, the following components are started:
* An ASP.NET Core application that exercises some basic Redis functionality and
  is instrumented with
  [OpenTelemetry .NET](https://github.com/open-telemetry/opentelemetry-dotnet).
  The fact that it is a .NET application is not necessarily relevant to this
  example.
* A Redis instance which is monitored by the infrastructure agent collector and
  used by the .NET application.
* An instance of the OpenTelemetry collector
  [deployed as an agent](https://opentelemetry.io/docs/collector/getting-started/#agent).
  This collector that will monitor your infrastructure. See
  [otel-agent-infrastructure-config.yaml](./otel-agent-infrastructure-config.yaml).
  It uses the
  [hostmetricsreceiver](https://github.com/open-telemetry/opentelemetry-collector/tree/main/receiver/hostmetricsreceiver)
  and
  [redisreceiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/redisreceiver)
  to generate metrics for both the host (CPU, memory, disk) and Redis instance.
* An instance of the OpenTelemetry collector
  [deployed as a gateway](https://opentelemetry.io/docs/collector/getting-started/#gateway).
  This collector acts as the central collector that the .NET application and
  the infrastructure agent collector exports data to. See
  [otel-collector-config.yaml](./otel-collector-config.yaml).

## Run

To run the example:

```shell
docker-compose up --build
```

Exercise the .NET application as follows:

```shell
# This endpoint performs some Redis operations.
curl http://localhost:5001/WeatherForecast
```