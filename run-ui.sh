#!/usr/bin/env bash
# run-ui.sh — Helper to install and run the AppBana UI (Vite dev server or build assets)
#
# Usage:
#   ./run-ui.sh            # start dev server (default)
#   ./run-ui.sh dev        # start dev server
#   ./run-ui.sh build      # production build (outputs to app-bana-ui/dist)
#   ./run-ui.sh preview    # serve production build (vite preview)
#   ./run-ui.sh clean      # remove node_modules + dist
#   ./run-ui.sh help       # show help
#
# Options / Env:
#   UI_DIR=app-bana-ui        Override UI module directory
#   NODE_VERSION=20           Target Node version (if using nvm). Defaults 20 (LTS) if no .nvmrc.
#   UI_PORT=5173              Override dev server port (Vite default 5173)
#   USE_SYSTEM_NODE=1         Skip nvm detection (use whatever node is on PATH)
#   EXTRA_ARGS="--host"        Extra args passed to vite (dev/preview)
#
# Behavior:
#   1. Attempts to source nvm (unless USE_SYSTEM_NODE=1)
#   2. Ensures required Node version (>=18.17) — installs/uses via nvm if present
#   3. Installs deps (npm ci if package-lock.json exists else npm install)
#   4. Runs requested mode
# NOTE: Guard array expansions carefully under set -u.
set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UI_DIR_REL="${UI_DIR:-app-bana-ui}"
UI_DIR="${SCRIPT_DIR}/${UI_DIR_REL}"
ACTION="${1:-dev}"
TARGET_NODE="${NODE_VERSION:-}"  # may be empty => resolved below
MIN_NODE_MAJOR=18
MIN_NODE_MINOR=17
COLOR_RESET='\033[0m'
COLOR_INFO='\033[1;34m'
COLOR_WARN='\033[1;33m'
COLOR_ERR='\033[1;31m'
COLOR_OK='\033[1;32m'

log() { echo -e "${COLOR_INFO}[run-ui]${COLOR_RESET} $*"; }
warn() { echo -e "${COLOR_WARN}[run-ui][warn]${COLOR_RESET} $*"; }
err() { echo -e "${COLOR_ERR}[run-ui][error]${COLOR_RESET} $*" >&2; }
ok() { echo -e "${COLOR_OK}[run-ui]${COLOR_RESET} $*"; }

usage() { grep '^# ' "$0" | sed 's/^# //'; }

if [[ "$ACTION" == help || "$ACTION" == --help || "$ACTION" == -h ]]; then
  usage; exit 0;
fi

if [[ ! -d "$UI_DIR" ]]; then
  err "UI directory not found: $UI_DIR"; exit 1;
fi

# 1. Load nvm unless skipped
if [[ -z "${USE_SYSTEM_NODE:-}" ]]; then
  if command -v nvm >/dev/null 2>&1; then
    log "nvm already on PATH ($(nvm --version))"
  else
    # Attempt typical install locations
    for CAND in "$HOME/.nvm" \
                "/opt/homebrew/opt/nvm" \
                "/usr/local/opt/nvm"; do
      if [[ -s "$CAND/nvm.sh" ]]; then
        # shellcheck disable=SC1090
        . "$CAND/nvm.sh" && log "Sourced nvm from $CAND" && break
      fi
    done
  fi
else
  warn "Skipping nvm detection (USE_SYSTEM_NODE=1)"
fi

# 2. Determine target node version
if [[ -f "$UI_DIR/.nvmrc" ]]; then
  TARGET_NODE="$(<"$UI_DIR/.nvmrc")"
fi
if [[ -z "$TARGET_NODE" ]]; then
  TARGET_NODE=20
fi

have_nvm=0
if command -v nvm >/dev/null 2>&1; then have_nvm=1; fi

if [[ $have_nvm -eq 1 && -z "${USE_SYSTEM_NODE:-}" ]]; then
  log "Ensuring Node $TARGET_NODE via nvm"
  nvm install "$TARGET_NODE" >/dev/null
  nvm use "$TARGET_NODE" >/dev/null
fi

