#!/usr/bin/env bash
#
# CereBroZen — launch the whole stack and open the front-ends.
#
#   ./launch.sh          production-style: build all from source, start, open browsers
#   ./launch.sh dev      backends on compose + front-ends as `next dev` — the DEMO LOGIN
#                        chips appear (they're compiled OUT of the production build on purpose)
#   ./launch.sh down     stop everything — PRESERVES DATA (never `down -v`)
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
PIDFILE=".launch-dev.pids"   # front-end dev-server pids, so `down` can stop them

open_url() {
  if command -v open >/dev/null 2>&1; then open "$1"          # macOS
  elif command -v xdg-open >/dev/null 2>&1; then xdg-open "$1" # Linux
  else echo "  (open $1 manually)"; fi
}

# Free a stray NON-docker process squatting on a front-end port (a leftover `next` dev
# server makes compose fail on the port bind — see docs/DEVELOPING.md). Compose-published
# ports are owned by docker-proxy and left alone.
free_port() {
  local port="$1" pid
  pid="$(lsof -ti "tcp:${port}" -sTCP:LISTEN 2>/dev/null | head -1 || true)"
  [ -z "$pid" ] && return 0
  if docker ps --format '{{.Ports}}' 2>/dev/null | grep -q ":${port}->"; then return 0; fi
  echo "  freeing port ${port} (stray pid ${pid})"
  kill "$pid" 2>/dev/null || true
}

# Retry a health check: `--wait` clears on a container's internal check, and a published
# port can lag it by a moment on first boot (that briefly printed a scary 000).
check() {  # url  label
  local url="$1" label="$2" code="000" i
  for i in 1 2 3 4 5 6; do
    code="$(curl -s -o /dev/null -w '%{http_code}' -m 10 "$url" 2>/dev/null)" || code="000"
    [ "$code" = "200" ] && break
    sleep 2
  done
  printf "  %-9s %-30s %s\n" "$label" "$url" "$code"
}

case "${1:-up}" in
  down)
    if [ -f "$PIDFILE" ]; then
      while read -r pid; do kill "$pid" 2>/dev/null || true; done < "$PIDFILE"
      rm -f "$PIDFILE"
    fi
    for p in 3000 3001 3002; do free_port "$p"; done  # catch orphaned dev servers
    echo "Stopping the stack (data preserved — no -v)…"
    $COMPOSE down --remove-orphans
    exit 0 ;;
  logs)
    exec $COMPOSE logs --tail=120 -f ;;
  dev)
    docker info >/dev/null 2>&1 || { echo "Docker isn't running."; exit 1; }
    echo "Starting backends on compose (db, redis, engine, platform)…"
    $COMPOSE up -d --wait db redis engine platform
    echo "Freeing 3000-3002 for the dev servers…"
    $COMPOSE stop web admin app >/dev/null 2>&1 || true
    for p in 3000 3001 3002 3060; do free_port "$p"; done
    : > "$PIDFILE"
    start_dev() {  # dir  port  label
      # Background the whole subshell; $! is then its pid, and this line runs at repo root
      # so $PIDFILE resolves correctly (an earlier "../../" wrote outside the repo). `exec`
      # makes the subshell BECOME npm so the recorded pid is the one to kill.
      ( cd "apps/$1" && exec npm run dev >"/tmp/cere-dev-$1.log" 2>&1 ) &
      echo $! >> "$PIDFILE"
      echo "  $3 → http://localhost:$2   (log: /tmp/cere-dev-$1.log)"
    }
    echo "Starting front-ends in DEV mode — demo-login chips are visible in dev…"
    start_dev web   3000 landing
    start_dev admin 3001 admin
    start_dev app   3002 app
    echo
    echo "Health (dev servers compile on first hit — first 200 can take ~20s):"
    check "http://localhost:8100/health" platform
    check "http://localhost:3000" landing
    check "http://localhost:3001" admin
    check "http://localhost:3002" app
    echo
    echo "Opening the front-ends…"
    open_url "http://localhost:3000"; open_url "http://localhost:3001"; open_url "http://localhost:3002"
    cat <<'NOTE'

Up in DEV mode — the demo-login chips now render on the sign-in screens.
  landing  http://localhost:3000
  admin    http://localhost:3001   demo chips: Dev Admin / Dana Okafor
  app      http://localhost:3002   demo chip:  Alex Rivera (member)

  Stop:  ./launch.sh down   (stops dev servers + compose, preserves data)
  Logs:  tail -f /tmp/cere-dev-{web,admin,app}.log
NOTE
    exit 0 ;;
esac

# 0. Docker must be running.
docker info >/dev/null 2>&1 || {
  echo "Docker isn't running. Start Docker Desktop and retry."; exit 1; }

# 1. Free stray non-docker squatters on the front-end ports (see free_port, above).
for p in 3000 3001 3002 3060; do free_port "$p"; done   # 3060 = the scratch preview server

# 2. Build from current source + start + wait for health. Keeps the db volume.
echo "Building & starting the stack from your current source (first run is slow, then cached)…"
$COMPOSE up --build -d --wait

# 3. Verify every surface answers.
echo
echo "Health:"
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
