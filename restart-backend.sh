#!/bin/bash

# AppBana Backend Restart Script
# This script manages PostgreSQL Docker, stops the running backend, rebuilds, and restarts it

set -e  # Exit on error

echo "🔄 Restarting AppBana Backend..."
echo ""

# Step 1: Check/Start PostgreSQL Docker
echo "1️⃣ Checking PostgreSQL Docker container..."

POSTGRES_CONTAINER_NAME="appbana-postgres"
POSTGRES_VERSION="16-alpine"
POSTGRES_PORT="5432"
POSTGRES_DB="appbana"
POSTGRES_USER="appbana"
POSTGRES_PASSWORD="appbana_dev_2026"

# Check if container exists
if docker ps -a --format '{{.Names}}' | grep -q "^${POSTGRES_CONTAINER_NAME}$"; then
    # Container exists, check if it's running
    if docker ps --format '{{.Names}}' | grep -q "^${POSTGRES_CONTAINER_NAME}$"; then
        echo "   ✅ PostgreSQL container already running"
    else
        echo "   🔄 Starting existing PostgreSQL container..."
        docker start ${POSTGRES_CONTAINER_NAME}
        sleep 3
        echo "   ✅ PostgreSQL container started"
    fi
else
    # Container doesn't exist, create and start it
    echo "   📦 Creating new PostgreSQL container..."
    docker run -d \
        --name ${POSTGRES_CONTAINER_NAME} \
        -e POSTGRES_DB=${POSTGRES_DB} \
        -e POSTGRES_USER=${POSTGRES_USER} \
        -e POSTGRES_PASSWORD=${POSTGRES_PASSWORD} \
        -p ${POSTGRES_PORT}:5432 \
        -v appbana-postgres-data:/var/lib/postgresql/data \
        postgres:${POSTGRES_VERSION}
    
    echo "   ⏳ Waiting for PostgreSQL to be ready..."
    sleep 5
    
    # Wait for PostgreSQL to accept connections
    for i in {1..30}; do
        if docker exec ${POSTGRES_CONTAINER_NAME} pg_isready -U ${POSTGRES_USER} > /dev/null 2>&1; then
            echo "   ✅ PostgreSQL is ready!"
            break
        fi
        if [ $i -eq 30 ]; then
            echo "   ❌ PostgreSQL failed to start within 30 seconds"
            exit 1
        fi
        sleep 1
    done
fi

# Display connection info
echo "   📊 PostgreSQL Connection Info:"
echo "      Host: localhost:${POSTGRES_PORT}"
echo "      Database: ${POSTGRES_DB}"
echo "      User: ${POSTGRES_USER}"
echo ""

# Step 2: Stop running backend
echo "2️⃣ Stopping running backend service..."
if [ -f "backend.pid" ]; then
    PID=$(cat backend.pid)
    if ps -p $PID > /dev/null 2>&1; then
        echo "   Killing process $PID..."
        kill $PID || kill -9 $PID
        sleep 2
        echo "   ✅ Backend stopped"
    else
        echo "   ⚠️  PID file exists but process not running"
    fi
    rm -f backend.pid
else
    # Try to find and kill any running Java process with app-bana
    echo "   No PID file found, searching for running app-bana process..."
    PIDS=$(pgrep -f "app-bana.*jar" || true)
    if [ -n "$PIDS" ]; then
        echo "   Found process(es): $PIDS"
        echo "$PIDS" | xargs kill || echo "$PIDS" | xargs kill -9
        sleep 2
        echo "   ✅ Backend stopped"
    else
        echo "   ℹ️  No running backend found"
    fi
fi

echo ""

# Step 3: Build JAR
echo "3️⃣ Building backend JAR..."
cd app-bana-service
./mvnw clean package -Dmaven.test.skip=true
cd ..

if [ ! -f "app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar" ]; then
    echo "❌ Build failed - JAR not found!"
    exit 1
fi

echo "   ✅ Build successful"
echo ""

# Step 4: Start backend
echo "4️⃣ Starting backend service..."
cd app-bana-service
nohup java -jar target/app-bana-1.0-SNAPSHOT-fat.jar > ../backend.log 2>&1 &
echo $! > ../backend.pid
cd ..

sleep 3

# Verify backend started
if ps -p $(cat backend.pid) > /dev/null 2>&1; then
    echo "   ✅ Backend started successfully!"
    echo "   📋 PID: $(cat backend.pid)"
    echo "   📝 Logs: backend.log"
    echo "   🌐 URL: http://localhost:8080"
    echo ""
    echo "✨ Backend restart complete!"
    echo ""
    echo "To view logs: tail -f backend.log"
    echo "To stop: kill \$(cat backend.pid)"
else
    echo "   ❌ Backend failed to start!"
    echo "   Check backend.log for errors"
    exit 1
fi
