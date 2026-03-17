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
