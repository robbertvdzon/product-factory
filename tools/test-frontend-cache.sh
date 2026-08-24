#!/usr/bin/env bash
set -euo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_SCRIPT="$REPOSITORY_ROOT/tools/build-frontend.sh"
FRONTEND_ROOT="$REPOSITORY_ROOT/product-factory-frontend"
revision="$(git -C "$REPOSITORY_ROOT" rev-parse HEAD)"

bundle_a="$(PF_GIT_REVISION="$revision" PF_BUILD_TIME=2026-08-24T18:00:00Z "$BUILD_SCRIPT" | tail -1)"
bundle_b="$(PF_GIT_REVISION="$revision" PF_BUILD_TIME=2026-08-24T18:00:01Z "$BUILD_SCRIPT" | tail -1)"

[[ "$bundle_a" != "$bundle_b" ]] || { echo "Twee inhoudelijk verschillende builds kregen dezelfde bundle-URL." >&2; exit 1; }
grep -q "$bundle_b" "$FRONTEND_ROOT/build/web/flutter_bootstrap.js"
! grep -q "$bundle_a" "$FRONTEND_ROOT/build/web/flutter_bootstrap.js"

docker run --rm \
  --add-host product-factory-backend:127.0.0.1 \
  -v "$FRONTEND_ROOT/nginx.conf:/etc/nginx/conf.d/default.conf:ro" \
  nginx:1.28.0-alpine nginx -t >/dev/null

test_directory="$(mktemp -d)"
container_id="$(docker run -d --rm -p 127.0.0.1::8080 \
  --add-host product-factory-backend:127.0.0.1 \
  -v "$FRONTEND_ROOT/build/web:/usr/share/nginx/html:ro" \
  -v "$FRONTEND_ROOT/nginx.conf:/etc/nginx/conf.d/default.conf:ro" \
  nginx:1.28.0-alpine)"
cleanup() {
  docker stop "$container_id" >/dev/null 2>&1 || true
  rm -rf "$test_directory"
}
trap cleanup EXIT
published_port="$(docker port "$container_id" 8080/tcp | awk -F: '{print $NF}')"
base_url="http://127.0.0.1:$published_port"

for attempt in {1..20}; do
  if curl --fail --silent "$base_url/" >/dev/null; then break; fi
  sleep 0.25
done

curl --silent --dump-header "$test_directory/index.headers" --output /dev/null "$base_url/"
curl --silent --dump-header "$test_directory/bundle.headers" --output /dev/null "$base_url/$bundle_b"
curl --silent --dump-header "$test_directory/version.headers" --output /dev/null "$base_url/version.json"
curl --silent --dump-header "$test_directory/worker.headers" --output "$test_directory/worker.js" "$base_url/flutter_service_worker.js"
curl --silent --dump-header "$test_directory/old.headers" --output /dev/null "$base_url/$bundle_a"
curl --silent --dump-header "$test_directory/spa.headers" --output /dev/null "$base_url/producten"

grep -qi '^Cache-Control: no-cache' "$test_directory/index.headers"
grep -qi '^Cache-Control: public, max-age=31536000, immutable' "$test_directory/bundle.headers"
grep -qi '^Cache-Control: no-store' "$test_directory/version.headers"
grep -qi '^Cache-Control: no-store' "$test_directory/worker.headers"
grep -q 'caches.keys' "$test_directory/worker.js"
grep -q 'unregister' "$test_directory/worker.js"
grep -q '^HTTP/1.1 404' "$test_directory/old.headers"
grep -q '^HTTP/1.1 200' "$test_directory/spa.headers"
grep -qi '^X-Content-Type-Options: nosniff' "$test_directory/index.headers"
grep -qi '^X-Frame-Options: DENY' "$test_directory/index.headers"
grep -qi '^Referrer-Policy: same-origin' "$test_directory/index.headers"
grep -qi '^Content-Security-Policy:' "$test_directory/index.headers"

printf 'cachetest: %s -> %s\n' "$bundle_a" "$bundle_b"
