#!/usr/bin/env bash
# One-command local run: starts the collector, builds and starts the
# function under `sam local`, sends real traffic, prints the NRQL to
# validate it in New Relic, then tears everything down on exit.
#
# Requires: Docker, SAM CLI, a JDK Gradle can run (JDK 21 recommended; see
# README prerequisites), and NEW_RELIC_LICENSE_KEY exported.
set -euo pipefail
cd "$(dirname "$0")"

: "${NEW_RELIC_LICENSE_KEY:?Set NEW_RELIC_LICENSE_KEY to your license key.}"
REGION="${AWS_REGION:-us-east-1}"
REQUEST_COUNT="${REQUEST_COUNT:-100}"

# A pre-set JAVA_HOME (or one found via auto-detection) is only trusted once
# its *actual* reported version is checked -- an inherited JAVA_HOME pointing
# at JDK 25 is exactly the failure mode this guards against (JDK 25 breaks
# Gradle itself, per the README prerequisites).
is_jdk_21() {
  [ -x "$1/bin/java" ] && "$1/bin/java" -version 2>&1 | grep -q '"21\.'
}

if [ -n "${JAVA_HOME:-}" ] && ! is_jdk_21 "$JAVA_HOME"; then
  echo "JAVA_HOME is set but isn't a JDK 21 ($("$JAVA_HOME/bin/java" -version 2>&1 | head -1)) - looking for one instead." >&2
  JAVA_HOME=""
fi
if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in "$HOME"/.sdkman/candidates/java/21*; do
    if is_jdk_21 "$candidate"; then
      JAVA_HOME="$candidate"
      break
    fi
  done
fi
if [ -z "${JAVA_HOME:-}" ] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  candidate="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  if [ -n "$candidate" ] && is_jdk_21 "$candidate"; then
    JAVA_HOME="$candidate"
  fi
fi
if [ -z "${JAVA_HOME:-}" ]; then
  echo "Could not find a JDK 21 to build with (checked JAVA_HOME, ~/.sdkman/candidates/java/21*," >&2
  echo "and '/usr/libexec/java_home -v 21'). Install one and export JAVA_HOME yourself before" >&2
  echo "running this script. JDK 25 is known not to work - Gradle itself fails on it." >&2
  exit 1
fi
export JAVA_HOME

echo "==> Looking up the current ADOT Java layer ARN for $REGION"
OTEL_LAYER_ARN="$(./scripts/get-otel-layer-arn.sh "$REGION")"
echo "    $OTEL_LAYER_ARN"

SAM_LOG="$(mktemp)"
SAM_PID=""

cleanup() {
  echo "==> Cleaning up"
  if [ -n "$SAM_PID" ] && kill -0 "$SAM_PID" 2>/dev/null; then
    kill "$SAM_PID" 2>/dev/null || true
    wait "$SAM_PID" 2>/dev/null || true
  fi
  docker compose down >/dev/null 2>&1 || true
  rm -f "$SAM_LOG"
}
trap cleanup EXIT

echo "==> Starting the collector"
docker compose up -d

echo "==> Building the function (JAVA_HOME=$JAVA_HOME)"
sam build

echo "==> Starting sam local start-api in the background"
sam local start-api \
  --docker-network faas-metrics-net \
  --parameter-overrides "otelLambdaLayerArn=${OTEL_LAYER_ARN} otelExporterOtlpEndpoint=http://collector:4318" \
  --port 3000 \
  >"$SAM_LOG" 2>&1 &
SAM_PID=$!

echo "==> Waiting for it to come up"
ready=""
for i in $(seq 1 60); do
  # -f: non-2xx must count as "not ready yet", not success.
  if curl -sf -o /dev/null "http://127.0.0.1:3000/?sleepMs=1"; then
    ready=1
    break
  fi
  if ! kill -0 "$SAM_PID" 2>/dev/null; then
    echo "sam local start-api exited early. Log:" >&2
    cat "$SAM_LOG" >&2
    exit 1
  fi
  sleep 2
done
if [ -z "$ready" ]; then
  echo "sam local start-api never returned a successful response after 120s. Log:" >&2
  cat "$SAM_LOG" >&2
  exit 1
fi

echo "==> Generating $REQUEST_COUNT requests of traffic"
./scripts/generate-traffic.sh "http://127.0.0.1:3000/" "$REQUEST_COUNT" traffic-results.csv

failures="$(awk -F, 'NR>1 && $3!=200' traffic-results.csv | wc -l | tr -d ' ')"
if [ "$failures" != "0" ]; then
  echo "" >&2
  echo "$failures of $REQUEST_COUNT requests did not return 200 - see traffic-results.csv. sam local log:" >&2
  cat "$SAM_LOG" >&2
  exit 1
fi

echo ""
echo "==> All $REQUEST_COUNT requests returned 200. Wait about a minute for the collector's metrics flush, then validate in New Relic:"
echo ""
echo "    FROM Metric SELECT sum(faas.invocations) WHERE service.name = 'java-faas-metrics-example' SINCE 10 minutes ago"
echo "    FROM Metric SELECT percentile(faas.invoke_duration, 50, 95, 99) WHERE service.name = 'java-faas-metrics-example' SINCE 10 minutes ago"
echo "    FROM Span SELECT percentile(duration.ms, 50, 95, 99) WHERE service.name = 'java-faas-metrics-example' AND span.kind = 'server' SINCE 10 minutes ago"
echo ""
echo "See README.md's 'Validate in New Relic' section for how to interpret these."
