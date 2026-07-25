# AI Agent Architecture for AppBana

**Created**: January 9, 2026  
**Status**: Proposal for Review  
**Branch**: ai-builder

---

## Overview

The modern AI Agent pattern (used by LangChain, AutoGPT, Claude, etc.) follows a **Think → Act → Observe** loop:

```
┌─────────────────────────────────────────────────────────────┐
│                      AI AGENT LOOP                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   User Request: "Create a customer management app"         │
│                         │                                   │
│                         ▼                                   │
│   ┌─────────────────────────────────────────┐              │
│   │  1. THINK (LLM)                         │              │
│   │  "I need to: create entity, then page"  │              │
│   └─────────────────────────────────────────┘              │
│                         │                                   │
│                         ▼                                   │
│   ┌─────────────────────────────────────────┐              │
│   │  2. ACT (Tool Call)                     │              │
│   │  create_entity(name="customer", ...)    │              │
│   └─────────────────────────────────────────┘              │
│                         │                                   │
│                         ▼                                   │
│   ┌─────────────────────────────────────────┐              │
│   │  3. OBSERVE (Tool Result)               │              │
│   │  "Entity 'customer' created successfully"│              │
│   └─────────────────────────────────────────┘              │
│                         │                                   │
│                         ▼                                   │
│              (Loop back to THINK)                           │
│                         │                                   │
│                         ▼                                   │
│   ┌─────────────────────────────────────────┐              │
│   │  4. FINAL ANSWER                        │              │
│   │  "Your app is ready with..."            │              │
│   └─────────────────────────────────────────┘              │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Current Implementation vs Agent + Tools Pattern

### Your Current Architecture: **Intent → Response**

```
┌───────────────────────────────────────────────────────────┐
│              YOUR CURRENT FLOW                            │
├───────────────────────────────────────────────────────────┤
│                                                           │
│   User: "Create a customer app"                          │
│                   │                                       │
│                   ▼                                       │
│   ┌─────────────────────────────────────┐                │
│   │  IntentClassifier                    │                │
│   │  → classify as "create_app"          │                │
│   └─────────────────────────────────────┘                │
│                   │                                       │
│                   ▼                                       │
│   ┌─────────────────────────────────────┐                │
│   │  AdvancedPromptEngine               │                │
│   │  → build prompt with RAG context     │                │
│   └─────────────────────────────────────┘                │
│                   │                                       │
│                   ▼                                       │
│   ┌─────────────────────────────────────┐                │
│   │  OpenAiLlmService                   │                │
│   │  → get text response                 │                │
│   └─────────────────────────────────────┘                │
│                   │                                       │
│                   ▼                                       │
│   Response: "I'll create a customer app for you..."     │
│                                                           │
│   ⚠️ BUT NOTHING ACTUALLY HAPPENS!                       │
│   ❌ No entity created in database                       │
│   ❌ No page generated                                   │
│   ❌ No app published                                    │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

### Agent + Tools Architecture: **Intent → Think → Act → Observe → Respond**

