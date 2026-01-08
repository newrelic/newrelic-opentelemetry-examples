#!/bin/bash

# Show what's being filtered out
# This helps you see which telemetry items are NOT being sent to New Relic

echo "==================================================="
echo "   Items Being Filtered (Not Sent to New Relic)"
echo "==================================================="
echo ""
echo "This shows telemetry that arrives at the collector"
echo "but is filtered out before being sent to New Relic."
echo ""
echo "Press Ctrl+C to stop"
echo ""

# Check if we should show historical or live data
if [ "$1" = "history" ]; then
  echo "📊 Historical Filtered Items"
  echo "---------------------------------------------------"
  echo ""

  echo "Validation errors (if filtered):"
  docker compose logs collector 2>&1 | grep -E "error\.type.*validation" | head -5
  echo ""

  echo "Auth errors (if filtered):"
  docker compose logs collector 2>&1 | grep -E "error\.type.*auth" | head -5
  echo ""

  echo "WARN level logs (if filtered):"
  docker compose logs collector 2>&1 | grep -E "severityText.*WARN" | head -5
  echo ""
else
  echo "📡 Live Filtering (watching in real-time)"
  echo "---------------------------------------------------"
  echo ""

  # Watch for traces/logs/metrics that would be filtered
  # Note: With the filter processor, filtered items don't appear in debug output
  # So we need to look for them before filtering

  docker compose logs -f collector 2>&1 | grep --line-buffered -E "(validation|auth|WARN)" --color=always
fi