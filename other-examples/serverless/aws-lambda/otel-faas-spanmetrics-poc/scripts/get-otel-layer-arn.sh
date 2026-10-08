#!/usr/bin/env bash
# Prints the current AWSOpenTelemetryDistroJava Lambda layer ARN for a region,
# sourced from the same data that backs
# https://aws-otel.github.io/docs/getting-started/lambda#adot-lambda-layer-arns
# (that page only renders via client-side JS, so this reads the underlying
# data file directly instead of scraping the rendered page).
set -euo pipefail

REGION="${1:?Usage: get-otel-layer-arn.sh <aws-region>}"
SOURCE_URL="https://raw.githubusercontent.com/aws-otel/aws-otel.github.io/main/src/config/lambdaLayerArns.js"

arn="$(curl -fsSL "$SOURCE_URL" \
  | grep "\"${REGION}\":" \
  | grep "AWSOpenTelemetryDistroJava" \
  | grep -oE 'arn:aws[a-zA-Z0-9:_/.-]+' \
  | head -1)"

if [ -z "$arn" ]; then
  echo "Could not find an AWSOpenTelemetryDistroJava layer ARN for region '$REGION'." >&2
  echo "Check $SOURCE_URL manually, or pass otelLambdaLayerArn explicitly." >&2
  exit 1
fi

echo "$arn"
