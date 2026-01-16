#!/bin/bash

# Quick start script - Interactive guide for viewing live telemetry

clear
cat << 'EOF'
╔════════════════════════════════════════════════════════════╗
║                                                            ║
║     OpenTelemetry Error Filtering - Live Data Viewer      ║
║                                                            ║
╚════════════════════════════════════════════════════════════╝

EOF

# Check if services are running
if ! docker compose ps | grep -q "running"; then
  echo "Starting services..."
  docker compose up -d
  echo "Waiting for services to be ready..."
  sleep 10
fi

echo "✅ Services are running!"
echo ""

# Check health
echo "Checking health..."
if curl -s http://localhost:13133 > /dev/null 2>&1; then
  echo "  ✅ Collector is healthy"
else
  echo "  ❌ Collector is not responding"
fi

if curl -s http://localhost:8080/health > /dev/null 2>&1; then
  echo "  ✅ Error generator is running"
else
  echo "  ❌ Error generator is not responding"
fi

echo ""
echo "════════════════════════════════════════════════════════"
echo ""

cat << 'EOF'
What would you like to view?

  1) 📊 Live statistics dashboard (recommended for first time)
  2) 📸 Capture samples to files (for analysis)
  3) 🔀 Compare filtered vs unfiltered (takes 60s)

  0) Exit

EOF

read -p "Enter your choice (0-3): " choice

case $choice in
  1)
    echo ""
    echo "Starting live statistics dashboard..."
    echo "This shows real-time filtering stats."
    echo ""
    sleep 2
    ./scripts/monitor-stats.sh
    ;;

  2)
    echo ""
    read -p "How many seconds to capture? (default: 30): " duration
    duration=${duration:-30}
    ./scripts/capture-samples.sh "$duration"
    ;;

  3)
    echo ""
    echo "This will run the example twice:"
    echo "  1. Without filtering"
    echo "  2. With filtering"
    echo ""
    read -p "How many seconds per run? (default: 30): " duration
    duration=${duration:-30}
    ./scripts/compare-filtering.sh "$duration"
    ;;

  0)
    echo ""
    echo "Goodbye!"
    exit 0
    ;;

  *)
    echo ""
    echo "Invalid choice. Please run again."
    exit 1
    ;;
esac