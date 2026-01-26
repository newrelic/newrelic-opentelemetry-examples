#!/bin/bash

# Monitor OpenTelemetry Collector Statistics
# Shows real-time transformation and throughput stats

echo "==================================================="
echo "   OpenTelemetry Collector - Live Statistics"
echo "==================================================="
echo ""
echo "Press Ctrl+C to stop"
echo ""
sleep 3

while true; do
  clear
  echo "==================================================="
  echo "   OpenTelemetry Collector - Live Statistics"
  echo "==================================================="
  echo ""
  echo "Time: $(date '+%Y-%m-%d %H:%M:%S')"
  echo ""

  echo "SPANS (Traces)"
  echo "---------------------------------------------------"
  SPANS_RECEIVED=$(curl -s http://localhost:8888/metrics | grep 'otelcol_receiver_accepted_spans{' | grep 'receiver="otlp"' | grep -oE '[0-9]+$' | head -1)
  SPANS_SENT=$(curl -s http://localhost:8888/metrics | grep 'otelcol_exporter_sent_spans{' | grep 'exporter="otlphttp"' | grep -oE '[0-9]+$' | head -1)

  echo "  Received:  ${SPANS_RECEIVED:-0} spans"
  echo "  Sent:      ${SPANS_SENT:-0} spans (to New Relic)"
  echo ""

  echo "LOGS"
  echo "---------------------------------------------------"
  LOGS_RECEIVED=$(curl -s http://localhost:8888/metrics | grep 'otelcol_receiver_accepted_log_records{' | grep 'receiver="otlp"' | grep -oE '[0-9]+$' | head -1)
  LOGS_SENT=$(curl -s http://localhost:8888/metrics | grep 'otelcol_exporter_sent_log_records{' | grep 'exporter="otlphttp"' | grep -oE '[0-9]+$' | head -1)

  echo "  Received:  ${LOGS_RECEIVED:-0} log records"
  echo "  Sent:      ${LOGS_SENT:-0} log records (to New Relic)"
  echo ""

  echo "METRICS"
  echo "---------------------------------------------------"
  METRICS_RECEIVED=$(curl -s http://localhost:8888/metrics | grep 'otelcol_receiver_accepted_metric_points{' | grep 'receiver="otlp"' | grep -oE '[0-9]+$' | head -1)
  METRICS_SENT=$(curl -s http://localhost:8888/metrics | grep 'otelcol_exporter_sent_metric_points{' | grep 'exporter="otlphttp"' | grep -oE '[0-9]+$' | head -1)

  echo "  Received:  ${METRICS_RECEIVED:-0} data points"
  echo "  Sent:      ${METRICS_SENT:-0} data points (to New Relic)"
  echo ""

  echo "COLLECTOR HEALTH"
  echo "---------------------------------------------------"
  if curl -s http://localhost:13133 > /dev/null 2>&1; then
    echo "  Status:    ✅ Healthy"
  else
    echo "  Status:    ❌ Unhealthy"
  fi

  if curl -s http://localhost:8080/health > /dev/null 2>&1; then
    echo "  Generator: ✅ Running"
  else
    echo "  Generator: ❌ Not running"
  fi
  echo ""

  echo "---------------------------------------------------"
  echo "Refreshing in 5 seconds..."

  sleep 5
done