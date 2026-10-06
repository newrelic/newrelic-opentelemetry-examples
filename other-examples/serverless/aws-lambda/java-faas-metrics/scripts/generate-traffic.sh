#!/usr/bin/env bash
set -euo pipefail

ENDPOINT="${1:-http://127.0.0.1:3000/}"
COUNT="${2:-100}"
OUTPUT_FILE="${3:-traffic-results.csv}"

echo "elapsed_ms,sleep_ms,status_code" > "$OUTPUT_FILE"

for i in $(seq 1 "$COUNT"); do
  sleep_ms=$(( (RANDOM % 791) + 10 ))  # 10-800ms, matches App.MIN/MAX_SLEEP_MILLIS
  start=$(date +%s%N)
  status=$(curl -s -o /dev/null -w "%{http_code}" "${ENDPOINT}?sleepMs=${sleep_ms}")
  end=$(date +%s%N)
  elapsed_ms=$(( (end - start) / 1000000 ))
  echo "${elapsed_ms},${sleep_ms},${status}" >> "$OUTPUT_FILE"
done

echo "Wrote $COUNT results to $OUTPUT_FILE"
