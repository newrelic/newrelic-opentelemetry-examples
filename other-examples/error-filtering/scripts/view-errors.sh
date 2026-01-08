#!/bin/bash

# View error telemetry in real-time
# Shows only error-related traces, logs, and metrics

ERROR_TYPE=${1:-"all"}

echo "==================================================="
echo "   Viewing Error Telemetry - Type: $ERROR_TYPE"
echo "==================================================="
echo ""
echo "Press Ctrl+C to stop"
echo ""

if [ "$ERROR_TYPE" = "all" ]; then
  echo "Showing all error types (validation, auth, database, timeout, network)"
  docker compose logs -f collector 2>&1 | grep --line-buffered -E "(error\.type|Status code.*Error|severityText.*ERROR)" --color=always
else
  echo "Showing only: $ERROR_TYPE errors"
  docker compose logs -f collector 2>&1 | grep --line-buffered -i "$ERROR_TYPE" --color=always
fi