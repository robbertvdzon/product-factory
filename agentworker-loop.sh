#!/usr/bin/env bash
set -u

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT" || exit 1
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"

WORK_DIR="$ROOT/work"
LOCK_FILE="$WORK_DIR/.agentworker-loop.pid"
mkdir -p "$WORK_DIR"

if [[ -f "$LOCK_FILE" ]] && kill -0 "$(cat "$LOCK_FILE" 2>/dev/null)" 2>/dev/null; then
  echo "[agentworker] er draait al een Product Factory-agentworker (PID $(cat "$LOCK_FILE"))."
  exit 1
fi
echo $$ > "$LOCK_FILE"
cleanup() { rm -f "$LOCK_FILE"; }
trap cleanup EXIT
trap 'echo "[agentworker] gestopt."; exit 0' INT TERM

if ! command -v codex >/dev/null 2>&1; then
  echo "[agentworker] Codex CLI ontbreekt in PATH." >&2
  exit 1
fi
if ! codex login status >/dev/null 2>&1; then
  echo "[agentworker] geen geldige Codex-login; voer eerst 'codex login' uit." >&2
  exit 1
fi
if [[ ! -f "$ROOT/secrets.env" ]] || [[ -z "$(awk -F= '$1 == "PF_AGENT_WORKER_TOKEN" {print substr($0, index($0, "=") + 1)}' "$ROOT/secrets.env" | tail -1)" ]]; then
  echo "[agentworker] PF_AGENT_WORKER_TOKEN ontbreekt in secrets.env." >&2
  exit 1
fi

while true; do
  echo "[agentworker] $(date '+%Y-%m-%d %H:%M:%S') — worker bouwen en starten."
  mvn -q -DskipTests -pl productfactory-contracts,productfactory-common install || {
    echo "[agentworker] gedeelde modules konden niet worden gebouwd; nieuwe poging over 10s." >&2
    sleep 10
    continue
  }
  mvn -pl agentworker spring-boot:run
  echo "[agentworker] worker gestopt; herstart over 5s."
  sleep 5
done
