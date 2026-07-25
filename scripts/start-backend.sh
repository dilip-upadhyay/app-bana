#!/usr/bin/env bash
# =====================================================================
# start-backend.sh  -  Restart the AppBana core API on port 8080
#
# What it does:
#   1. Stops any backend process already running on port 8080
#   2. Ensures PostgreSQL is up
#   3. Builds the app-bana-service module (with its parent deps)
#   4. Launches the service on port 8080
# =====================================================================
set -euo pipefail

BE_PORT="${BE_PORT:-8080}"
PG_PORT="${PG_PORT:-5432}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

echo "=========================================="
echo "[backend] Restarting on port $BE_PORT"
echo "=========================================="

# --- Pre-flight: required tools -------------------------------------
command -v java >/dev/null 2>&1 || { echo "ERROR: Java JDK not found on PATH. Install JDK 21+ and retry."; exit 1; }
command -v mvn  >/dev/null 2>&1 || { echo "ERROR: Maven not found on PATH. Install Apache Maven 3.9+ and retry."; exit 1; }

# --- Step 1: stop any existing process on BE_PORT --------------------
echo "[1/4] Stopping any existing backend process on port $BE_PORT..."
if command -v lsof >/dev/null 2>&1; then
    PIDS=$(lsof -ti:"$BE_PORT" 2>/dev/null || true)
    if [ -n "$PIDS" ]; then
        echo "   Killing PID(s): $PIDS"
        kill -9 $PIDS 2>/dev/null || true
    fi
fi

# --- Step 2: ensure PostgreSQL is running ----------------------------
echo "[2/4] Ensuring PostgreSQL is running..."
if ! command -v docker >/dev/null 2>&1; then
    echo "   ERROR: Docker is not installed or not on PATH."
    exit 1
fi
if ! docker info >/dev/null 2>&1; then
    echo "   ERROR: Docker daemon is not running. Start Docker Desktop and retry."
    exit 1
fi
# PostgreSQL -- try to start; if container missing, create it
if ! docker start appbana-postgres >/dev/null 2>&1; then
    echo "   Creating PostgreSQL container..."
    docker run -d \
        --name appbana-postgres \
        -e POSTGRES_DB=appbana \
        -e POSTGRES_USER=appbana \
        -e POSTGRES_PASSWORD=appbana_dev_2026 \
        -p "$PG_PORT:5432" \
        -v appbana-postgres-data:/var/lib/postgresql/data \
        postgres:16-alpine >/dev/null
    sleep 3
fi
echo "   PostgreSQL: running on port $PG_PORT"

# --- Step 3: build the module ----------------------------------------
echo "[3/4] Building app-bana-service module..."
cd "$ROOT_DIR"
if ! mvn -q -pl app-bana-service -am -DskipTests install; then
    if [ ! -f "app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar" ]; then
        echo "   ERROR: Build failed and no existing jar found."
        exit 1
    fi
    echo "   WARNING: Build failed, using existing jar."
fi

# --- Step 4: launch --------------------------------------------------
echo "[4/4] Launching Backend on port $BE_PORT..."
echo "   Health: http://localhost:$BE_PORT/health"
echo "   Press Ctrl+C to stop."
echo "=========================================="

cd app-bana-service
exec java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