```
┌───────────────────────────────────────────────────────────┐
│              AGENT + TOOLS FLOW                           │
├───────────────────────────────────────────────────────────┤
│                                                           │
│   User: "Create a customer app"                          │
│                   │                                       │
│                   ▼                                       │
│   ┌─────────────────────────────────────┐                │
│   │  1. THINK (LLM decides what to do)  │                │
│   │  "I need to call create_entity and   │                │
│   │   generate_page tools"               │                │
│   └─────────────────────────────────────┘                │
│                   │                                       │
│                   ▼                                       │
│   ┌─────────────────────────────────────┐                │
│   │  2. ACT (Execute tools)             │                │
│   │  → CreateEntityTool.execute()       │                │
│   │  → POST /schema { customer... }     │ ✅ REAL ACTION │
│   └─────────────────────────────────────┘                │
│                   │                                       │
│                   ▼                                       │
│   ┌─────────────────────────────────────┐                │
│   │  3. OBSERVE (Tool result)           │                │
│   │  "Entity 'customer' created"         │                │
│   └─────────────────────────────────────┘                │
│                   │                                       │
│                   ▼                                       │
│   ┌─────────────────────────────────────┐                │
│   │  4. THINK AGAIN                     │                │
│   │  "Now I need to create pages"        │                │
│   └─────────────────────────────────────┘                │
│                   │                                       │
│                   ▼                                       │
│   ┌─────────────────────────────────────┐                │
│   │  5. ACT (More tools)                │                │
│   │  → GeneratePageTool.execute()       │                │
│   │  → POST /api/apps/{id}/pages        │ ✅ REAL ACTION │
│   └─────────────────────────────────────┘                │
│                   │                                       │
│                   ▼                                       │
│   Response: "I've created your customer app with:       │
│   ✅ Customer entity with name, email, phone fields     │
│   ✅ Customer list page at /customers                   │
│   ✅ Add Customer form at /customers/new"               │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

---

## Summary: Key Differences

| Aspect | Current Implementation | Agent + Tools Pattern |
|--------|---------------------------|----------------------|
| **What happens** | LLM generates **text** | LLM **executes real actions** |
| **Entity creation** | ❌ Just describes it | ✅ Actually calls `POST /schema` |
| **Page generation** | ❌ Just explains it | ✅ Actually creates page metadata |
| **Loop capability** | ❌ Single request-response | ✅ Multi-step: Think → Act → Observe → Repeat |
| **Error handling** | ❌ N/A (nothing executed) | ✅ Tool returns error, LLM retries or adapts |
| **Context awareness** | ✅ Has conversation memory | ✅ Has memory + live data from tools |
| **Backend integration** | ❌ Disconnected | ✅ Tools call AppBana REST APIs |

---

## The Gap

**Current flow**:
```
User → "Create customer app" → LLM says "I will create..."  → END (nothing created)
```

**Agent flow**:
```
User → "Create customer app" → LLM returns {tool_calls: [{create_entity: {...}}]} 
     → Your code executes POST /schema → Returns result to LLM 
     → LLM returns {tool_calls: [{generate_page: {...}}]}
     → Your code executes POST /pages → Returns result to LLM
     → LLM returns {final_answer: "Your app is ready with customer entity and pages!"}
```

The agent pattern **closes the loop** between the AI's intent and **actual execution** in your backend.

---

## Core Components

### 1. **AiAgent** (Orchestrator)
The main agent class that runs the loop.

```java
public class AiAgent {
    private final LlmService llmService;
    private final Map<String, Tool> tools;
    
    public AgentResponse process(String userMessage, AgentContext context) {
        List<AgentStep> steps = new ArrayList<>();
        
        while (iteration < maxIterations) {
            // 1. THINK - Ask LLM what to do
            AgentThought thought = think(userMessage, steps, context);
            
            // 2. Check if done
            if (thought.isFinalAnswer()) {
                return new AgentResponse(thought.getFinalAnswer(), steps);
            }
            
            // 3. ACT - Execute tools
            List<ToolResult> results = executeTools(thought.getToolCalls(), context);
            
            // 4. OBSERVE - Add results to history for next iteration
            steps.addAll(results);
        }
    }
}
```

### 2. **AgentContext** (Session State)
Carries tenant/app/user info and variables between tools.

```java
public record AgentContext(
    String tenantId,
    String appId,
    String userId,
    String sessionId,
    String backendUrl,
    Map<String, Object> variables,  // Shared between tools
    Map<String, Object> memory      // Persists across conversations
) {}
```

### 3. **Tool** (Interface)
Each capability the agent can use.

```java
public interface Tool {
    String getName();           // "create_entity"
    String getDescription();    // For LLM to understand when to use
    String getParameterSchema(); // JSON Schema for arguments
    ToolResult execute(Map<String, Object> args, AgentContext ctx);
}
```

### 4. **ToolResult** (Outcome)
```java
public record ToolResult(
    boolean success,
    Object data,      // JSON-serializable result
    String error      // Error message if failed
) {}
```

---

## Recommended Tools for AppBana

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `create_entity` | Create a database entity/table | `name`, `fields[]`, `description` |
| `update_entity` | Modify entity schema | `name`, `addFields[]`, `removeFields[]` |
| `list_entities` | Get all entities in current app | none |
| `get_entity_schema` | Get schema for specific entity | `name` |
| `generate_page` | Create a UI page | `name`, `path`, `type`, `entityName` |
| `update_page` | Modify page structure | `pageId`, `changes` |
| `list_pages` | Get all pages in current app | none |
| `create_app` | Create a new application | `name`, `description` |
| `publish_app` | Deploy app to runtime | `appId` |
| `search_knowledge` | RAG search in builder-database | `query` |
| `execute_query` | Run SQL query (read-only) | `sql` |

---

## LLM System Prompt Structure

```
You are AppBana AI Builder - an intelligent agent that helps users create applications.

