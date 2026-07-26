#!/bin/bash
# =====================================================================
# start-studio.sh  -  Start the AI-native Studio on port 5174
# =====================================================================
cd "$(dirname "$0")/.."

STUDIO_PORT=5174

echo "=========================================="
echo "[studio] Starting on port $STUDIO_PORT"
echo "=========================================="

# Ensure pnpm is available
if ! command -v pnpm &>/dev/null; then
  echo "Installing pnpm..."
  npm install -g pnpm >/dev/null 2>&1
fi

# Kill any existing process on port 5174
EXISTING=$(lsof -ti ":$STUDIO_PORT" 2>/dev/null)
if [ -n "$EXISTING" ]; then
  echo "Stopping existing process on port $STUDIO_PORT (PID $EXISTING)"
  kill -9 "$EXISTING" 2>/dev/null || true
fi

# Install workspace deps if needed
if [ ! -d "node_modules" ]; then
  echo "Installing dependencies..."
  pnpm install --ignore-scripts
fi

echo "Starting AppBana Studio at http://localhost:$STUDIO_PORT"
cd app-bana-studio && npx vite --port $STUDIO_PORT
