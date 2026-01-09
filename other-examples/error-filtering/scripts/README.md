# Helper Scripts for Viewing Live Telemetry

This directory contains helper scripts to view and analyze telemetry data flowing through the OpenTelemetry Collector.

## Quick Reference

```bash
# Interactive menu - Start here!
./quick-start.sh

# Live statistics dashboard
./monitor-stats.sh

# View errors only
./view-errors.sh [error-type]

# View what's being filtered
./view-filtered.sh [history]

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
- View collector output
- Filter by error type
- Capture samples
- Compare filtering
- Open zpages web UI

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

### view-errors.sh
**View error telemetry in real-time**

Shows only error-related traces, logs, and metrics. Optionally filter by specific error type.

**Usage:**
```bash
# View all errors
./view-errors.sh

# View specific error type
./view-errors.sh validation
./view-errors.sh auth
./view-errors.sh database
./view-errors.sh timeout
./view-errors.sh network
```

**Example:**
```bash
./view-errors.sh database
# Shows only database error telemetry
```

---

### view-filtered.sh
**Show what's being filtered out**

Displays telemetry that arrives at the collector but is filtered out before being sent to New Relic.

**Usage:**
```bash
# Live filtering (watch in real-time)
./view-filtered.sh

# Historical (from existing logs)
./view-filtered.sh history
```

**Note:** Filtered items don't appear in debug output (they're dropped before export), so this script shows items that match filter criteria before they're dropped.

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

---

## Prerequisites

All scripts require:
- Docker and Docker Compose
- Services running (`docker compose up`)
- bash shell
- curl (for stats scripts)

Some scripts also use:
- `bc` (for percentage calculations)
- `jq` (for JSON formatting - optional)
- `grep`, `sort`, `uniq` (standard Unix tools)

## Tips

### First Time Users
1. Start with `./quick-start.sh` - it guides you through all options
2. Try the statistics dashboard to see high-level filtering stats
3. Then explore specific views (errors, specific error types)

### Understanding Filtering
1. Run `./compare-filtering.sh 30` to see the impact of filtering
2. The comparison shows exactly what gets filtered out
3. Adjust `.env` settings and compare again

### Debugging Issues
1. Use `./monitor-stats.sh` to see if data is flowing
2. If no data appears, check health status in the dashboard
3. Use `./capture-samples.sh` to save data for detailed analysis

### Analyzing Patterns
1. Capture samples with `./capture-samples.sh 60`
2. Examine individual files in `telemetry-samples/`
3. Look for patterns in error types and frequencies

## Common Workflows

### Quick Health Check
```bash
./quick-start.sh
# Select option 1 (statistics dashboard)
# Check that spans/logs/metrics are being received
```

### Debug Filtering
```bash
# See what's being filtered in real-time
./view-errors.sh validation

# Or capture to files for detailed analysis
./capture-samples.sh 30
grep "validation" telemetry-samples/traces-sample.txt
```

### Measure Filter Impact
```bash
# Compare before and after filtering
./compare-filtering.sh 30

# Review the summary
cat comparison/comparison-summary.txt
```

### Monitor Production
```bash
# Watch stats continuously
./monitor-stats.sh

# In another terminal, watch for critical errors only
./view-errors.sh database
```

## Troubleshooting

### "Command not found"
Make sure scripts are executable:
```bash
chmod +x scripts/*.sh
```

### "Connection refused" errors
Services aren't running. Start them:
```bash
docker compose up -d
# Wait 10 seconds
./monitor-stats.sh
```

### "bc: command not found"
Install bc for percentage calculations:
```bash
# macOS
brew install bc

# Ubuntu/Debian
apt-get install bc
```

### No output from scripts
Check if collector is running:
```bash
docker compose ps
docker compose logs collector
```

## See Also

- **[VIEWING_LIVE_DATA.md](../VIEWING_LIVE_DATA.md)** - Detailed guide with manual methods
- **[README.md](../README.md)** - Main example documentation