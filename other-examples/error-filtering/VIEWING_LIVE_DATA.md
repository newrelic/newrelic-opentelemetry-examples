# Viewing Live Telemetry Data

This guide shows you how to see telemetry data flowing through the collector in real-time.

## ⚠️ Important: This Example Uses Transformation

**This collector transforms telemetry, not filters it.**

- **All data is sent to New Relic** - No data is dropped
- **Filter metrics don't exist** - The `otelcol_processor_filter_*` metrics mentioned in some sections below won't be available
- **What to look for instead**: Compare received vs sent metrics - they should be the same
- **Transformation happens silently** - You'll see the status/severity changes in the debug output

Use the helper scripts in `./scripts/` which have been updated for the transformation approach.

## Quick Start - See Everything

The fastest way to see live data:

```bash
# 1. Start the services
docker compose up --build

# 2. In another terminal, watch collector output
docker compose logs -f collector
```

You'll see output like:
```
collector  | 2024-01-08T12:34:56.789Z info Traces {"kind": "exporter", "data_type": "traces", "name": "debug", "resource spans": 1, "spans": 1}
collector  | 2024-01-08T12:34:56.790Z info Logs {"kind": "exporter", "data_type": "logs", "name": "debug", "resource logs": 1, "log records": 1}
```

---

## Method 1: Enable Detailed Debug Output

### Step 1: Edit otel-config.yaml

Change the debug exporter to show full details:

```yaml
exporters:
  debug:
    verbosity: detailed  # Change from 'basic' to 'detailed'
    sampling_initial: 100  # Show first 100 items
    sampling_thereafter: 10  # Then show every 10th item
```

### Step 2: Restart the Collector

```bash
docker compose restart collector
```

### Step 3: Watch the Output

```bash
docker compose logs -f collector
```

You'll now see the full JSON structure of each trace, log, and metric!

**Example Output:**

```json
Trace #0
Resource SchemaURL:
Resource attributes:
     -> service.name: Str(error-generator)
     -> service.version: Str(1.0.0)
ScopeSpans #0
Span #0
    Trace ID       : 8c3e5a2b9f1d4e7a6b2c8d9e0f1a2b3c
    Parent ID      :
    ID             : a1b2c3d4e5f67890
    Name           : handle_request
    Kind           : Internal
    Start time     : 2024-01-08 12:34:56.100 +0000 UTC
    End time       : 2024-01-08 12:34:56.200 +0000 UTC
    Status code    : Error
    Status message : Database error: connection pool exhausted
Attributes:
     -> request.number: Int(43)
     -> error.type: Str(database)
     -> http.status_code: Int(500)
     -> error: Bool(true)
     -> telemetry.filtered: Bool(true)
```

---

## Method 2: Query Collector Metrics

The collector exposes Prometheus metrics about its own operation.

### See Filter Statistics

```bash
# Total spans filtered
curl -s http://localhost:8888/metrics | grep "otelcol_processor_filter.*spans"

# Total logs filtered
curl -s http://localhost:8888/metrics | grep "otelcol_processor_filter.*logs"

# Total metric datapoints filtered
curl -s http://localhost:8888/metrics | grep "otelcol_processor_filter.*datapoints"
```

**Example Output:**
```
otelcol_processor_filter_spans_filtered{processor="filter/traces",service_instance_id="...",service_name="otelcol-contrib",service_version="0.98.0"} 45
```

This shows 45 spans have been filtered out!

### See Processing Throughput

```bash
# Traces received and sent
curl -s http://localhost:8888/metrics | grep "otelcol_receiver_accepted_spans"
curl -s http://localhost:8888/metrics | grep "otelcol_exporter_sent_spans"

# Compare to see how many were dropped
```

### Create a Monitoring Dashboard

Use this script to continuously monitor:

```bash
# Save as monitor.sh
#!/bin/bash
while true; do
  clear
  echo "=== OpenTelemetry Collector Stats ==="
  echo ""
  echo "Spans Received:"
  curl -s http://localhost:8888/metrics | grep "otelcol_receiver_accepted_spans" | tail -1
  echo ""
  echo "Spans Filtered:"
  curl -s http://localhost:8888/metrics | grep "otelcol_processor_filter.*spans_filtered" | tail -1
  echo ""
  echo "Spans Sent to New Relic:"
  curl -s http://localhost:8888/metrics | grep "otelcol_exporter_sent_spans" | tail -1
  echo ""
  echo "Logs Filtered:"
  curl -s http://localhost:8888/metrics | grep "otelcol_processor_filter.*logs_filtered" | tail -1
  echo ""
  echo "Metric Points Filtered:"
  curl -s http://localhost:8888/metrics | grep "otelcol_processor_filter.*datapoints_filtered" | tail -1
  echo ""
  sleep 5
done
```

