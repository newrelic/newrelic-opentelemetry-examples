# Helper Scripts for Viewing Live Telemetry

This directory contains helper scripts to view and analyze telemetry data flowing through the OpenTelemetry Collector.

## Quick Reference

```bash
# Interactive menu - Start here!
./quick-start.sh

# Live statistics dashboard
./monitor-stats.sh

# Capture samples to files
./capture-samples.sh [duration-seconds]

# Compare filtered vs unfiltered
./compare-filtering.sh [duration-seconds]
```

## Script Descriptions

### quick-start.sh
**Interactive menu for all viewing options**

Provides a user-friendly menu to access all viewing methods. Best for first-time users.

**Usage:**
```bash
./quick-start.sh
```

**Features:**
- Live statistics dashboard
- Capture samples
- Compare filtering

---

### monitor-stats.sh
**Real-time statistics dashboard**

Shows live filtering statistics updated every 5 seconds:
- Spans received/filtered/sent
- Logs received/filtered/sent
- Metrics received/filtered/sent
- Filter percentages
- Health status

**Usage:**
```bash
./monitor-stats.sh
```

**Example Output:**
```
📊 SPANS (Traces)
---------------------------------------------------
  Received:  150 spans
  Filtered:  45 spans (dropped)
  Sent:      105 spans (to New Relic)
  Filter %:  30.0% filtered out
```

---

### capture-samples.sh
**Capture telemetry samples to files**

Captures collector output for a specified duration and extracts traces, logs, and metrics to separate files for analysis.

**Usage:**
```bash
# Capture for 30 seconds (default)
./capture-samples.sh

# Capture for 60 seconds
./capture-samples.sh 60
```

**Output Files:**
- `telemetry-samples/collector-output.log` - Full collector output
- `telemetry-samples/traces-sample.txt` - Extracted trace spans
- `telemetry-samples/logs-sample.txt` - Extracted log records
- `telemetry-samples/metrics-sample.txt` - Extracted metrics
- `telemetry-samples/error-types-summary.txt` - Error breakdown
- `telemetry-samples/filter-stats.txt` - Filtering statistics

**Example:**
```bash
./capture-samples.sh 30
# Captures 30 seconds of data
# View traces: less telemetry-samples/traces-sample.txt
```

---

### compare-filtering.sh
**Compare filtered vs unfiltered telemetry**

Runs the example twice (with and without filtering) and shows the difference. This helps you see exactly what's being filtered out.

**Usage:**
```bash
# Run 30 seconds for each test (default)
./compare-filtering.sh

# Run 60 seconds for each test
./compare-filtering.sh 60
```

**Process:**
1. Runs with all filters disabled
2. Captures telemetry for specified duration
3. Runs with all filters enabled
4. Captures telemetry for specified duration
5. Compares and shows differences

**Output Files:**
- `comparison/unfiltered-output.log` - Full output without filtering
- `comparison/filtered-output.log` - Full output with filtering
- `comparison/unfiltered-stats.txt` - Metrics without filtering
- `comparison/filtered-stats.txt` - Metrics with filtering
- `comparison/comparison-summary.txt` - Detailed comparison

**Example Output:**
```
📊 COMPARISON RESULTS
---------------------------------------------------
TRACES (Spans):
  Without filtering: 150 spans
  With filtering:    95 spans
  Filtered out:      55 spans (36.7%)
```
