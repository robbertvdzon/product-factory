#!/usr/bin/env bash
set -euo pipefail

environment_name="${1:-}"
expected_revision="${2:-}"
case "$environment_name" in
  acceptance)
    frontend_url="https://product-factory-acceptance.vdzonsoftware.nl"
    backend_url="https://product-factory-api-acceptance.vdzonsoftware.nl"
    expected_auth="false"
    ;;
  production)
    frontend_url="https://product-factory.vdzonsoftware.nl"
    backend_url="https://product-factory-api.vdzonsoftware.nl"
    expected_auth="true"
    ;;
  *)
    echo "Gebruik: tools/smoke-test.sh {acceptance|production} [volledige-git-revisie]" >&2
    exit 2
    ;;
esac

frontend_headers="$(curl --fail --silent --show-error --head "$frontend_url/")"
frontend_version="$(curl --fail --silent --show-error "$frontend_url/version.json")"
session="$(curl --fail --silent --show-error "$backend_url/api/auth/session")"
version="$(curl --fail --silent --show-error "$backend_url/api/version")"
curl --fail --silent --show-error "$backend_url/actuator/health/liveness" >/dev/null
curl --fail --silent --show-error "$backend_url/actuator/health/readiness" >/dev/null

test "$(jq -r '.authRequired' <<<"$session")" = "$expected_auth"
test "$(jq -r '.environment' <<<"$session")" = "$environment_name"
test "$(jq -r '.environment' <<<"$version")" = "$environment_name"
test "$(jq -r '.applicationVersion' <<<"$version")" = "0.1.0"
if [[ -n "$expected_revision" ]]; then
  test "$(jq -r '.gitRevision' <<<"$version")" = "$expected_revision"
  test "$(jq -r '.gitRevision' <<<"$frontend_version")" = "$expected_revision"
fi
rg -qi '^cache-control: no-cache' <<<"$frontend_headers"
printf 'smoke-test: %s versie=%s revisie=%s authRequired=%s\n' \
  "$environment_name" \
  "$(jq -r '.applicationVersion' <<<"$version")" \
  "$(jq -r '.gitRevision' <<<"$version")" \
  "$expected_auth"