---

## Method 3: Use File Exporter (Capture to Disk)

Want to save telemetry to files for detailed analysis?

### Step 1: Add File Exporter to otel-config.yaml

```yaml
exporters:
  # ... existing exporters ...

  file/traces:
    path: /tmp/traces.json
    format: json

  file/logs:
    path: /tmp/logs.json
    format: json

  file/metrics:
    path: /tmp/metrics.json
    format: json

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, filter/traces, transform/traces, resourcedetection, batch]
      exporters: [otlphttp, debug, file/traces]  # Add file/traces

    logs:
      receivers: [otlp]
      processors: [memory_limiter, filter/logs, transform/logs, resourcedetection, batch]
      exporters: [otlphttp, debug, file/logs]  # Add file/logs

    metrics:
      receivers: [otlp]
      processors: [memory_limiter, filter/metrics, transform/metrics, resourcedetection, batch]
      exporters: [otlphttp, debug, file/metrics]  # Add file/metrics
```

### Step 2: Mount Volume in docker-compose.yaml

```yaml
services:
  collector:
    # ... existing config ...
    volumes:
      - ./otel-config.yaml:/etc/otel-collector-config.yaml
      - ./telemetry-output:/tmp  # Add this line
```

### Step 3: View the Files

```bash
# Create output directory
mkdir -p telemetry-output

# Restart collector
docker compose restart collector

# Watch traces being written
tail -f telemetry-output/traces.json | jq '.'

# Watch logs being written
tail -f telemetry-output/logs.json | jq '.'

# Watch metrics being written
tail -f telemetry-output/metrics.json | jq '.'
```

---

## Method 4: Compare Filtered vs Unfiltered

See exactly what gets filtered:

### Step 1: Capture WITHOUT Filtering

```bash
# Edit .env
FILTER_VALIDATION_ERRORS=false
FILTER_AUTH_ERRORS=false
FILTER_WARN_LOGS=false

# Start and capture for 60 seconds
docker compose up --build > unfiltered-output.log 2>&1 &
sleep 60
docker compose down

# Count spans
grep "Span #0" unfiltered-output.log | wc -l
```

### Step 2: Capture WITH Filtering

```bash
# Edit .env
FILTER_VALIDATION_ERRORS=true
FILTER_AUTH_ERRORS=true
FILTER_WARN_LOGS=true

# Start and capture for 60 seconds
docker compose up --build > filtered-output.log 2>&1 &
sleep 60
docker compose down

# Count spans
grep "Span #0" filtered-output.log | wc -l
```

### Step 3: Compare

```bash
# Show difference
diff -u <(grep "error.type" unfiltered-output.log | sort | uniq) \
        <(grep "error.type" filtered-output.log | sort | uniq)
```

---

## Method 5: Use zpages (Live Web UI)

The collector has a built-in web UI called zpages.

### Access zpages

Open your browser to: http://localhost:55679/debug/tracez

You'll see:
- **tracez** - Live trace spans currently in the collector
- **pipelinez** - Pipeline statistics and health
- **servicez** - Service configuration and versions
- **extensionz** - Extension status

### Most Useful Pages

1. **http://localhost:55679/debug/tracez** - See active traces
2. **http://localhost:55679/debug/pipelinez** - See pipeline throughput

---

## Method 6: Use Interactive Scripts

I've created helper scripts to make viewing data easier.

### View Live Errors Only

```bash
# Save as view-errors.sh
#!/bin/bash
echo "Watching for ERROR telemetry..."
docker compose logs -f collector | grep -E "(error|Error|ERROR)" --color=always
```

### View Specific Error Type

```bash
# Save as view-error-type.sh
#!/bin/bash
ERROR_TYPE=${1:-database}
echo "Watching for $ERROR_TYPE errors..."
docker compose logs -f collector | grep -i "$ERROR_TYPE" --color=always
```

Usage:
```bash
./view-error-type.sh validation
./view-error-type.sh auth
./view-error-type.sh database
```

### View Filtering in Action

```bash
# Save as view-filtering.sh
#!/bin/bash
echo "=== Filtered Items (these won't go to New Relic) ==="
echo ""
docker compose logs collector | grep -i "filtered" | head -20
```

---

## Method 7: Pretty Print JSON Output

If you want nicely formatted JSON:

