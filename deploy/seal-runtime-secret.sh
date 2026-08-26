#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERT_FILE="${PF_SEAL_CERT:-${DEPLOY_DIR}/../../robberts-infrastructure/manifests/cluster-bootstrap/cluster-cert.pem}"
OUTPUT_FILE="${PF_SEAL_OUTPUT:-${DEPLOY_DIR}/overlays/acceptance/sealed-secret-agent-runtime.yaml}"
NAMESPACE="${PF_SEAL_NAMESPACE:-product-factory-acceptance}"
SECRET_NAME="product-factory-runtime-secrets"

command -v kubeseal >/dev/null 2>&1 || { echo "Error: kubeseal ontbreekt in PATH." >&2; exit 1; }
[[ -f "$CERT_FILE" ]] || { echo "Error: clustercertificaat ontbreekt." >&2; exit 1; }
[[ -n "${PF_AGENT_RUNTIME_TOKEN:-}" ]] || { echo "Error: PF_AGENT_RUNTIME_TOKEN ontbreekt." >&2; exit 1; }

plain_file="$(mktemp)"
sealed_file="$(mktemp)"
trap 'rm -f "$plain_file" "$sealed_file"' EXIT
chmod 600 "$plain_file" "$sealed_file"

kubectl create secret generic "$SECRET_NAME" \
  --namespace "$NAMESPACE" \
  --from-literal="PF_AGENT_RUNTIME_TOKEN=$PF_AGENT_RUNTIME_TOKEN" \
  --dry-run=client -o yaml > "$plain_file"
kubeseal --cert "$CERT_FILE" --format yaml < "$plain_file" > "$sealed_file"
mv "$sealed_file" "$OUTPUT_FILE"
echo "[seal] Runtime-consumentcredential versleuteld naar $OUTPUT_FILE"
