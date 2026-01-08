#!/bin/bash

# AI Builder Development Script (with hot reload)

echo "🔧 Starting AI Builder in development mode..."

# Check if OPENAI_API_KEY is set
if [ -z "$OPENAI_API_KEY" ]; then
    echo "❌ ERROR: OPENAI_API_KEY environment variable is not set"
    exit 1
fi

# Load environment variables
if [ -f ai-builder/.env ]; then
    export $(cat ai-builder/.env | grep -v '^#' | xargs)
fi

# Start Qdrant if not running
QDRANT_HOST=${QDRANT_HOST:-localhost}
QDRANT_PORT=${QDRANT_PORT:-6333}

if ! curl -s "http://${QDRANT_HOST}:${QDRANT_PORT}/health" > /dev/null 2>&1; then
    echo "⚠️  Starting Qdrant..."
    if docker ps -a --format '{{.Names}}' | grep -q '^qdrant$'; then
        docker start qdrant
    else
        docker run -d --name qdrant -p ${QDRANT_PORT}:6333 -v $(pwd)/qdrant_storage:/qdrant/storage qdrant/qdrant
    fi
    sleep 3
fi

# Run with Maven exec plugin (supports hot reload)
cd ai-builder
echo "🚀 Starting in development mode (Ctrl+C to stop)..."
echo "📝 Code changes will require restart"
echo ""

mvn compile exec:java -Dexec.mainClass="com.appbana.ai.AiBuilderMain"
