#!/bin/bash

# Capture sample telemetry data to files for analysis
# Usage: ./capture-samples.sh [duration_seconds]

DURATION=${1:-30}
OUTPUT_DIR="./telemetry-samples"

echo "==================================================="
echo "   Capturing Telemetry Samples"
echo "==================================================="
echo ""
echo "Duration: ${DURATION} seconds"
echo "Output directory: ${OUTPUT_DIR}"
echo ""

# Create output directory
mkdir -p "${OUTPUT_DIR}"

echo "Starting capture..."
echo ""

# Capture collector logs
docker compose logs -f collector > "${OUTPUT_DIR}/collector-output.log" 2>&1 &
CAPTURE_PID=$!

# Wait for specified duration
for i in $(seq $DURATION -1 1); do
  echo -ne "Capturing... ${i} seconds remaining   \r"
  sleep 1
done
echo ""

# Stop capture
kill $CAPTURE_PID 2>/dev/null

echo ""
echo "✅ Capture complete!"
echo ""
echo "Extracting samples..."

# Extract traces
grep -A 30 "Span #" "${OUTPUT_DIR}/collector-output.log" > "${OUTPUT_DIR}/traces-sample.txt"
TRACE_COUNT=$(grep -c "Span #" "${OUTPUT_DIR}/traces-sample.txt")

# Extract logs
grep -A 15 "LogRecord #" "${OUTPUT_DIR}/collector-output.log" > "${OUTPUT_DIR}/logs-sample.txt"
LOG_COUNT=$(grep -c "LogRecord #" "${OUTPUT_DIR}/logs-sample.txt")

# Extract metrics
grep -A 20 "Metric #" "${OUTPUT_DIR}/collector-output.log" > "${OUTPUT_DIR}/metrics-sample.txt"
METRIC_COUNT=$(grep -c "Metric #" "${OUTPUT_DIR}/metrics-sample.txt")

# Extract error types
grep -E "error\.type" "${OUTPUT_DIR}/collector-output.log" | sort | uniq -c > "${OUTPUT_DIR}/error-types-summary.txt"

echo ""
echo "📊 Summary"
echo "---------------------------------------------------"
echo "  Traces captured:  ${TRACE_COUNT}"
echo "  Logs captured:    ${LOG_COUNT}"
echo "  Metrics captured: ${METRIC_COUNT}"
echo ""
echo "📁 Files created:"
echo "  ${OUTPUT_DIR}/collector-output.log       - Full collector output"
echo "  ${OUTPUT_DIR}/traces-sample.txt          - Sample traces"
echo "  ${OUTPUT_DIR}/logs-sample.txt            - Sample logs"
echo "  ${OUTPUT_DIR}/metrics-sample.txt         - Sample metrics"
echo "  ${OUTPUT_DIR}/error-types-summary.txt    - Error types breakdown"
echo ""
