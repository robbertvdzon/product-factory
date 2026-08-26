#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_FILE="${PF_SEAL_SOURCE:-${DEPLOY_DIR}/../secrets.env}"
CERT_FILE="${PF_SEAL_CERT:-${DEPLOY_DIR}/../../robberts-infrastructure/manifests/cluster-bootstrap/cluster-cert.pem}"
OUTPUT_FILE="${PF_SEAL_OUTPUT:-${DEPLOY_DIR}/sealed-secret-product-factory.yaml}"
NAMESPACE="${PF_SEAL_NAMESPACE:-product-factory}"
SECRET_NAME="product-factory-secrets"
REQUIRED_KEYS=(
  PF_DB_URL
  PF_DB_USERNAME
  PF_DB_PASSWORD
  PF_GOOGLE_CLIENT_ID
  PF_STAKEHOLDER_EMAILS
  PF_SESSION_SIGNING_SECRET
  PF_AGENT_RUNTIME_TOKEN
)

command -v kubeseal >/dev/null 2>&1 || { echo "Error: kubeseal ontbreekt in PATH." >&2; exit 1; }
[[ -f "$SOURCE_FILE" ]] || { echo "Error: secretbron bestaat niet." >&2; exit 1; }
[[ -f "$CERT_FILE" ]] || { echo "Error: clustercertificaat ontbreekt." >&2; exit 1; }

value_for() {
  local wanted="$1"
  if [[ "$wanted" == "PF_AGENT_RUNTIME_TOKEN" && -n "${PF_AGENT_RUNTIME_TOKEN:-}" ]]; then
    printf '%s\n' "$PF_AGENT_RUNTIME_TOKEN"
    return
  fi
  awk -v key="$wanted" 'index($0, key "=") == 1 { print substr($0, length(key) + 2) }' "$SOURCE_FILE" | tail -1
}

for key in "${REQUIRED_KEYS[@]}"; do
  [[ -n "$(value_for "$key")" ]] || { echo "Error: verplichte secretkey ontbreekt: $key" >&2; exit 1; }
done

plain_file="$(mktemp)"
sealed_file="$(mktemp)"
trap 'rm -f "$plain_file" "$sealed_file"' EXIT
chmod 600 "$plain_file" "$sealed_file"

{
  printf 'apiVersion: v1\nkind: Secret\nmetadata:\n  name: %s\n  namespace: %s\ntype: Opaque\nstringData:\n' "$SECRET_NAME" "$NAMESPACE"
  for key in "${REQUIRED_KEYS[@]}"; do
    printf '  %s: |-\n' "$key"
    printf '%s\n' "$(value_for "$key")" | sed 's/^/    /'
  done
} > "$plain_file"

kubeseal --cert "$CERT_FILE" --format yaml < "$plain_file" > "$sealed_file"
mv "$sealed_file" "$OUTPUT_FILE"
echo "[seal] ${#REQUIRED_KEYS[@]} waarden versleuteld naar $OUTPUT_FILE"