if ! command -v node >/dev/null 2>&1; then
  err "node is not available. Install Node >=18.17 or enable nvm."; exit 1;
fi

NODE_VER_RAW="$(node -v | sed 's/^v//')"
NODE_MAJOR="${NODE_VER_RAW%%.*}"
NODE_MINOR_PART="${NODE_VER_RAW#*.}"; NODE_MINOR="${NODE_MINOR_PART%%.*}"

version_ok=1
if (( NODE_MAJOR < MIN_NODE_MAJOR )); then version_ok=0; fi
if (( NODE_MAJOR == MIN_NODE_MAJOR && NODE_MINOR < MIN_NODE_MINOR )); then version_ok=0; fi
if (( version_ok == 0 )); then
  warn "Detected Node v${NODE_VER_RAW} < required ${MIN_NODE_MAJOR}.${MIN_NODE_MINOR}. Some tooling may fail."
fi

ok "Using Node $(node -v), npm $(npm -v)"

cd "$UI_DIR"

# 3. Install deps
if [[ "$ACTION" == clean ]]; then
  log "Cleaning node_modules and dist..."
  rm -rf node_modules dist
  ok "Clean complete."; exit 0
fi

if [[ -f package-lock.json ]]; then
  log "Installing dependencies (npm ci)"
  npm ci --no-audit --no-fund
else
  log "Installing dependencies (npm install)"
  npm install --no-audit --no-fund
fi

declare -a VITE_ARGS=()
[[ -n "${UI_PORT:-}" ]] && VITE_ARGS+=(--port "${UI_PORT}")
# shellcheck disable=SC2206 # we intentionally word-split EXTRA_ARGS
[[ -n "${EXTRA_ARGS:-}" ]] && VITE_ARGS+=(${EXTRA_ARGS})

join_args() { # echo joined args for logging only
  if (( ${#VITE_ARGS[@]} )); then printf '%s ' "${VITE_ARGS[@]}"; fi
}

# Kill any existing Vite dev server on port 5173 (or UI_PORT if set)
kill_existing_server() {
  local PORT="${UI_PORT:-5173}"
  log "Checking for existing server on port $PORT..."
  
  # Find process using the port (macOS/Linux compatible)
  local PID
  if command -v lsof >/dev/null 2>&1; then
    PID=$(lsof -ti tcp:"$PORT" 2>/dev/null || true)
  else
    # Fallback for systems without lsof
    PID=$(netstat -anp tcp 2>/dev/null | grep "LISTEN.*:$PORT" | awk '{print $9}' | cut -d'/' -f1 || true)
  fi
  
  # If still not found, try a best-effort grep for vite on this port (helps when lsof is missing or permissions block lookup)
  if [[ -z "$PID" ]]; then
    PID=$(ps -ef | grep "vite" | grep -v grep | grep ":$PORT" | awk '{print $2}' || true)
  fi

  if [[ -n "$PID" ]]; then
    warn "Found existing server (PID: $PID) on port $PORT. Stopping..."
    kill "$PID" 2>/dev/null || kill -9 "$PID" 2>/dev/null || true
    sleep 1
    ok "Existing server stopped"
  else
    log "No existing server found on port $PORT"
  fi
}

case "$ACTION" in
  dev)
    kill_existing_server
    log "Starting Vite dev server..."
    log "Command: npm run dev -- $(join_args)"
    if (( ${#VITE_ARGS[@]} )); then
      npm run dev -- "${VITE_ARGS[@]}"
    else
      npm run dev
    fi
    ;;
  build)
    log "Building production assets..."
    npm run build
    ok "Build complete. Output: dist/"
    ;;
  preview)
    kill_existing_server
    log "Previewing production build (will build if dist missing)..."
    [[ -d dist ]] || npm run build
    log "Command: npm run preview -- $(join_args)"
    if (( ${#VITE_ARGS[@]} )); then
      npm run preview -- "${VITE_ARGS[@]}"
    else
      npm run preview
    fi
    ;;
  *)
    err "Unknown action: $ACTION"; usage; exit 1;
    ;;
esac
