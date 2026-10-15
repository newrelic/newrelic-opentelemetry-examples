#!/usr/bin/env bash
# Prints the OpenTelemetry Lambda collector layer ARN (amd64) for a region,
# from the newest layer-collector release of open-telemetry/opentelemetry-lambda.
#
# The ARN printed in a release's notes can't be trusted (layer-collector/0.23.0
# prints the 0_22_0 layer), so this derives the name from the release tag and
# then asks AWS which version of that layer exists.
set -euo pipefail

REGION="${1:?Usage: get-collector-layer-arn.sh <aws-region>}"
RELEASES_URL="https://api.github.com/repos/open-telemetry/opentelemetry-lambda/releases?per_page=50"

tag="$(curl -fsSL "$RELEASES_URL" \
  | grep -oE '"tag_name": *"layer-collector/[0-9]+\.[0-9]+\.[0-9]+"' \
  | head -1 \
  | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')"
if [ -z "$tag" ]; then
  echo "Could not find a layer-collector release at $RELEASES_URL." >&2
  exit 1
fi

layer="arn:aws:lambda:${REGION}:184161586896:layer:opentelemetry-collector-amd64-${tag//./_}"

# Layer versions are numbered from 1 and the project publishes each release
# once, so the first few versions are all that need checking.
for version in 1 2 3 4 5; do
  if aws lambda get-layer-version-by-arn --arn "${layer}:${version}" >/dev/null 2>&1; then
    latest="${layer}:${version}"
  elif [ -n "${latest:-}" ]; then
    break
  fi
done

if [ -z "${latest:-}" ]; then
  echo "No published version of ${layer} found in ${REGION}." >&2
  exit 1
fi

echo "$latest"
