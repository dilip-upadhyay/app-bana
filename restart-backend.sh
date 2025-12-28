#!/bin/bash

# AppBana Backend Restart Script
# This script stops the running backend, rebuilds, and restarts it

set -e  # Exit on error

echo "🔄 Restarting AppBana Backend..."
echo ""

# Step 1: Stop running backend
echo "1️⃣ Stopping running backend service..."
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

# Step 2: Build JAR
echo "2️⃣ Building backend JAR..."
cd app-bana-service
./mvnw clean package -DskipTests
cd ..

if [ ! -f "app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar" ]; then
    echo "❌ Build failed - JAR not found!"
    exit 1
fi

echo "   ✅ Build successful"
echo ""

# Step 3: Start backend
echo "3️⃣ Starting backend service..."
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
