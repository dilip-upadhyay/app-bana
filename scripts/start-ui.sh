#!/usr/bin/env bash
# =====================================================================
# start-ui.sh  -  Restart the AppBana Studio UI on port 5173
#
# What it does:
#   1. Stops any Vite dev server already running on port 5173
#   2. Ensures Node dependencies are installed (npm install if missing)
#   3. Launches the Vite dev server
# =====================================================================
set -euo pipefail

UI_PORT="${UI_PORT:-5173}"
UI_DIR="${UI_DIR:-app-bana-ui}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

echo "=========================================="
echo "[ui] Restarting on port $UI_PORT"
echo "=========================================="

# --- Step 1: stop any existing process on UI_PORT --------------------
echo "[1/3] Stopping any existing UI process on port $UI_PORT..."
if command -v lsof >/dev/null 2>&1; then
    PIDS=$(lsof -ti:"$UI_PORT" 2>/dev/null || true)
    if [ -n "$PIDS" ]; then
        echo "   Killing PID(s): $PIDS"
        kill -9 $PIDS 2>/dev/null || true
    fi
fi

# --- Step 2: ensure node dependencies --------------------------------
echo "[2/3] Ensuring Node dependencies are installed..."
if ! command -v node >/dev/null 2>&1; then
    echo "   ERROR: Node.js is not installed or not on PATH."
    exit 1
fi
if ! command -v npm >/dev/null 2>&1; then
    echo "   ERROR: npm is not installed or not on PATH."
    exit 1
fi
cd "$ROOT_DIR/$UI_DIR"
if [ ! -d node_modules ]; then
    echo "   Installing dependencies (this may take a minute)..."
    if [ -f package-lock.json ]; then
        npm ci
    else
        npm install
    fi
fi
echo "   node_modules: present"

# --- Step 3: launch --------------------------------------------------
echo "[3/3] Launching Vite dev server on port $UI_PORT..."
echo "   URL: http://localhost:$UI_PORT"
echo "   Press Ctrl+C to stop."
echo "=========================================="

exec npm run dev
