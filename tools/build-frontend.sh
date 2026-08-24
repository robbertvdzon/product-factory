#!/usr/bin/env bash
set -euo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FRONTEND_ROOT="$REPOSITORY_ROOT/product-factory-frontend"

application_version="${PF_APPLICATION_VERSION:-0.1.0}"
git_revision="${PF_GIT_REVISION:-$(git -C "$REPOSITORY_ROOT" rev-parse HEAD)}"
build_time="${PF_BUILD_TIME:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
runtime_environment="${PF_ENVIRONMENT:-local}"
backend_url="${PF_PUBLIC_BACKEND_URL-http://localhost:8080}"
google_client_id="${PF_GOOGLE_CLIENT_ID:-}"

[[ "$application_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Ongeldige applicatieversie." >&2; exit 1; }
[[ "$git_revision" =~ ^[0-9a-f]{40}$ ]] || { echo "Ongeldige Git-revisie." >&2; exit 1; }
[[ "$build_time" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] || { echo "Ongeldige UTC-buildtijd." >&2; exit 1; }
[[ "$runtime_environment" =~ ^(local|acceptance|production)$ ]] || { echo "Ongeldige omgeving." >&2; exit 1; }

cd "$FRONTEND_ROOT"
if [[ -d build/web ]]; then
  find build/web -maxdepth 1 -type f -name 'main.*.js' -delete
fi
flutter build web \
  --release \
  --pwa-strategy=none \
  --no-web-resources-cdn \
  --dart-define="PF_APPLICATION_VERSION=$application_version" \
  --dart-define="PF_GIT_REVISION=$git_revision" \
  --dart-define="PF_BUILD_TIME=$build_time" \
  --dart-define="PF_ENVIRONMENT=$runtime_environment" \
  --dart-define="PF_PUBLIC_BACKEND_URL=$backend_url" \
  --dart-define="PF_GOOGLE_CLIENT_ID=$google_client_id"

rm -f build/web/flutter_service_worker.js
bundle_hash="$(shasum -a 256 build/web/main.dart.js | awk '{print substr($1, 1, 16)}')"
bundle_name="main.$bundle_hash.js"
mv build/web/main.dart.js "build/web/$bundle_name"
perl -pi -e "s/main\\.dart\\.js/$bundle_name/g" build/web/flutter_bootstrap.js

frontend_identity="$application_version+${git_revision:0:12}"
printf '{"applicationVersion":"%s","apiVersion":"1","gitRevision":"%s","buildTime":"%s","environment":"%s","frontendBuildIdentity":"%s"}\n' \
  "$application_version" "$git_revision" "$build_time" "$runtime_environment" "$frontend_identity" \
  > build/web/version.json

test -f "build/web/$bundle_name"
grep -q "$bundle_name" build/web/flutter_bootstrap.js
! grep -q 'main\.dart\.js' build/web/flutter_bootstrap.js
printf '%s\n' "$bundle_name"
