#!/usr/bin/env bash
#
# CereBroZen — launch the whole stack and open the front-ends.
#
#   ./launch.sh          build from current source, start, wait for health, open browsers
#   ./launch.sh down     stop the stack — PRESERVES DATA (never `down -v`)
#   ./launch.sh logs     tail all service logs
#
# Why a wrapper over `docker compose up`: it frees a stray local preview server that would
# fight compose for a port, health-checks every surface, opens the three front-ends, and —
# the important one — its `down` is plain `down`, NOT the `down -v` that `npm run stack:down`
# uses. `-v` deletes the engine's Postgres volume (conversations, escalations, vector index).
# This script will never do that to you.

set -euo pipefail
cd "$(dirname "$0")"

COMPOSE="docker compose -f docker-compose.yml"

open_url() {
  if command -v open >/dev/null 2>&1; then open "$1"          # macOS
  elif command -v xdg-open >/dev/null 2>&1; then xdg-open "$1" # Linux
  else echo "  (open $1 manually)"; fi
}

case "${1:-up}" in
  down)
    echo "Stopping the stack (data preserved — no -v)…"
    $COMPOSE down --remove-orphans
    exit 0 ;;
  logs)
    exec $COMPOSE logs --tail=120 -f ;;
esac

# 0. Docker must be running.
docker info >/dev/null 2>&1 || {
  echo "Docker isn't running. Start Docker Desktop and retry."; exit 1; }

# 1. Free a stray NON-docker process squatting on a front-end port (a leftover `next` dev
#    server makes compose fail on the port bind — see docs/DEVELOPING.md). Compose-published
#    ports are owned by docker-proxy and left alone.
free_port() {
  local port="$1" pid
  pid="$(lsof -ti "tcp:${port}" -sTCP:LISTEN 2>/dev/null | head -1 || true)"
  [ -z "$pid" ] && return 0
  if docker ps --format '{{.Ports}}' 2>/dev/null | grep -q ":${port}->"; then
    return 0  # this port belongs to the running stack — don't touch it
  fi
  echo "  freeing port ${port} (stray pid ${pid})"
  kill "$pid" 2>/dev/null || true
}
for p in 3000 3001 3002 3060; do free_port "$p"; done   # 3060 = the scratch preview server

# 2. Build from current source + start + wait for health. Keeps the db volume.
echo "Building & starting the stack from your current source (first run is slow, then cached)…"
$COMPOSE up --build -d --wait

# 3. Verify every surface answers.
echo
echo "Health:"
check() {  # url  label — retry briefly: `--wait` clears on the container's internal
           # healthcheck, and a published port can lag it by a moment on first boot.
  local url="$1" label="$2" code="000" i
  for i in 1 2 3 4 5; do
    code="$(curl -s -o /dev/null -w '%{http_code}' -m 10 "$url" 2>/dev/null)" || code="000"
    [ "$code" = "200" ] && break
    sleep 2
  done
  printf "  %-9s %-30s %s\n" "$label" "$url" "$code"
}
check "http://localhost:8000/health" engine
check "http://localhost:8100/health" platform
check "http://localhost:3000"        landing
check "http://localhost:3001"        admin
check "http://localhost:3002"        app

# 4. Open the three front-ends.
echo
echo "Opening the front-ends…"
open_url "http://localhost:3000"   # landing / marketing
open_url "http://localhost:3001"   # admin console
open_url "http://localhost:3002"   # employee web app

cat <<'NOTE'

Up.
  landing  http://localhost:3000
  admin    http://localhost:3001   sign in: admin@cerebrozen.in / admin12345
  app      http://localhost:3002   sign in: demo@cerebrozen.in  / demo12345

  Stop:  ./launch.sh down   (preserves data — never runs `down -v`)
  Logs:  ./launch.sh logs
NOTE
