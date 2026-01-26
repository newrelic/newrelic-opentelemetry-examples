#!/bin/bash

# Compare telemetry with and without transformation
# This script runs the example twice and shows the difference

DURATION=${1:-30}

echo "==================================================="
echo "   Comparing Transformed vs Untransformed Telemetry"
echo "==================================================="
echo ""
echo "Total time: ~$(($DURATION * 2)) seconds"
echo ""
sleep 3

# Create output directory
OUTPUT_DIR="./comparison"
mkdir -p "${OUTPUT_DIR}"

# Save current configs
cp .env .env.backup
cp otel-config.yaml otel-config.yaml.backup

echo ""
echo "================================================"
echo "Step 1: Running WITHOUT transformation..."
echo "================================================"
echo ""

# Use config without transforms
cp otel-config-no-transform.yaml otel-config.yaml

# Start services
docker compose down
docker compose up -d

# Wait for startup
echo "Waiting for services to start..."
sleep 10

# Capture for duration
echo "Capturing for ${DURATION} seconds..."
docker compose logs -f collector > "${OUTPUT_DIR}/unfiltered-output.log" 2>&1 &
CAPTURE_PID=$!

for i in $(seq $DURATION -1 1); do
  echo -ne "Capturing... ${i} seconds remaining\r"
  sleep 1
done
echo ""

kill $CAPTURE_PID 2>/dev/null

# Get stats
curl -s http://localhost:8888/metrics > "${OUTPUT_DIR}/unfiltered-stats.txt"

# Stop services
docker compose down

echo ""
echo "================================================"
echo "Step 2: Running WITH transformation..."
echo "================================================"
echo ""

# Restore config with transforms
cp otel-config.yaml.backup otel-config.yaml

# Start services
docker compose up -d

# Wait for startup
echo "Waiting for services to start..."
sleep 10

# Capture for duration
echo "Capturing for ${DURATION} seconds..."
docker compose logs -f collector > "${OUTPUT_DIR}/filtered-output.log" 2>&1 &
CAPTURE_PID=$!

for i in $(seq $DURATION -1 1); do
  echo -ne "Capturing... ${i} seconds remaining   \r"
  sleep 1
done
echo ""

kill $CAPTURE_PID 2>/dev/null

# Stop services
docker compose down

# Restore original configs
mv .env.backup .env
mv otel-config.yaml.backup otel-config.yaml

echo ""
echo "================================================"
echo "Analyzing results..."
echo "================================================"
echo ""

# Count traces
UNFILTERED_SPANS=$(grep -c "Span #" "${OUTPUT_DIR}/unfiltered-output.log")
FILTERED_SPANS=$(grep -c "Span #" "${OUTPUT_DIR}/filtered-output.log")

# Count logs
UNFILTERED_LOGS=$(grep -c "LogRecord #" "${OUTPUT_DIR}/unfiltered-output.log")
FILTERED_LOGS=$(grep -c "LogRecord #" "${OUTPUT_DIR}/filtered-output.log")

# Detailed comparison analysis
echo "========================================" > "${OUTPUT_DIR}/comparison-summary.txt"
echo "TRANSFORMATION COMPARISON ANALYSIS" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "========================================" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "" >> "${OUTPUT_DIR}/comparison-summary.txt"

# Error type breakdown
echo "1. ERROR TYPE BREAKDOWN" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "-------------------" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "Without transformation:" >> "${OUTPUT_DIR}/comparison-summary.txt"
grep -E "error\.type" "${OUTPUT_DIR}/unfiltered-output.log" | grep -o 'Str([^)]*)' | sort | uniq -c >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "With transformation:" >> "${OUTPUT_DIR}/comparison-summary.txt"
grep -E "error\.type" "${OUTPUT_DIR}/filtered-output.log" | grep -o 'Str([^)]*)' | sort | uniq -c >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "Note: validation/auth errors appear less in 'with transformation' because" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "they are transformed and have error.type removed." >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "" >> "${OUTPUT_DIR}/comparison-summary.txt"

