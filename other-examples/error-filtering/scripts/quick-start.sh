#!/bin/bash

# Quick start script - Interactive guide for viewing live telemetry

clear
cat << 'EOF'
╔════════════════════════════════════════════════════════════╗
║                                                            ║
║     OpenTelemetry Error Filtering - Live Data Viewer      ║
║                                                            ║
╚════════════════════════════════════════════════════════════╝

This interactive guide helps you view live telemetry data.

EOF

# Check if services are running
if ! docker compose ps | grep -q "running"; then
  echo "⚠️  Services not running!"
  echo ""
  read -p "Start services now? (y/n) " -n 1 -r
  echo ""
  if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Starting services..."
    docker compose up -d
    echo "Waiting for services to be ready..."
    sleep 10
  else
    echo "Please start services with: docker compose up -d"
    exit 1
  fi
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
  2) 🔍 View all collector output (detailed)
  3) ⚠️  View only errors
  4) 🔧 View specific error type (validation, auth, etc.)
  5) 📸 Capture samples to files (for analysis)
  6) 🔀 Compare filtered vs unfiltered (takes 60s)
  7) 🌐 Open zpages web UI
  8) 📖 View documentation

  0) Exit

EOF

read -p "Enter your choice (0-8): " choice

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
    echo "Showing all collector output..."
    echo "This is very detailed! Press Ctrl+C to stop."
    echo ""
    read -p "Press Enter to continue..."
    docker compose logs -f collector
    ;;

  3)
    echo ""
    echo "Showing only errors..."
    echo "Press Ctrl+C to stop."
    echo ""
    sleep 2
    ./scripts/view-errors.sh
    ;;

  4)
    echo ""
    echo "Available error types:"
    echo "  - validation"
    echo "  - auth"
    echo "  - database"
    echo "  - timeout"
    echo "  - network"
    echo ""
    read -p "Enter error type: " error_type
    echo ""
    echo "Showing $error_type errors..."
    echo "Press Ctrl+C to stop."
    echo ""
    sleep 2
    ./scripts/view-errors.sh "$error_type"
    ;;

  5)
    echo ""
    read -p "How many seconds to capture? (default: 30): " duration
    duration=${duration:-30}
    ./scripts/capture-samples.sh "$duration"
    ;;

  6)
    echo ""
    echo "This will run the example twice:"
    echo "  1. Without filtering"
    echo "  2. With filtering"
    echo ""
    read -p "How many seconds per run? (default: 30): " duration
    duration=${duration:-30}
    ./scripts/compare-filtering.sh "$duration"
    ;;

  7)
    echo ""
    echo "Opening zpages in your browser..."
    echo ""
    echo "Available pages:"
    echo "  - http://localhost:55679/debug/tracez    (live traces)"
    echo "  - http://localhost:55679/debug/pipelinez (pipeline stats)"
    echo ""
    if command -v open &> /dev/null; then
      open "http://localhost:55679/debug/tracez"
    elif command -v xdg-open &> /dev/null; then
      xdg-open "http://localhost:55679/debug/tracez"
    else
      echo "Please open http://localhost:55679/debug/tracez in your browser"
    fi
    ;;

  8)
    echo ""
    echo "Documentation files:"
    echo "  - README.md                 - Getting started guide"
    echo "  - VIEWING_LIVE_DATA.md      - How to view live data"
    echo ""
    read -p "Which file to view? (1-2): " doc_choice
    case $doc_choice in
      1) less README.md ;;
      2) less VIEWING_LIVE_DATA.md ;;
      *) echo "Invalid choice" ;;
    esac
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