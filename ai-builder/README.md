# AI Builder Service

Intelligent AI agent for building applications through natural conversation.

## Features

- 🧠 **RAG (Retrieval Augmented Generation)**: Learns from every app created
- 🎯 **Pattern Mining**: Discovers successful app structures automatically
- 👤 **Personalization**: Adapts to each user's preferences and style
- 💬 **Multi-turn Dialogue**: Natural conversations with context
- 🔄 **Continuous Learning**: Gets smarter from user feedback
- 🎤 **Voice Interface**: Speak naturally to create apps

## Technology Stack

- **LLM**: OpenAI GPT-4
- **Vector Database**: Qdrant (self-hosted)
- **Embeddings**: OpenAI text-embedding-3-small
- **Database**: PostgreSQL
- **Framework**: Embedded Tomcat
- **Language**: Java 21+

## Quick Start

### Prerequisites

- Java 21 or higher
- Maven 3.8+
- PostgreSQL 16
- Docker (for Qdrant)
- OpenAI API key

### 1. Set Environment Variables

```bash
export OPENAI_API_KEY=sk-your-key-here
export DATABASE_URL=jdbc:postgresql://localhost:5432/appbana
export DATABASE_USER=appbana
export DATABASE_PASSWORD=your-password
```

### 2. Start the Service

The startup script will automatically:
- ✅ Check if Qdrant is running
- ✅ Start Qdrant Docker container if needed
- ✅ Build the project
- ✅ Start the AI Builder service

```bash
./start-ai-builder.sh
```

The service will start on port 8081 by default.

### 3. Test

```bash
# Health check
curl http://localhost:8081/health

# Chat endpoint
curl -X POST http://localhost:8081/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Create a CRM app", "userId": "test-user"}'
```

### 4. Stop the Service

```bash
./stop-ai-builder.sh
```

This will stop both the AI Builder service and the Qdrant container.

## Configuration

All configuration is done via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `AI_PORT` | 8081 | Server port |
| `OPENAI_API_KEY` | (required) | OpenAI API key |
| `OPENAI_MODEL` | gpt-4 | LLM model to use |
| `OPENAI_EMBEDDING_MODEL` | text-embedding-3-small | Embedding model |
| `QDRANT_HOST` | localhost | Qdrant host |
| `QDRANT_PORT` | 6333 | Qdrant port |
| `DATABASE_URL` | jdbc:postgresql://localhost:5432/appbana | Database URL |
| `DATABASE_USER` | appbana | Database user |
| `DATABASE_PASSWORD` | | Database password |
| `AI_ENABLE_LEARNING` | true | Enable learning features |
| `AI_ENABLE_VOICE` | true | Enable voice input |

## Project Structure

```
ai-builder/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/appbana/ai/
│   │   │       ├── AiBuilderMain.java          # Main entry point
│   │   │       ├── config/
│   │   │       │   └── AiConfig.java           # Configuration
│   │   │       ├── server/
│   │   │       │   └── AiServer.java           # HTTP server
│   │   │       ├── rag/                        # RAG components
│   │   │       │   ├── EmbeddingService.java
│   │   │       │   ├── VectorStoreService.java
│   │   │       │   └── ConversationMemory.java
│   │   │       ├── learning/                   # Learning components
│   │   │       │   ├── PatternMiner.java
│   │   │       │   ├── UserPreferenceEngine.java
│   │   │       │   └── FeedbackLoop.java
│   │   │       ├── dialogue/                   # Dialogue management
│   │   │       │   ├── DialogueManager.java
│   │   │       │   └── AmbiguityResolver.java
│   │   │       └── llm/                        # LLM integration
│   │   │           ├── OpenAiService.java
│   │   │           └── AdvancedPromptEngine.java
│   │   └── resources/
│   │       └── logback.xml                     # Logging config
│   └── test/
│       └── java/
│           └── com/appbana/ai/                 # Tests
└── pom.xml
```

## Development

### Running Tests

```bash
mvn test
```

### Running with Hot Reload

```bash
mvn compile exec:java -Dexec.mainClass="com.appbana.ai.AiBuilderMain"
```

### Building Fat JAR

```bash
mvn clean package
```

## API Documentation

See [docs/AI_AGENT_STORIES.md](../docs/AI_AGENT_STORIES.md) for detailed API specifications.

## Implementation Plan

See [docs/AI_AGENT_IMPLEMENTATION_PLAN.md](../docs/AI_AGENT_IMPLEMENTATION_PLAN.md) for the complete implementation roadmap.

## Cost Estimates

- **OpenAI GPT-4**: $150-200/month (1000 users)
- **OpenAI Embeddings**: $20-30/month
- **Qdrant**: $0 (self-hosted)
- **Total**: ~$170-230/month

## License

Proprietary - AppBana
