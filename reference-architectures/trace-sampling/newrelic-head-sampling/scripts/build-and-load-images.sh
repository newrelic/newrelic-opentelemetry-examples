#!/usr/bin/env bash
# Builds the two app images used by this example and loads them into the
# "nr-sampling-nr-head" kind cluster so they can be referenced with
# imagePullPolicy: Never (no registry needed).
set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-nr-sampling-nr-head}"
REPO_ROOT="$(git rev-parse --show-toplevel)"
EXAMPLE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

build_and_load() {
  local image="$1"
  local context="$2"
  local dockerfile="$3"

  echo "Building ${image} from ${context} (${dockerfile})"
  docker build --build-context overlay="${EXAMPLE_ROOT}/apps/javascript" -f "${dockerfile}" -t "${image}" "${context}"

  echo "Loading ${image} into kind cluster ${CLUSTER_NAME}"
  kind load docker-image "${image}" --name "${CLUSTER_NAME}"
}

build_and_load "getting-started-javascript-sampling-nr-head:local" \
  "${REPO_ROOT}/getting-started-guides/javascript" \
  "${EXAMPLE_ROOT}/apps/javascript/Dockerfile"

build_and_load "getting-started-python-sampling-nr-head:local" \
  "${REPO_ROOT}/getting-started-guides/python" \
  "${EXAMPLE_ROOT}/apps/python/Dockerfile"