## Available Tools

### create_entity
Create a new entity (database table) in the current app.
Parameters: { "name": string, "fields": [...], "description": string }

### generate_page
Generate a UI page for the application.
Parameters: { "name": string, "path": string, "type": "list|form|dashboard" }

[... more tools ...]

## Response Format

You must respond with JSON in one of two formats:

**Format 1: Call Tools**
{
  "thinking": "My reasoning about what to do next...",
  "tool_calls": [
    { "name": "tool_name", "arguments": { ... } }
  ]
}

**Format 2: Final Answer**
{
  "thinking": "My reasoning...",
  "final_answer": "The complete response to the user..."
}

## Guidelines
1. Think step-by-step before acting
2. Use tools to gather information and take actions
3. You can call multiple tools at once if they're independent
4. After enough information, provide final_answer
```

---

## Example Conversation Flow

**User**: "Create a customer management app with name, email, and phone fields"

**Agent Iteration 1** (THINK):
```json
{
  "thinking": "User wants a customer app. I need to: 1) Create customer entity, 2) Generate list page, 3) Generate form page",
  "tool_calls": [
    {
      "name": "create_entity",
      "arguments": {
        "name": "customer",
        "fields": [
          {"name": "name", "type": "string", "required": true},
          {"name": "email", "type": "string", "required": true},
          {"name": "phone", "type": "string"}
        ]
      }
    }
  ]
}
```

**Tool Result**: `{"success": true, "entityName": "customer", "fieldCount": 4}`

**Agent Iteration 2** (THINK):
```json
{
  "thinking": "Entity created. Now I'll create the list and form pages.",
  "tool_calls": [
    {
      "name": "generate_page",
      "arguments": {"name": "Customers", "path": "/customers", "type": "list", "entityName": "customer"}
    },
    {
      "name": "generate_page", 
      "arguments": {"name": "Add Customer", "path": "/customers/new", "type": "form", "entityName": "customer"}
    }
  ]
}
```

**Agent Iteration 3** (FINAL):
```json
{
  "thinking": "All components created successfully.",
  "final_answer": "✅ I've created your customer management app with:\n\n1. **Customer entity** with fields: id, name, email, phone\n2. **Customer List page** at /customers\n3. **Add Customer form** at /customers/new\n\nClick Publish to deploy your app!"
}
```

---

## File Structure (Recommended)

```
ai-builder/src/main/java/com/appbana/ai/
├── agent/
│   ├── AiAgent.java              # Main orchestrator
│   ├── AgentContext.java         # Session context
│   ├── AgentConfig.java          # Configuration
│   └── tool/
│       ├── Tool.java             # Interface
│       ├── ToolResult.java       # Result wrapper
│       ├── ToolRegistry.java     # Tool discovery
│       ├── CreateEntityTool.java
│       ├── UpdateEntityTool.java
│       ├── ListEntitiesTool.java
│       ├── GeneratePageTool.java
│       ├── UpdatePageTool.java
│       ├── ListPagesTool.java
│       ├── CreateAppTool.java
│       ├── PublishAppTool.java
│       └── SearchKnowledgeTool.java
├── llm/
│   ├── OpenAiLlmService.java     # (existing)
│   └── LlmPrompts.java           # System prompts
├── dialogue/
│   └── DialogueManager.java      # (existing)
└── api/
    └── AiChatController.java     # (existing)
