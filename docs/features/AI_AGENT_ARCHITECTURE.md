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
