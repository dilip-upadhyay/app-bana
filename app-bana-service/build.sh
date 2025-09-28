#!/usr/bin/env bash
# Build script for AppBana UI workspace (Angular)
# Usage:
#   ./build.sh              # install deps if needed and build all UI projects
#   ./build.sh --clean      # clean dist before building
#   ./build.sh --reinstall  # force reinstall npm deps before building

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UI_DIR="$SCRIPT_DIR/ui"
DIST_DIR="$UI_DIR/dist"

CLEAN=false
REINSTALL=false

for arg in "$@"; do
  case "$arg" in
    --clean)
      CLEAN=true
      ;;
    --reinstall)
      REINSTALL=true
      ;;
    *)
      echo "Unknown option: $arg" >&2
      echo "Usage: $0 [--clean] [--reinstall]" >&2
      exit 1
      ;;
  esac
done

if [[ ! -d "$UI_DIR" ]]; then
  echo "UI workspace not found at: $UI_DIR" >&2
  exit 1
fi

pushd "$UI_DIR" >/dev/null

if $CLEAN; then
  echo "Cleaning UI dist directory..."
  rm -rf "$DIST_DIR"
fi

# Install dependencies only if needed or forced
if $REINSTALL; then
  if [[ -f package-lock.json ]]; then
    echo "Reinstalling npm dependencies with npm ci..."
    npm ci
  else
    echo "Reinstalling npm dependencies with npm install..."
    npm install
  fi
elif [[ ! -d node_modules ]]; then
  if [[ -f package-lock.json ]]; then
    echo "Installing npm dependencies with npm ci..."
    npm ci
  else
    echo "Installing npm dependencies with npm install..."
    npm install
  fi
else
  echo "Dependencies already installed. Skipping npm install. Use --reinstall to force."
fi

# Build order chosen based on prior successful compilation
echo "Building Angular libraries..."
./node_modules/.bin/ng build ui-material
./node_modules/.bin/ng build ui-schema
./node_modules/.bin/ng build designer
./node_modules/.bin/ng build runtime

echo "Building Studio app..."
./node_modules/.bin/ng build studio

popd >/dev/null

echo "Build finished. Artifacts are in: $DIST_DIR"