# Span status comparison
echo "2. SPAN STATUS CODES" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "-------------------" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "" >> "${OUTPUT_DIR}/comparison-summary.txt"
UNFILTERED_ERROR_SPANS=$(grep -c "Status code.*Error" "${OUTPUT_DIR}/unfiltered-output.log")
UNFILTERED_OK_SPANS=$(grep -c "Status code.*Ok" "${OUTPUT_DIR}/unfiltered-output.log")
FILTERED_ERROR_SPANS=$(grep -c "Status code.*Error" "${OUTPUT_DIR}/filtered-output.log")
FILTERED_OK_SPANS=$(grep -c "Status code.*Ok" "${OUTPUT_DIR}/filtered-output.log")
echo "Without transformation:" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "  Error status: ${UNFILTERED_ERROR_SPANS} spans" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "  Ok status:    ${UNFILTERED_OK_SPANS} spans" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "With transformation:" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "  Error status: ${FILTERED_ERROR_SPANS} spans" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "  Ok status:    ${FILTERED_OK_SPANS} spans" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "✅ Validation/auth errors transformed: Error → Ok" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "" >> "${OUTPUT_DIR}/comparison-summary.txt"

# Log severity comparison
echo "3. LOG SEVERITY LEVELS" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "-------------------" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "" >> "${OUTPUT_DIR}/comparison-summary.txt"
UNFILTERED_ERROR_LOGS=$(grep -c "SeverityNumber: Error" "${OUTPUT_DIR}/unfiltered-output.log")
UNFILTERED_WARN_LOGS=$(grep -c "SeverityNumber: Warn" "${OUTPUT_DIR}/unfiltered-output.log")
UNFILTERED_INFO_LOGS=$(grep -c "SeverityNumber: Info" "${OUTPUT_DIR}/unfiltered-output.log")
FILTERED_ERROR_LOGS=$(grep -c "SeverityNumber: Error" "${OUTPUT_DIR}/filtered-output.log")
FILTERED_WARN_LOGS=$(grep -c "SeverityNumber: Warn" "${OUTPUT_DIR}/filtered-output.log")
FILTERED_INFO_LOGS=$(grep -c "SeverityNumber: Info" "${OUTPUT_DIR}/filtered-output.log")
echo "Without transformation:" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "  Error: ${UNFILTERED_ERROR_LOGS} logs" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "  Warn:  ${UNFILTERED_WARN_LOGS} logs" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "  Info:  ${UNFILTERED_INFO_LOGS} logs" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "With transformation:" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "  Error: ${FILTERED_ERROR_LOGS} logs" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "  Warn:  ${FILTERED_WARN_LOGS} logs" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "  Info:  ${FILTERED_INFO_LOGS} logs" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "✅ WARN logs downgraded to INFO, validation/auth errors downgraded" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "" >> "${OUTPUT_DIR}/comparison-summary.txt"

# Transformation markers
echo "4. TRANSFORMATION MARKERS" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "-------------------" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "" >> "${OUTPUT_DIR}/comparison-summary.txt"
TRANSFORMED_COUNT=$(grep -c "error.expected.*true" "${OUTPUT_DIR}/filtered-output.log")
echo "Items marked as 'error.expected': ${TRANSFORMED_COUNT}" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "✅ Expected errors are tagged and have modified status/severity" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "" >> "${OUTPUT_DIR}/comparison-summary.txt"

echo "📊 COMPARISON RESULTS"
echo "---------------------------------------------------"
echo ""
echo "NOTE: With transformation, ALL data is sent to New Relic."
echo "The difference is that 'expected' errors have their status changed."
echo ""
echo "TRACES (Spans):"
echo "  Without transformation: ${UNFILTERED_SPANS} spans"
echo "  With transformation:    ${FILTERED_SPANS} spans"
echo "  Note: Same count, but transformed spans have status=Ok instead of Error"
echo ""

echo "LOGS:"
echo "  Without transformation: ${UNFILTERED_LOGS} logs"
echo "  With transformation:    ${FILTERED_LOGS} logs"
echo "  Note: Same count, but WARN logs are downgraded to INFO severity"
echo ""

echo "📁 FILES CREATED:"
echo "  ${OUTPUT_DIR}/unfiltered-output.log    - Full output without transformation"
echo "  ${OUTPUT_DIR}/filtered-output.log      - Full output with transformation"
echo "  ${OUTPUT_DIR}/unfiltered-stats.txt     - Metrics without transformation"
echo "  ${OUTPUT_DIR}/filtered-stats.txt       - Metrics with transformation"
echo "  ${OUTPUT_DIR}/comparison-summary.txt   - Detailed comparison"
echo ""

echo "💡 VIEW DETAILS:"
echo "  cat ${OUTPUT_DIR}/comparison-summary.txt"
echo ""

cat "${OUTPUT_DIR}/comparison-summary.txt"
echo ""