```

---

## Key Design Decisions

| Decision | Recommendation | Rationale |
|----------|----------------|-----------|
| **Max Iterations** | 10 | Prevents infinite loops |
| **Parallel Tools** | Yes (Java 21 Virtual Threads) | Faster when independent |
| **Tool Timeout** | 30 seconds | Prevents hanging |
| **Error Handling** | Retry once, then return error | Resilient but not infinite |
| **Context Passing** | Via `AgentContext` object | Clean dependency injection |
| **Tool Discovery** | Registry pattern | Easy to add new tools |

---

## Integration with Existing Code

Your existing classes fit nicely:

| Existing Class | Role in Agent Pattern |
|----------------|----------------------|
| `OpenAiLlmService` | The LLM backend for THINK step |
| `IntentClassifier` | Can be a pre-processing step or a tool |
| `ChainOfThoughtReasoning` | The "thinking" part is built into prompt |
| `DialogueManager` | Manages conversation state between requests |
| `QdrantService` | Used by `SearchKnowledgeTool` for RAG |

---

## What's Missing in Current Code

1. **Tool Execution Layer** — No code that actually calls your backend APIs (like `POST /schema`, `POST /api/apps/{id}/pages`)

2. **Agent Loop** — Your code does:
   ```
   classify → build prompt → call LLM → return response
   ```
   But needs:
   ```
   classify → think → call tools → observe → think again → ... → final answer
   ```

3. **Tool Registry** — A collection of tools the LLM can choose from

4. **Structured Output Parsing** — Telling LLM to output JSON with `tool_calls` array, then parsing and executing those calls

---

## What You Already Have (Reusable)

| Component | Current Role | Role in Agent Pattern |
|-----------|--------------|----------------------|
| `OpenAiLlmService` | Call LLM for response | Same - but now asks for tool calls |
| `IntentClassifier` | Classify intent | Can become a pre-filter or just let agent handle |
| `AdvancedPromptEngine` | Build prompts with RAG | Becomes part of system prompt for agent |
| `ConversationMemory` | Store conversations | Becomes agent memory |
| `DialogueManager` | State machine for conversation | Agent loop replaces this |
| `QdrantService` (RAG) | Search knowledge base | Becomes `search_knowledge` tool |

---

## Next Steps

1. **Review this architecture** and decide on approach
2. **Implement Tool interface** and a few core tools
3. **Implement AiAgent** with the think-act-observe loop
4. **Update AiChatController** to use the agent
5. **Test with simple use cases** (create entity, generate page)
6. **Expand tool library** as needed

---

**Document Status**: Proposal for Review  
**Author**: GitHub Copilot  
**Next Review**: TBD



# ✅ AI Builder - Separate Microservice Setup Complete!

## 🎯 Architecture

The AI Builder now runs as a **separate microservice** on port **8081**, while the main AppBana service runs on port **8080**.

```
┌─────────────────────┐      ┌──────────────────────┐
│   Frontend (UI)     │      │   AI Builder         │
│   Port: 5173        │─────▶│   Port: 8081         │
└─────────────────────┘      │   ✅ No Servlets     │
                             │   ✅ Router Pattern   │
                             │   ✅ AiChatController │
                             └──────────────────────┘
                                      │
                                      │ (calls backend)
                                      ▼
                             ┌──────────────────────┐
                             │  AppBana Service     │
                             │  Port: 8080          │
                             │  (entity/page APIs)  │
                             └──────────────────────┘
```

---

## 🚀 How to Start & Test

### 1. Start Qdrant (Vector Database)
```bash
docker run -p 6333:6333 -p 6334:6334 qdrant/qdrant
```

### 2. Start AI Builder Service
```bash
cd ai-builder
mvn clean install -DskipTests
mvn exec:java -Dexec.mainClass="com.appbana.ai.AiBuilderMain"
```

**Expected Output:**
```
✅ AI Builder Server started on port 8081
📍 Health check: http://localhost:8081/health
📍 Chat endpoint: http://localhost:8081/api/ai/chat  
📍 Agent endpoint: http://localhost:8081/api/ai/chat/agent
```

### 3. Test Health Check
```bash
curl http://localhost:8081/health
```

**Expected Response:**
```json
{
  "status": "UP",
  "service": "ai-builder",
  "qdrant": "UP"
}
```

### 4. Test Chat Endpoint
```bash
curl -X POST http://localhost:8081/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Create a customer management app",
    "sessionId": "test-123",
    "userId": "demo-user"
  }'
```

### 5. Test Agent Endpoint
```bash
curl -X POST http://localhost:8081/api/ai/chat/agent \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Create a customer entity with name and email fields",
    "sessionId": "test-123",
    "userId": "demo-user",
    "tenantId": "default",
    "appId": "default"
  }'
```

### 6. Start Frontend UI
```bash
cd app-bana-ui
npm run dev
```

Open http://localhost:5173 and use the AI Chat Builder component!

---

## 📁 What Was Changed

### ✅ Removed:
- ❌ Tomcat/Servlet dependencies
- ❌ `ChatServlet` class

### ✅ Added/Updated:
- ✅ `AiServer.java` - Uses Router pattern with HttpServer
- ✅ Proper service initialization (LLM, Knowledge Base, Tools, Agent)
- ✅ `/api/ai/chat` - Regular chat endpoint
- ✅ `/api/ai/chat/agent` - Agent-based endpoint
- ✅ `ai-chat-service.ts` - Points to `http://localhost:8081`

