# AI Builder Service

AI-powered application builder microservice using GPT-4, RAG (Retrieval Augmented Generation), and an agent-based architecture with tool execution capabilities.

## Architecture

The AI Builder runs as a **separate microservice** on port **8081** using a custom Router pattern (no servlets/Tomcat):

```
Frontend (5173) → AI Builder (8081) → AppBana Service (8080)
                       ↓
                  [AI Agent]
                  - CreateEntity
                  - ListEntities
                  - GeneratePage
                  - SearchKnowledge
                       ↓
                  Qdrant (6334)
```

## Features

- **AI Agent**: Think → Act → Observe loop for autonomous task execution
- **4 Essential Tools**: Entity creation, listing, page generation, knowledge search
- **RAG Integration**: 39 AppBana schemas indexed in Qdrant for context-aware responses
- **Metadata Validation**: Auto-fix and validate AI-generated metadata
- **GPT-4 Integration**: Advanced prompt engineering with schema context

## Prerequisites

- Java 17+
- Maven 3.6+
- Docker (for Qdrant)
- OpenAI API Key
- PostgreSQL (running on localhost:5432)

## Quick Start

### 1. Start Qdrant Vector Database

```bash
docker run -d -p 6333:6333 -p 6334:6334 --name qdrant qdrant/qdrant
```

**Note**: Qdrant uses two ports:
- **6333**: HTTP/REST API
- **6334**: gRPC API (used by AI Builder)

### 2. Set Environment Variables

```bash
export OPENAI_API_KEY="your-openai-api-key"
export QDRANT_HOST="localhost"
export QDRANT_PORT="6334"
```

### 3. Start AI Builder Service

```bash
cd ai-builder
mvn clean install -DskipTests
mvn exec:java -Dexec.mainClass="com.appbana.ai.AiBuilderMain"
```

**Expected Output:**
```
✅ Qdrant health check passed
✅ Qdrant collections initialized
✅ AI Builder Server started on port 8081
📍 Health check: http://localhost:8081/health
📍 Chat endpoint: http://localhost:8081/api/ai/chat
📍 Agent endpoint: http://localhost:8081/api/ai/chat/agent
```

## API Endpoints

### Health Check

```bash
curl http://localhost:8081/health
```

**Response:**
```json
{
  "status": "UP",
  "service": "ai-builder",
  "qdrant": "UP"
}
```

### Chat Endpoint

Regular chat without tool execution:

```bash
curl -X POST http://localhost:8081/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Hello, can you help me?",
    "sessionId": "test-123",
    "userId": "demo"
  }'
```

### Agent Endpoint (with Tools) ⭐

Agent-based chat with autonomous tool execution:

```bash
curl -X POST http://localhost:8081/api/ai/chat/agent \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Create a customer entity with name and email fields",
    "sessionId": "test-456",
    "userId": "demo",
    "tenantId": "default",
    "appId": "default"
  }'
```

## Testing with Authentication

The tools require authentication to call the backend AppBana service on port 8080.

### 1. Start AppBana Service

```bash
cd app-bana-service
mvn exec:java -Dexec.mainClass="com.appbana.Main"
```

### 2. Register/Login to Get Token

```bash
# Register new user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "demo@demo.com", "password": "demo", "name": "Demo"}'
```

**Response:**
```json
{
  "token": "y5JYS_PJsMo3T5egtl9YAh1Cbx96vvaFKBwMNE2D6Ns",
  "user": {
    "id": 5,
    "email": "demo@demo.com",
    "tenantId": "t-2c440300"
  }
}
```

### 3. Test Agent with Token

```bash
curl -X POST http://localhost:8081/api/ai/chat/agent \
  -H "Content-Type: application/json" \
  -H "X-Session-Token: YOUR_TOKEN_HERE" \
  -d '{
    "message": "Create a customer entity with name and email fields",
    "sessionId": "test-123",
    "userId": "demo",
    "tenantId": "t-2c440300",
    "appId": "default"
  }'
```

**Note**: Tools currently need session token support to be added. For now, the agent will process the request but tools will get 401 errors when calling backend APIs.

## Configuration

