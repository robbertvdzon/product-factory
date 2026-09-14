#!/usr/bin/env bash
set -euo pipefail

namespace="${PF_OPENSHIFT_NAMESPACE:-product-factory}"

command -v oc >/dev/null || {
  echo "oc is niet beschikbaar." >&2
  exit 1
}
command -v pbcopy >/dev/null || {
  echo "pbcopy is niet beschikbaar; kopieer de token niet via terminaluitvoer." >&2
  exit 1
}

oc exec -n "$namespace" deployment/product-factory-backend -- \
  sh -c 'test -n "$PF_DEBUG_TOKEN" && printf %s "$PF_DEBUG_TOKEN"' | pbcopy

echo "PF_DEBUG_TOKEN is rechtstreeks naar het klembord gekopieerd; de waarde is niet uitgevoerd."

