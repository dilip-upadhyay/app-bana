#!/usr/bin/env bash
# Run script for AppBana Studio (Angular SSR)
# This script rebuilds the UI workspace and launches the Studio server.
#
# Usage:
#   ./run.sh                  # build (incremental) and run on default port 4000
#   ./run.sh --clean          # clean build then run
#   ./run.sh --reinstall      # reinstall dependencies before building
#   ./run.sh --port 5000      # run on a custom port
#   ./run.sh --open           # open the app in the default browser after starting
#
# Notes:
# - Uses PORT environment variable if provided; otherwise defaults to 4000.
# - Requires Node/npm and Angular CLI dependencies to be installed (handled by build.sh).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UI_DIR="$SCRIPT_DIR/ui"
PORT="${PORT:-4000}"
OPEN=false
CLEAN=false
REINSTALL=false

# Parse args
while [[ $# -gt 0 ]]; do
  case "$1" in
    --open)
      OPEN=true
      shift
      ;;
    --clean)
      CLEAN=true
      shift
      ;;
    --reinstall)
      REINSTALL=true
      shift
      ;;
    --port)
      if [[ -n "${2:-}" ]]; then
        PORT="$2"
        shift 2
      else
        echo "--port requires a value" >&2
        exit 1
      fi
      ;;
    *)
      echo "Unknown option: $1" >&2
      echo "Usage: $0 [--clean] [--reinstall] [--port <n>] [--open]" >&2
      exit 1
      ;;
  esac
done

# Ensure UI workspace exists
if [[ ! -d "$UI_DIR" ]]; then
  echo "UI workspace not found at: $UI_DIR" >&2
  exit 1
fi

# Build step
BUILD_ARGS=()
if $CLEAN; then BUILD_ARGS+=("--clean"); fi
if $REINSTALL; then BUILD_ARGS+=("--reinstall"); fi

if [[ ${#BUILD_ARGS[@]} -gt 0 ]]; then
  "$SCRIPT_DIR/build.sh" "${BUILD_ARGS[@]}"
else
  "$SCRIPT_DIR/build.sh"
fi

# Run Studio SSR server
pushd "$UI_DIR" >/dev/null

URL="http://localhost:${PORT}"
echo "Starting Studio SSR server on ${URL} ..."

# If --open is provided on macOS, open the browser a couple seconds after start
if $OPEN && [[ "$(uname -s)" == "Darwin" ]]; then
  (sleep 2 && open "$URL") &
fi

# Run in foreground; use PORT to override default
exec env PORT="$PORT" npm run serve:ssr:studio

popd >/dev/null