---

## 🔧 Service Initialization

The `AiServer` properly initializes:

1. **OpenAiLlmService** - GPT-4 integration
2. **EmbeddingService** - Text embeddings for RAG
3. **VectorStoreService** - Qdrant vector operations
4. **AppBanaSchemaLoader** - Loads 39 AppBana schemas
5. **KnowledgeBaseService** - RAG semantic search
6. **AppBanaPromptEnhancer** - Injects schema context
7. **MetadataValidator** - Validates & auto-fixes metadata
8. **ToolRegistry** - 4 essential tools registered:
   - `CreateEntityTool`
   - `ListEntitiesTool`
   - `GeneratePageTool`
   - `SearchKnowledgeTool`
9. **AiAgent** - Think → Act → Observe loop
10. **AiChatController** - HTTP endpoint handlers

---

## 🎨 UI Integration

The frontend `ai-chat-builder.ts` component directly calls:
```typescript
private baseUrl = 'http://localhost:8081/api/ai';
```

**No proxy needed!** CORS is enabled in the Router.

---

## 🧪 Testing Checklist

- [ ] Qdrant starts successfully
- [ ] AI Builder service starts on 8081
- [ ] Health check returns `{ status: "UP" }`
- [ ] Chat endpoint responds
- [ ] Agent endpoint responds
- [ ] Frontend UI loads
- [ ] Can send messages in UI
- [ ] Receives AI responses

---

## 🐛 Troubleshooting

### Issue: "Connection refused to localhost:8081"
**Solution**: Ensure AI Builder service is running:
```bash
cd ai-builder
mvn exec:java -Dexec.mainClass="com.appbana.ai.AiBuilderMain"
```

### Issue: "Qdrant connection failed"
**Solution**: Start Qdrant:
```bash
docker run -p 6333:6333 qdrant/qdrant
```

### Issue: "CORS error in browser"
**Solution**: CORS is already enabled in Router. Check browser console for actual error.

### Issue: "Tool execution fails"
**Solution**: Ensure main AppBana service is running on port 8080 (tools call backend APIs).

---

## 📊 Architecture Benefits

✅ **Microservice** - AI Builder runs independently  
✅ **Scalable** - Can scale AI service separately  
✅ **No Servlets** - Uses lightweight HttpServer  
✅ **Router Pattern** - Consistent with main service  
✅ **Direct API Calls** - UI calls AI service directly  
✅ **CORS Enabled** - Works with frontend  

---

## 🎯 Next Steps

1. **Add DataSource** for ConversationMemory (currently null)
2. **Add authentication** for AI endpoints
3. **Add rate limiting** to prevent abuse
4. **Add caching** for frequently used queries
5. **Add logging & monitoring** (OpenTelemetry)
6. **Deploy to production** (Docker, Kubernetes)

---

**✅ AI Builder is ready to test!**

Start the services and try creating an app through the UI! 🚀



# AI Builder Testing Guide

## Overview

This guide will help you test the AI Builder on the UI, including both the regular chat endpoint and the new agent-based endpoint.

---

## 🚀 Quick Start

### 1. Start the Backend Services

```bash
# Terminal 1: Start AI Builder service
cd ai-builder
mvn clean install -DskipTests
mvn exec:java -Dexec.mainClass="com.appbana.ai.AiBuilderMain"
```

The AI Builder service will start on **http://localhost:8081**

### 2. Start the Frontend

```bash
# Terminal 2: Start the UI
cd app-bana-ui
npm install
npm run dev
```

The UI will start on **http://localhost:5173** (or another port if 5173 is busy)

### 3. Access the AI Chat UI

Open your browser and navigate to:
```
http://localhost:5173
```

Look for the **AI Chat Builder** component in the UI.

---

## 📡 Available Endpoints

### Current Endpoints

#### 1. **Health Check**
```bash
curl http://localhost:8081/health
```

Expected Response:
```json
{
  "status": "UP",
  "service": "ai-builder",
  "qdrant": "UP"
}
```

#### 2. **Regular Chat** (Placeholder)
```bash
curl -X POST http://localhost:8081/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Create a customer management app",
    "sessionId": "test-session-123",
    "userId": "test-user"
  }'
```

