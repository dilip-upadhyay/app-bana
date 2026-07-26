#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
PORT=5175

echo "[start-runtime] Starting AppBana Runtime on port $PORT..."

# Kill anything on the port
lsof -ti ":$PORT" 2>/dev/null | xargs kill -9 2>/dev/null || true

cd "$ROOT_DIR/app-bana-runtime"

# Ensure node_modules
if [ ! -d "node_modules" ]; then
  echo "[start-runtime] Installing dependencies..."
  cd "$ROOT_DIR"
  pnpm install --ignore-scripts
  cd "$ROOT_DIR/app-bana-runtime"
fi

echo "[start-runtime] Launching Vite dev server on port $PORT..."
npx vite --port "$PORT"