Configuration is loaded from environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `AI_PORT` | `8081` | AI Builder service port |
| `OPENAI_API_KEY` | - | **Required** - OpenAI API key |
| `OPENAI_MODEL` | `gpt-4` | OpenAI model to use |
| `OPENAI_EMBEDDING_MODEL` | `text-embedding-3-small` | Embedding model |
| `QDRANT_HOST` | `localhost` | Qdrant host |
| `QDRANT_PORT` | `6334` | Qdrant gRPC port |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/appbana` | PostgreSQL URL |
| `AI_ENABLE_LEARNING` | `true` | Enable learning from interactions |

## Project Structure

```
ai-builder/
├── src/main/java/com/appbana/ai/
│   ├── agent/               # AI Agent core logic
│   │   ├── AiAgent.java     # Think-Act-Observe loop
│   │   ├── AgentContext.java
│   │   ├── AgentResponse.java
│   │   └── tool/            # Tool implementations
│   │       ├── CreateEntityTool.java
│   │       ├── ListEntitiesTool.java
│   │       ├── GeneratePageTool.java
│   │       └── SearchKnowledgeTool.java
│   ├── api/                 # HTTP endpoints
│   │   ├── Router.java      # Custom HTTP router
│   │   ├── AiChatController.java
│   │   └── dto/
│   ├── config/              # Configuration
│   │   └── AiConfig.java
│   ├── knowledge/           # RAG & Knowledge Base
│   │   ├── AppBanaSchemaLoader.java
│   │   ├── KnowledgeBaseService.java
│   │   ├── MetadataValidator.java
│   │   └── AppBanaPromptEnhancer.java
│   ├── llm/                 # LLM integration
│   │   ├── OpenAiLlmService.java
│   │   ├── AdvancedPromptEngine.java
│   │   └── IntentClassifier.java
│   ├── rag/                 # Vector store & embeddings
│   │   ├── QdrantService.java
│   │   ├── VectorStoreService.java
│   │   └── EmbeddingService.java
│   └── server/              # HTTP server
│       └── AiServer.java
└── src/test/java/           # Tests (20 passing)
```

## Service Initialization

On startup, the AI Builder initializes:

1. **OpenAiLlmService** - GPT-4 integration
2. **EmbeddingService** - Text embeddings for RAG
3. **VectorStoreService** - Qdrant vector operations
4. **AppBanaSchemaLoader** - Loads 39 AppBana schemas
5. **KnowledgeBaseService** - RAG semantic search
6. **AppBanaPromptEnhancer** - Injects schema context into prompts
7. **MetadataValidator** - Validates & auto-fixes metadata
8. **ToolRegistry** - Registers 4 essential tools
9. **AiAgent** - Orchestrates Think → Act → Observe loop
10. **AiChatController** - HTTP endpoint handlers
11. **HttpServer** - Starts on port 8081

## Agent Flow

When you send a request to `/api/ai/chat/agent`:

1. **THINK**: Agent analyzes the request using GPT-4
2. **ACT**: Agent decides which tools to execute
3. **OBSERVE**: Agent collects tool execution results
4. **REPEAT**: If needed, agent continues the loop
5. **RESPOND**: Agent formulates final answer with execution metadata

## Testing

Run unit tests:

```bash
mvn test
```

**Test Results**: 20/20 passing
- `AiAgentTest`: 8 tests
- `EssentialToolsTest`: 12 tests

## Troubleshooting

### Port 8081 already in use

```bash
lsof -i :8081
kill -9 <PID>
```

### Qdrant connection failed

Make sure Qdrant is running on port 6334 (gRPC):

```bash
docker ps | grep qdrant
curl http://localhost:6333/  # HTTP health check
```

### OpenAI API errors

Check your API key:

```bash
echo $OPENAI_API_KEY
```

### Database connection issues

Ensure PostgreSQL is running:

```bash
psql -h localhost -U appbana -d appbana
```

## Development

### Building

```bash
mvn clean install
```

### Running tests

```bash
mvn test
```

### Running with debug

```bash
mvn exec:java -Dexec.mainClass="com.appbana.ai.AiBuilderMain" -Dexec.args="-Ddebug=true"
```

## Known Issues

1. **Tools need authentication**: Tools currently don't pass session tokens to backend APIs. This will be fixed in a future update.
2. **ConversationMemory disabled**: Requires DataSource, currently set to null.

## Next Steps

- [ ] Add session token support to tools
- [ ] Enable ConversationMemory with DataSource
- [ ] Add rate limiting for API endpoints
- [ ] Add caching for frequently used queries
- [ ] Add authentication middleware
- [ ] Deploy to production (Docker/Kubernetes)

## License

Proprietary - AppBana

## Support

For issues or questions, contact the AppBana development team.