Current Response (placeholder):
```json
{
  "message": "AI Builder Service is running! Implementation coming soon...",
  "state": "INITIAL",
  "timestamp": "2026-01-09T04:10:30Z"
}
```

---

## 🤖 Testing the Agent (New Feature)

The agent endpoint is implemented in `AiChatController.chatAgent()` but **not yet wired to the server**. Here's how to test it:

### Option 1: Test via Unit Tests (Recommended for now)

The agent has comprehensive unit tests:

```bash
cd ai-builder
mvn test -Dtest=AiAgentTest,EssentialToolsTest
```

This will run 20 tests covering:
- Agent loop execution
- Tool calling
- Error handling
- Max iterations
- Tool validation

### Option 2: Wire Up the Agent Endpoint (Next Step)

To make the agent available via HTTP, you need to:

1. **Update `AiServer.java`** to use `AiChatController` instead of the placeholder `ChatServlet`
2. **Register the agent endpoint** at `/api/ai/chat/agent`

I can help you do this if you'd like!

---

## 🧪 Manual Testing Scenarios

### Scenario 1: Simple Chat
1. Open the UI at http://localhost:5173
2. Type: "Hello"
3. Expected: AI responds with a greeting

### Scenario 2: Create Entity (Once Agent is Wired)
1. Type: "Create a customer entity with name, email, and phone fields"
2. Expected: Agent uses `CreateEntityTool` to create the entity
3. Verify: Check backend logs for tool execution

### Scenario 3: Generate Page (Once Agent is Wired)
1. Type: "Create a customer list page"
2. Expected: Agent uses `GeneratePageTool` to create the page
3. Verify: Page metadata is generated

### Scenario 4: Multi-Step Workflow (Once Agent is Wired)
1. Type: "Build a complete customer management app"
2. Expected: Agent:
   - Creates customer entity
   - Generates list page
   - Generates form page
   - Returns success message

---

## 🔧 Troubleshooting

### Issue: "Connection refused" to localhost:8081
**Solution**: Make sure the AI Builder service is running:
```bash
cd ai-builder
mvn exec:java -Dexec.mainClass="com.appbana.ai.AiBuilderMain"
```

### Issue: "Qdrant connection failed"
**Solution**: Start Qdrant using Docker:
```bash
docker run -p 6333:6333 -p 6334:6334 qdrant/qdrant
```

### Issue: UI shows "Chat request failed"
**Solution**: Check CORS settings and ensure backend is running on port 8081

### Issue: Agent endpoint returns 404
**Solution**: The agent endpoint needs to be wired up in `AiServer.java` (see Option 2 above)

---

## 📊 Monitoring Agent Execution

When the agent is running, you'll see detailed logs:

```
[AGENT] Starting processing for user: test-user
[AGENT] === Iteration 1 ===
[AGENT] Executing 1 tool(s)
[CreateEntityTool] Creating entity with args: {name=Customer, fields=[...]}
[AGENT] [create_entity] Success (150ms)
[AGENT] === Iteration 2 ===
[AGENT] Final answer reached after 2 iterations
```

---

## 🎯 Next Steps

To fully enable agent testing on the UI:

1. **Wire up the agent endpoint** in `AiServer.java`
2. **Update the frontend** to call `/api/ai/chat/agent` for agent-based interactions
3. **Add a toggle** in the UI to switch between regular chat and agent mode
4. **Display agent steps** in the UI to show tool execution

Would you like me to help implement any of these?

---

## 📝 Testing Checklist

- [ ] Backend service starts successfully
- [ ] Health check returns UP
- [ ] Frontend connects to backend
- [ ] Chat UI loads
- [ ] Can send messages
- [ ] Receives responses
- [ ] Agent unit tests pass (20/20)
- [ ] Agent endpoint wired up
- [ ] Agent can create entities
- [ ] Agent can generate pages
- [ ] Multi-step workflows work

---

## 🐛 Known Issues

1. **Agent endpoint not exposed**: The `chatAgent()` method exists but isn't registered in `AiServer.java`
2. **Placeholder chat servlet**: Current `/api/ai/chat` returns a placeholder response
3. **No authentication**: Demo uses hardcoded `demo-user`

---

## 💡 Tips

- Use browser DevTools Network tab to inspect API calls
- Check backend logs for detailed agent execution traces
- Use `debugMode: true` in `AgentConfig` for verbose logging
- Test with simple requests first, then complex multi-step workflows
