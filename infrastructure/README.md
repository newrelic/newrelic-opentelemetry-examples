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

## What you'll see

The
[hostmetricsreceiver](https://github.com/open-telemetry/opentelemetry-collector/tree/main/receiver/hostmetricsreceiver)
will generate metrics like the following:

```shell
Metric #0
Descriptor:
     -> Name: system.cpu.time
     -> Description: Total CPU seconds broken down by different states.
     -> Unit: s
     -> DataType: DoubleSum
     -> IsMonotonic: true
     -> AggregationTemporality: AGGREGATION_TEMPORALITY_CUMULATIVE
DoubleDataPoints #0
Data point labels:
     -> cpu: cpu0
     -> state: user
StartTimestamp: 2021-05-24 11:16:30 +0000 UTC
Timestamp: 2021-05-24 21:31:10.326404 +0000 UTC
Value: 99.770000
DoubleDataPoints #1
Data point labels:
     -> cpu: cpu0
     -> state: system
StartTimestamp: 2021-05-24 11:16:30 +0000 UTC
Timestamp: 2021-05-24 21:31:10.326404 +0000 UTC
Value: 228.670000
DoubleDataPoints #2
Data point labels:
     -> cpu: cpu0
     -> state: idle
StartTimestamp: 2021-05-24 11:16:30 +0000 UTC
Timestamp: 2021-05-24 21:31:10.326404 +0000 UTC
Value: 36061.290000
DoubleDataPoints #3
Data point labels:
     -> cpu: cpu0
     -> state: interrupt
StartTimestamp: 2021-05-24 11:16:30 +0000 UTC
Timestamp: 2021-05-24 21:31:10.326404 +0000 UTC
Value: 0.000000
DoubleDataPoints #4
Data point labels:
     -> cpu: cpu0
     -> state: nice
StartTimestamp: 2021-05-24 11:16:30 +0000 UTC
Timestamp: 2021-05-24 21:31:10.326404 +0000 UTC
Value: 0.000000
DoubleDataPoints #5
Data point labels:
     -> cpu: cpu0
     -> state: softirq
StartTimestamp: 2021-05-24 11:16:30 +0000 UTC
Timestamp: 2021-05-24 21:31:10.326404 +0000 UTC
Value: 8.180000
DoubleDataPoints #6
Data point labels:
     -> cpu: cpu0
     -> state: steal
StartTimestamp: 2021-05-24 11:16:30 +0000 UTC
Timestamp: 2021-05-24 21:31:10.326404 +0000 UTC
Value: 0.000000
DoubleDataPoints #7
Data point labels:
     -> cpu: cpu0
     -> state: wait
StartTimestamp: 2021-05-24 11:16:30 +0000 UTC
Timestamp: 2021-05-24 21:31:10.326404 +0000 UTC
Value: 5.190000
Metric #1
Descriptor:
     -> Name: system.memory.usage
     -> Description: Bytes of memory in use.
     -> Unit: By
     -> DataType: IntSum
     -> IsMonotonic: false
     -> AggregationTemporality: AGGREGATION_TEMPORALITY_CUMULATIVE
IntDataPoints #0
Data point labels:
     -> state: used
StartTimestamp: 1970-01-01 00:00:00 +0000 UTC
Timestamp: 2021-05-24 21:31:10.3271517 +0000 UTC
Value: 683798528
IntDataPoints #1
Data point labels:
     -> state: free
StartTimestamp: 1970-01-01 00:00:00 +0000 UTC
Timestamp: 2021-05-24 21:31:10.3271517 +0000 UTC
Value: 3474579456
IntDataPoints #2
Data point labels:
     -> state: buffered
StartTimestamp: 1970-01-01 00:00:00 +0000 UTC
Timestamp: 2021-05-24 21:31:10.3271517 +0000 UTC
Value: 359395328
IntDataPoints #3
Data point labels:
     -> state: cached
StartTimestamp: 1970-01-01 00:00:00 +0000 UTC
Timestamp: 2021-05-24 21:31:10.3271517 +0000 UTC
Value: 1717616640
IntDataPoints #4
Data point labels:
     -> state: slab_reclaimable
StartTimestamp: 1970-01-01 00:00:00 +0000 UTC
Timestamp: 2021-05-24 21:31:10.3271517 +0000 UTC
Value: 390475776
IntDataPoints #5
Data point labels:
     -> state: slab_unreclaimable
StartTimestamp: 1970-01-01 00:00:00 +0000 UTC
Timestamp: 2021-05-24 21:31:10.3271517 +0000 UTC
Value: 46325760
```

The
[redisreceiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/redisreceiver)
will generate metrics like the following:

```shell
Metric #3
Descriptor:
     -> Name: redis/cpu/time
     -> Description: User CPU consumed by the Redis server in seconds since server start
     -> Unit: s
     -> DataType: DoubleSum
     -> IsMonotonic: true
     -> AggregationTemporality: AGGREGATION_TEMPORALITY_CUMULATIVE
DoubleDataPoints #0
Data point labels:
     -> state: user
StartTimestamp: 2021-05-24 21:29:47.4007291 +0000 UTC
Timestamp: 2021-05-24 21:31:10.4007291 +0000 UTC
Value: 0.102638
Metric #4
Descriptor:
     -> Name: redis/clients/connected
     -> Description: Number of client connections (excluding connections from replicas)
     -> Unit: 
     -> DataType: IntSum
     -> IsMonotonic: false
     -> AggregationTemporality: AGGREGATION_TEMPORALITY_CUMULATIVE
IntDataPoints #0
StartTimestamp: 2021-05-24 21:29:47.4007291 +0000 UTC
Timestamp: 2021-05-24 21:31:10.4007291 +0000 UTC
Value: 3
Metric #5
Descriptor:
     -> Name: redis/clients/max_input_buffer
     -> Description: Biggest input buffer among current client connections
     -> Unit: 
     -> DataType: IntGauge
IntDataPoints #0
StartTimestamp: 1970-01-01 00:00:00 +0000 UTC
Timestamp: 2021-05-24 21:31:10.4007291 +0000 UTC
Value: 24
```