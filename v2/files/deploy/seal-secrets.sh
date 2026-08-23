#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_FILE="${PF_SEAL_SOURCE:-${DEPLOY_DIR}/../secrets.env}"
CERT_FILE="${PF_SEAL_CERT:-${DEPLOY_DIR}/../../robberts-infrastructure/manifests/cluster-bootstrap/cluster-cert.pem}"
OUTPUT_FILE="${DEPLOY_DIR}/base/sealed-secret-product-factory.yaml"
NAMESPACE="product-factory"
SECRET_NAME="product-factory-secrets"
REQUIRED_KEYS=(
  PF_DB_PASSWORD
  PF_WORKSPACE_GITHUB_TOKEN
  PF_GOOGLE_CLIENT_ID
  PF_ADMIN_EMAILS
  PF_AGENT_WORKER_TOKEN
  PF_SOFTWARE_FACTORY_TOKEN
  PF_DASHBOARD_REMEMBER_SECRET
)

if ! command -v kubeseal >/dev/null 2>&1; then
  echo "Error: kubeseal is niet gevonden in PATH." >&2
  exit 1
fi
if [[ ! -f "$SOURCE_FILE" ]]; then
  echo "Error: secretbron bestaat niet: $SOURCE_FILE" >&2
  echo "Kopieer secrets.env.example naar secrets.env en vul alle waarden in." >&2
  exit 1
fi
if [[ ! -f "$CERT_FILE" ]]; then
  echo "Error: gedeeld cluster-certificaat ontbreekt: $CERT_FILE" >&2
  exit 1
fi

value_for() {
  local wanted="$1"
  awk -v key="$wanted" 'index($0, key "=") == 1 { print substr($0, length(key) + 2) }' "$SOURCE_FILE" | tail -1
}

workspace_token="$(value_for PF_WORKSPACE_GITHUB_TOKEN)"
if [[ -z "$workspace_token" ]]; then
  echo "Error: zet de nieuwe PF_WORKSPACE_GITHUB_TOKEN expliciet in $SOURCE_FILE." >&2
  exit 1
fi
if [[ "$workspace_token" != github_pat_* ]]; then
  echo "Error: PF_WORKSPACE_GITHUB_TOKEN moet een fine-grained GitHub-token zijn." >&2
  exit 1
fi

for key in "${REQUIRED_KEYS[@]}"; do
  if [[ -z "$(value_for "$key")" ]]; then
    echo "Error: verplichte waarde $key is leeg of ontbreekt in $SOURCE_FILE." >&2
    exit 1
  fi
done

plain_file="$(mktemp)"
sealed_file="$(mktemp)"
trap 'rm -f "$plain_file" "$sealed_file"' EXIT
chmod 600 "$plain_file" "$sealed_file"

{
  cat <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: ${SECRET_NAME}
  namespace: ${NAMESPACE}
type: Opaque
stringData:
EOF
  for key in "${REQUIRED_KEYS[@]}"; do
    printf '  %s: |-\n' "$key"
    printf '%s\n' "$(value_for "$key")" | sed 's/^/    /'
  done
} > "$plain_file"

kubeseal --cert "$CERT_FILE" --format yaml < "$plain_file" > "$sealed_file"
mv "$sealed_file" "$OUTPUT_FILE"
echo "[seal] ${#REQUIRED_KEYS[@]} waarden versleuteld naar $OUTPUT_FILE"
echo "[seal] commit uitsluitend het SealedSecret; nooit $SOURCE_FILE"
