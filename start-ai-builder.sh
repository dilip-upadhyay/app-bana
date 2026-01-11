#!/bin/bash

# AI Builder Service Startup Script

echo "🚀 Starting AI Builder Service..."

# Load environment variables from .env if it exists
if [ -f ai-builder/.env ]; then
    echo "📝 Loading environment from ai-builder/.env"
    export $(cat ai-builder/.env | grep -v '^#' | xargs)
fi

# Check if OPENAI_API_KEY is set
if [ -z "$OPENAI_API_KEY" ]; then
    echo "❌ ERROR: OPENAI_API_KEY environment variable is not set"
    echo "Please set it with: export OPENAI_API_KEY=sk-your-key-here"
    exit 1
fi

# Define port
PORT=${AI_PORT:-8081}

# Check if port is in use and kill process
echo "🔍 Checking if port $PORT is in use..."
PID=$(lsof -ti:$PORT)
if [ ! -z "$PID" ]; then
    echo "⚠️  Found process $PID running on port $PORT. Stopping it..."
    kill -9 $PID
    echo "✅ Stopped existing process."
else
    echo "✅ Port $PORT is free."
fi

# Check if Qdrant is running
echo "🔍 Checking Qdrant status..."
QDRANT_HOST=${QDRANT_HOST:-localhost}
QDRANT_PORT=${QDRANT_PORT:-6333}

if curl -s "http://${QDRANT_HOST}:${QDRANT_PORT}/health" > /dev/null 2>&1; then
    echo "✅ Qdrant is already running on ${QDRANT_HOST}:${QDRANT_PORT}"
else
    echo "⚠️  Qdrant is not running. Starting Qdrant container..."
    
    # Check if Docker is installed
    if ! command -v docker &> /dev/null; then
        echo "❌ ERROR: Docker is not installed. Please install Docker first."
        exit 1
    fi
    
    # Check if Qdrant container already exists
    if docker ps -a --format '{{.Names}}' | grep -q '^qdrant$'; then
        echo "📦 Qdrant container exists. Starting it..."
        docker start qdrant
    else
        echo "📦 Creating and starting new Qdrant container..."
        docker run -d \
            --name qdrant \
            -p ${QDRANT_PORT}:6333 \
            -v $(pwd)/qdrant_storage:/qdrant/storage \
            qdrant/qdrant
    fi
    
    # Wait for Qdrant to be ready
    echo "⏳ Waiting for Qdrant to be ready..."
    for i in {1..30}; do
        if curl -s "http://${QDRANT_HOST}:${QDRANT_PORT}/health" > /dev/null 2>&1; then
            echo "✅ Qdrant is ready!"
            break
        fi
        if [ $i -eq 30 ]; then
            echo "❌ ERROR: Qdrant failed to start after 30 seconds"
            exit 1
        fi
        sleep 1
    done
fi

# Build the project
echo "🔨 Building AI Builder Service..."
cd ai-builder
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

echo "✅ Build successful!"

# Run the service
echo "🚀 Starting AI Builder server on port ${AI_PORT:-8081}..."
echo "📍 Health check: http://localhost:${AI_PORT:-8081}/health"
echo "📍 Chat API: http://localhost:${AI_PORT:-8081}/api/ai/chat"
echo ""
echo "Press Ctrl+C to stop the service"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

java -jar target/ai-builder-1.0-SNAPSHOT-fat.jar

