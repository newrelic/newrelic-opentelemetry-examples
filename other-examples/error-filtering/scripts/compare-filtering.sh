#!/bin/bash

# Compare telemetry with and without transformation
# This script runs the example twice and shows the difference

DURATION=${1:-30}

echo "==================================================="
echo "   Comparing Transformed vs Untransformed Telemetry"
echo "==================================================="
echo ""
echo "This will run the example twice:"
echo "  1. Without transformation (${DURATION}s)"
echo "  2. With transformation (${DURATION}s)"
echo ""
echo "Total time: ~$(($DURATION * 2)) seconds"
echo ""
read -p "Press Enter to continue..."

# Create output directory
OUTPUT_DIR="./comparison"
mkdir -p "${OUTPUT_DIR}"

# Save current .env
cp .env .env.backup

echo ""
echo "================================================"
echo "Step 1: Running WITHOUT filtering..."
echo "================================================"
echo ""

# Disable all filters
cat > .env.temp << 'EOF'
NEW_RELIC_API_KEY=${NEW_RELIC_API_KEY}
NEW_RELIC_OTLP_ENDPOINT=${NEW_RELIC_OTLP_ENDPOINT:-https://otlp.nr-data.net}
ERROR_RATE=0.4
REQUEST_INTERVAL_MS=2000
ENABLE_TIMEOUT_ERRORS=true
ENABLE_VALIDATION_ERRORS=true
ENABLE_DATABASE_ERRORS=true
ENABLE_NETWORK_ERRORS=true
ENABLE_AUTH_ERRORS=true
FILTER_VALIDATION_ERRORS=false
FILTER_AUTH_ERRORS=false
FILTER_WARN_LOGS=false
SAMPLE_ERROR_METRICS=false
EOF

# Preserve API key from original .env
grep "NEW_RELIC_API_KEY=" .env.backup >> .env.temp
mv .env.temp .env

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
echo "Step 2: Running WITH filtering..."
echo "================================================"
echo ""

# Enable filters
cat > .env.temp << 'EOF'
NEW_RELIC_API_KEY=${NEW_RELIC_API_KEY}
NEW_RELIC_OTLP_ENDPOINT=${NEW_RELIC_OTLP_ENDPOINT:-https://otlp.nr-data.net}
ERROR_RATE=0.4
REQUEST_INTERVAL_MS=2000
ENABLE_TIMEOUT_ERRORS=true
ENABLE_VALIDATION_ERRORS=true
ENABLE_DATABASE_ERRORS=true
ENABLE_NETWORK_ERRORS=true
ENABLE_AUTH_ERRORS=true
FILTER_VALIDATION_ERRORS=true
FILTER_AUTH_ERRORS=true
FILTER_WARN_LOGS=true
SAMPLE_ERROR_METRICS=false
EOF

# Preserve API key
grep "NEW_RELIC_API_KEY=" .env.backup >> .env.temp
mv .env.temp .env

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
  echo -ne "Capturing... ${i} seconds remaining\r"
  sleep 1
done
echo ""

kill $CAPTURE_PID 2>/dev/null

# Get stats
curl -s http://localhost:8888/metrics > "${OUTPUT_DIR}/filtered-stats.txt"

# Stop services
docker compose down

# Restore original .env
mv .env.backup .env

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

# Count error types
echo "Error type breakdown (unfiltered):" > "${OUTPUT_DIR}/comparison-summary.txt"
grep -E "error\.type" "${OUTPUT_DIR}/unfiltered-output.log" | grep -o 'Str([^)]*)' | sort | uniq -c >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "" >> "${OUTPUT_DIR}/comparison-summary.txt"
echo "Error type breakdown (filtered):" >> "${OUTPUT_DIR}/comparison-summary.txt"
grep -E "error\.type" "${OUTPUT_DIR}/filtered-output.log" | grep -o 'Str([^)]*)' | sort | uniq -c >> "${OUTPUT_DIR}/comparison-summary.txt"

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