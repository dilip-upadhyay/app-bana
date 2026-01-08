#!/bin/bash

# AI Builder Service Stop Script

echo "🛑 Stopping AI Builder Service..."

# Stop Qdrant container
if docker ps --format '{{.Names}}' | grep -q '^qdrant$'; then
    echo "📦 Stopping Qdrant container..."
    docker stop qdrant
    echo "✅ Qdrant stopped"
else
    echo "ℹ️  Qdrant container is not running"
fi

# Find and kill AI Builder process
AI_PID=$(ps aux | grep 'ai-builder.*fat.jar' | grep -v grep | awk '{print $2}')

if [ -n "$AI_PID" ]; then
    echo "🔪 Stopping AI Builder process (PID: $AI_PID)..."
    kill $AI_PID
    sleep 2
    
    # Force kill if still running
    if ps -p $AI_PID > /dev/null 2>&1; then
        echo "⚠️  Process still running, force killing..."
        kill -9 $AI_PID
    fi
    
    echo "✅ AI Builder stopped"
else
    echo "ℹ️  AI Builder is not running"
fi

echo "✅ All services stopped"
