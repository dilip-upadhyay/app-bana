#!/usr/bin/env bash
# =====================================================================
# start-everything.sh  -  Restart all AppBana services (macOS / Linux)
#
# Orchestrates the three per-module scripts in the correct order.
# Each module script is fully self-contained (stops old, ensures deps,
# builds if needed, launches). This script backgrounds each one with
# logs redirected to dev-logs/, and waits for each service to be
# reachable before starting the next.
#
# Order:
#   1. AI Builder  (port 8081)  <- also brings up Qdrant + PostgreSQL
#   2. Backend     (port 8080)
#   3. UI          (port 5173)
#
# Logs:
#   dev-logs/ai-builder.log
#   dev-logs/backend.log
#   dev-logs/ui.log
#
# Stop everything:
#   kill $(cat dev-logs/*.pid)
# =====================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

if [ ! -f pom.xml ]; then
    echo "ERROR: could not locate repo root (pom.xml missing)."
    exit 1
fi

LOG_DIR="$ROOT_DIR/dev-logs"
mkdir -p "$LOG_DIR"

echo "=========================================="
echo "Starting All AppBana Services"
echo "=========================================="

wait_for_port() {
    local port="$1"
    local name="$2"
    local timeout="${3:-180}"
    local elapsed=0
    echo "   Waiting for $name on port $port..."
    while ! (echo > "/dev/tcp/127.0.0.1/$port") >/dev/null 2>&1; do
        sleep 2
        elapsed=$((elapsed + 2))
        if [ "$elapsed" -ge "$timeout" ]; then
            echo "   ERROR: $name did not become ready within ${timeout}s"
            echo "   See $LOG_DIR/${name}.log for details."
            exit 1
        fi
    done
    echo "   $name is up."
}

launch() {
    local name="$1"
    local script="$2"
    echo "Launching $name (log: $LOG_DIR/$name.log)..."
    nohup bash "$SCRIPT_DIR/$script" > "$LOG_DIR/$name.log" 2>&1 &
    echo $! > "$LOG_DIR/$name.pid"
}

echo "[1/3] AI Builder"
launch ai-builder start-ai-builder.sh
wait_for_port 8081 ai-builder

echo "[2/3] Backend"
launch backend start-backend.sh
wait_for_port 8080 backend

echo "[3/3] UI"
launch ui start-ui.sh
wait_for_port 5173 ui 60

echo "=========================================="
echo "All services launched:"
echo "   AI Builder: http://localhost:8081/health   (PID $(cat "$LOG_DIR/ai-builder.pid"))"
echo "   Backend:    http://localhost:8080/health   (PID $(cat "$LOG_DIR/backend.pid"))"
echo "   UI:         http://localhost:5173          (PID $(cat "$LOG_DIR/ui.pid"))"
echo ""
echo "Tail logs:  tail -f $LOG_DIR/*.log"
echo "Stop all:   kill \$(cat $LOG_DIR/*.pid)"
echo "=========================================="
