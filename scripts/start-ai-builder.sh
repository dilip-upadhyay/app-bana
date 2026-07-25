#!/usr/bin/env bash
# =====================================================================
# start-ai-builder.sh  -  Restart the AI Builder service on port 8081
#
# What it does:
#   1. Stops any AI Builder / Qdrant process already running
#   2. Ensures Docker dependencies (Qdrant, PostgreSQL) are up
#   3. Ensures OPENAI_API_KEY is set
#   4. Builds the ai-builder module (with its parent deps)
#   5. Launches the service on port 8081
# =====================================================================
set -euo pipefail

AI_PORT="${AI_PORT:-8081}"
QDRANT_HTTP_PORT="${QDRANT_HTTP_PORT:-6333}"
QDRANT_GRPC_PORT="${QDRANT_GRPC_PORT:-6334}"
PG_PORT="${PG_PORT:-5432}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

echo "=========================================="
echo "[ai-builder] Restarting on port $AI_PORT"
echo "=========================================="

# --- Pre-flight: required tools -------------------------------------
command -v java >/dev/null 2>&1 || { echo "ERROR: Java JDK not found on PATH. Install JDK 21+ and retry."; exit 1; }
command -v mvn  >/dev/null 2>&1 || { echo "ERROR: Maven not found on PATH. Install Apache Maven 3.9+ and retry."; exit 1; }

# --- Step 1: stop any existing process on AI_PORT --------------------
echo "[1/5] Stopping any existing AI Builder process on port $AI_PORT..."
if command -v lsof >/dev/null 2>&1; then
    PIDS=$(lsof -ti:"$AI_PORT" 2>/dev/null || true)
    if [ -n "$PIDS" ]; then
        echo "   Killing PID(s): $PIDS"
        kill -9 $PIDS 2>/dev/null || true
    fi
fi

# --- Step 2: ensure OPENAI_API_KEY is set ----------------------------
echo "[2/5] Checking OPENAI_API_KEY..."
if [ -z "${OPENAI_API_KEY:-}" ] && [ -f "$ROOT_DIR/ai-builder/.env" ]; then
    # shellcheck disable=SC2046
    export $(grep -E '^OPENAI_API_KEY=' "$ROOT_DIR/ai-builder/.env" | xargs)
fi
if [ -z "${OPENAI_API_KEY:-}" ]; then
    echo "   ERROR: OPENAI_API_KEY is not set."
    echo "   Export it with:  export OPENAI_API_KEY=sk-your-key-here"
    echo "   Or add it to ai-builder/.env"
    exit 1
fi
echo "   OPENAI_API_KEY found (starts with ${OPENAI_API_KEY:0:7}...)"

# --- Step 3: ensure Docker dependencies (PostgreSQL + Qdrant) --------
echo "[3/5] Ensuring Docker dependencies are running..."
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

# Qdrant -- try to start; if container missing, create it
if ! docker start qdrant >/dev/null 2>&1; then
    echo "   Creating Qdrant container..."
    docker run -d \
        --name qdrant \
        -p "$QDRANT_HTTP_PORT:6333" \
        -p "$QDRANT_GRPC_PORT:6334" \
        -v "$ROOT_DIR/qdrant_storage:/qdrant/storage" \
        qdrant/qdrant >/dev/null
    sleep 3
fi
echo "   Qdrant: running on port $QDRANT_HTTP_PORT"

# --- Step 4: build the module ----------------------------------------
echo "[4/5] Building ai-builder module..."
cd "$ROOT_DIR"
if ! mvn -q -pl ai-builder -am -DskipTests install; then
    if [ ! -f "ai-builder/target/ai-builder-1.0-SNAPSHOT-fat.jar" ]; then
        echo "   ERROR: Build failed and no existing jar found."
        exit 1
    fi
    echo "   WARNING: Build failed, using existing jar."
fi

# --- Step 5: launch --------------------------------------------------
mkdir -p "$ROOT_DIR/logs"
LOG_FILE="$ROOT_DIR/logs/ai-builder.log"
echo "[5/5] Launching AI Builder on port $AI_PORT..."
echo "   Health: http://localhost:$AI_PORT/health"
echo "   Chat:   http://localhost:$AI_PORT/api/ai/chat"
echo "   Log:    $LOG_FILE"
echo "   Press Ctrl+C to stop."
echo "=========================================="

export DATABASE_URL="jdbc:postgresql://localhost:$PG_PORT/appbana"
export DATABASE_USER="appbana"
export DATABASE_PASSWORD="appbana_dev_2026"

cd ai-builder
java -jar target/ai-builder-1.0-SNAPSHOT-fat.jar 2>&1 | tee "$LOG_FILE"