```bash
# Install jq if you don't have it
# macOS: brew install jq
# Ubuntu: apt-get install jq

# Watch collector logs with pretty JSON
docker compose logs -f collector 2>&1 | while IFS= read -r line; do
  if echo "$line" | jq . 2>/dev/null; then
    :  # It's JSON, jq already printed it
  else
    echo "$line"  # Not JSON, print as-is
  fi
done
```

---

## Practical Exercises

### Exercise 1: See a Validation Error Get Filtered

```bash
# 1. Enable detailed debug output (edit otel-config.yaml)
verbosity: detailed

# 2. Enable validation errors in generator, but filter them
# Edit .env:
ENABLE_VALIDATION_ERRORS=true
FILTER_VALIDATION_ERRORS=true

# 3. Restart and watch
docker compose restart
docker compose logs -f collector | grep -A 20 "validation"
```

You'll see the validation error arrive at the collector but NOT appear in the "sent to New Relic" logs!

### Exercise 2: See Data Redaction in Action

```bash
# 1. Enable detailed debug
# 2. Enable auth errors (they contain tokens)
ENABLE_AUTH_ERRORS=true
FILTER_AUTH_ERRORS=false  # Don't filter so we can see the redaction

# 3. Watch for token redaction
docker compose logs -f collector | grep -E "(token|REDACTED)"
```

You'll see `token=abc123xyz` get transformed to `token=REDACTED`!

### Exercise 3: Monitor Filter Efficiency

```bash
# Watch stats every 5 seconds
watch -n 5 'curl -s http://localhost:8888/metrics | grep -E "(received|filtered|sent)" | grep spans'
```

You'll see counters increasing:
- `received_spans` - Total spans received
- `filtered_spans` - Total spans dropped
- `sent_spans` - Total spans sent to New Relic

---

## Understanding the Output

### Span Output Format

```
Span #0
    Trace ID       : <hex string>
    Parent ID      : <hex string or empty>
    ID             : <hex string>
    Name           : <operation name>
    Kind           : <Internal|Server|Client|Producer|Consumer>
    Start time     : <timestamp>
    End time       : <timestamp>
    Status code    : <Ok|Error|Unset>
    Status message : <error message if status is Error>
Attributes:
     -> key: Type(value)
     -> key: Type(value)
```

### Log Output Format

```
LogRecord #0
     -> Timestamp: <timestamp>
     -> ObservedTimestamp: <timestamp>
     -> Severity: <number>
     -> SeverityText: <text>
     -> Body: Str(<log message>)
     -> Attributes:
          -> key: Type(value)
     -> Trace ID: <hex string>
     -> Span ID: <hex string>
```

### Metric Output Format

```
Metric #0
Descriptor:
     -> Name: <metric name>
     -> Description: <description>
     -> Unit: <unit>
     -> DataType: <Sum|Gauge|Histogram>
NumberDataPoints #0
     -> StartTimestamp: <timestamp>
     -> Timestamp: <timestamp>
     -> Value: <number>
     -> Attributes:
          -> key: Type(value)
```

---

## Troubleshooting

### "Too much output, can't keep up!"

Reduce verbosity:
```yaml
debug:
  verbosity: basic
  sampling_initial: 5
  sampling_thereafter: 100
```

### "I don't see any output"

1. Check collector is running: `docker compose ps`
2. Check health: `curl http://localhost:13133`
3. Check error generator is running: `curl http://localhost:8080/health`

### "Output is not formatted"

Install jq for JSON formatting:
```bash
# macOS
brew install jq

# Ubuntu/Debian
apt-get install jq

# Then pipe through jq
docker compose logs collector | jq '.'
```

### "I want to see raw OTLP protocol"

Enable gRPC tracing in docker-compose.yaml:
```yaml
collector:
  environment:
    - GRPC_GO_LOG_VERBOSITY_LEVEL=99
    - GRPC_GO_LOG_SEVERITY_LEVEL=info
```

---

## Next Steps

1. **Start simple**: Use Method 1 (basic debug logs)
2. **Add detail**: Enable detailed debug output
3. **Monitor stats**: Use Method 2 (metrics endpoint)
4. **Experiment**: Try different filter configurations and observe the impact
5. **Save samples**: Use Method 3 (file exporter) to save examples

## Quick Reference Commands

```bash
# Start everything
docker compose up --build

# Watch logs
docker compose logs -f collector

# Watch errors only
docker compose logs -f collector | grep -i error

# Check metrics
curl http://localhost:8888/metrics | grep filter

# Health check
curl http://localhost:13133

# Restart after config change
docker compose restart collector

# Stop everything
docker compose down
```