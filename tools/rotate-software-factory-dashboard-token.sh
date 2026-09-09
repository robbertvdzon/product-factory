#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_FILE="${PF_SEAL_SOURCE:-${ROOT_DIR}/secrets.env}"
SOFTWARE_FACTORY_NAMESPACE="${PF_SOFTWARE_FACTORY_NAMESPACE:-software-factory}"
SOFTWARE_FACTORY_SECRET="${PF_SOFTWARE_FACTORY_SECRET:-softwarefactory-dashboard-secrets}"
PRODUCT_FACTORY_NAMESPACE="${PF_SEAL_NAMESPACE:-product-factory}"
PRODUCT_FACTORY_SECRET="${PF_SECRET_NAME:-product-factory-secrets}"

command -v oc >/dev/null 2>&1 || { echo "Error: oc ontbreekt in PATH." >&2; exit 1; }
command -v openssl >/dev/null 2>&1 || { echo "Error: openssl ontbreekt in PATH." >&2; exit 1; }
[[ -f "$SOURCE_FILE" ]] || { echo "Error: secretbron bestaat niet." >&2; exit 1; }

decode_secret() {
  oc -n "$SOFTWARE_FACTORY_NAMESPACE" get secret "$SOFTWARE_FACTORY_SECRET" \
    -o "jsonpath={.data.$1}" | base64 --decode
}

allowed_emails="$(decode_secret SF_ALLOWED_EMAILS)"
remember_secret="$(decode_secret SF_DASHBOARD_REMEMBER_SECRET)"
token_email="${PF_DASHBOARD_TOKEN_EMAIL:-${allowed_emails%%,*}}"
token_email="$(printf '%s' "$token_email" | tr '[:upper:]' '[:lower:]' | xargs)"

case ",$(printf '%s' "$allowed_emails" | tr '[:upper:]' '[:lower:]' | tr -d ' ')," in
  *",${token_email},"*) ;;
  *) echo "Error: token-e-mail staat niet in SF_ALLOWED_EMAILS." >&2; exit 1 ;;
esac

if date -v+30d +%s >/dev/null 2>&1; then
  expires_at="$(date -v+30d +%s)"
else
  expires_at="$(date -d '+30 days' +%s)"
fi
payload="${token_email}:${expires_at}"
signature="$(printf '%s' "$payload" | openssl dgst -sha256 -hmac "$remember_secret" | awk '{print $NF}')"
dashboard_token="$(printf '%s' "${payload}:${signature}" | openssl base64 -A | tr '+/' '-_' | tr -d '=')"

upsert_secret() {
  local key="$1"
  local value="$2"
  local temporary
  temporary="$(mktemp)"
  awk -v wanted="$key" -v replacement="$value" '
    BEGIN { replaced=0 }
    index($0, wanted "=") == 1 { print wanted "=" replacement; replaced=1; next }
    { print }
    END { if (!replaced) print wanted "=" replacement }
  ' "$SOURCE_FILE" > "$temporary"
  chmod 600 "$temporary"
  mv "$temporary" "$SOURCE_FILE"
}

current_value() {
  awk -v wanted="$1" 'index($0, wanted "=") == 1 { print substr($0, length(wanted) + 2) }' "$SOURCE_FILE" | tail -1
}

for key in PF_DB_URL PF_DB_USERNAME PF_DB_PASSWORD PF_GOOGLE_CLIENT_ID PF_STAKEHOLDER_EMAILS \
  PF_SESSION_SIGNING_SECRET PF_AGENT_RUNTIME_TOKEN PF_SOFTWARE_FACTORY_TOKEN PF_DEBUG_TOKEN; do
  if [[ -z "$(current_value "$key")" ]]; then
    live_value="$(oc -n "$PRODUCT_FACTORY_NAMESPACE" get secret "$PRODUCT_FACTORY_SECRET" -o "jsonpath={.data.$key}" | base64 --decode)"
    [[ -n "$live_value" ]] || { echo "Error: bestaande secretwaarde ontbreekt: $key" >&2; exit 1; }
    upsert_secret "$key" "$live_value"
  fi
done

factory_owners="$(awk -F= '$1=="PF_FACTORY_OWNER_EMAILS" {print substr($0,index($0,"=")+1)}' "$SOURCE_FILE" | tail -1)"
if [[ -z "$factory_owners" ]]; then
  factory_owners="$(awk -F= '$1=="PF_STAKEHOLDER_EMAILS" {print substr($0,index($0,"=")+1)}' "$SOURCE_FILE" | tail -1)"
  [[ -n "$factory_owners" ]] || { echo "Error: factory owner-e-mail ontbreekt." >&2; exit 1; }
  upsert_secret PF_FACTORY_OWNER_EMAILS "$factory_owners"
fi
upsert_secret PF_SOFTWARE_FACTORY_DASHBOARD_TOKEN "$dashboard_token"
unset remember_secret dashboard_token signature payload

PF_SEAL_SOURCE="$SOURCE_FILE" \
PF_SEAL_OUTPUT="${ROOT_DIR}/deploy/overlays/production/sealed-secret-product-factory.yaml" \
  "${ROOT_DIR}/deploy/seal-secrets.sh"
echo "[rotate] Nieuw dashboardtoken verzegeld; vernieuw vóór $(date -r "$expires_at" '+%Y-%m-%d' 2>/dev/null || date -d "@$expires_at" '+%Y-%m-%d')."
