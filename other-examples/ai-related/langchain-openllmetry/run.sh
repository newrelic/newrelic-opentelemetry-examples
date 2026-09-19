#!/bin/bash

set -e

cd "$(dirname "${BASH_SOURCE[0]}")"

SCRIPT="app/run_openllmetry.py"

if [ ! -f .env ]; then
  echo "Missing .env — copying from .env.example. Fill in OPENAI_API_KEY and NEW_RELIC_LICENSE_KEY." >&2
  cp .env.example .env
  exit 1
fi

# Start the OTel Collector
echo "Starting OTel Collector..."
docker compose up -d

# Activate virtual environment if it exists
if [ -d ".venv" ]; then
  source .venv/bin/activate
fi

# Install dependencies
pip install -q -r requirements.txt

# Send sample requests one by one with a delay between each
DELAY=5

echo ""
echo "--- Sending sample requests via $SCRIPT (${DELAY}s between each) ---"

send_request() {
  local label="$1"
  local prompt="$2"
  echo ""
  echo ">>> $label"
  python "$SCRIPT" "$prompt"
}

send_request "Request 1: Trip to Berlin" "Plan a trip to Berlin — tell me the weather and the best places to visit"
sleep $DELAY

send_request "Request 2: Trip to Tokyo" "Plan a trip to Tokyo — tell me the weather and the best places to visit"
sleep $DELAY

send_request "Request 3: Trip to New York" "Plan a trip to New York — tell me the weather and the best places to visit"
sleep $DELAY

send_request "Request 4: Trip to Sydney" "Plan a trip to Sydney — tell me the weather and the best places to visit"

echo ""
echo "--- Done ---"
