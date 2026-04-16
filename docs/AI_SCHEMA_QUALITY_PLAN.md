# AI Schema Quality Improvement Plan
**Branch:** `feature/ai-schema-quality`  
**Created:** April 17, 2026

---

## Problem Statement

The current AI agent relies entirely on prompt engineering for schema generation, leading to:
1. **Type bugs** — LLM generates invalid field types (`currency`, `float`, `string`) that silently fall back to `VARCHAR(255)`
2. **Missing required fields** — No guarantee every entity has `id`, `created_at`, `updated_at`
3. **Semantic quality** — LLM generates technically valid but business-wrong schemas without domain context
4. **Conversation quality** — Agent may skip clarification or rush to build without covering all requirements

---

## Solution: 4-Layer Quality Stack

Each layer is independent. They can be shipped one at a time.

```
User message
     ↓
[Layer 3] Dynamic Prompt Builder          ← conversation quality
  ├── Inject: domain RAG examples         ← semantic quality
  ├── Inject: missing info checklist
  └── Inject: session decisions so far
     ↓
LLM generates response
     ↓
[Layer 1] Structured Generation           ← type safety
     ↓
[Layer 2] SchemaEnricher                  ← missing required fields
     ↓
ScaffoldAppTool executes
```

---

## Implementation Phases

### Phase 1 — SchemaEnricher (DONE FIRST)
**File:** `ai-builder/src/main/java/com/appbana/ai/agent/tool/SchemaEnricher.java`  
**Effort:** ~1 hour  
**Risk:** Zero — pure Java, post-LLM processing

**What it does:**
- Merges LLM-generated entity fields with a mandatory baseline
- Every entity always gets: `id` (PK, autoIncrement), `created_at` (datetime), `updated_at` (datetime)
- Deduplicates — if LLM already added `id`, does not add it again
- Applied inside `ScaffoldAppTool.execute()` before entities are posted to the backend

**Baseline fields:**
```json
[
  { "id": "id",         "name": "id",         "type": "number",   "primaryKey": true,  "autoIncrement": true, "required": true  },
  { "id": "created_at", "name": "created_at", "type": "datetime", "primaryKey": false, "autoIncrement": false,"required": false },
  { "id": "updated_at", "name": "updated_at", "type": "datetime", "primaryKey": false, "autoIncrement": false,"required": false }
]
```

---

### Phase 2 — Structured Generation
**File:** `ai-builder/src/main/java/com/appbana/ai/llm/OpenAiLlmService.java`  
**File:** `ai-builder/src/main/java/com/appbana/ai/agent/AiAgent.java`  
**Effort:** ~half a day  
**Risk:** Low — only applies to `scaffold_app` tool calls, not general chat

**What it does:**
- Adds `response_format` with `json_schema` + `strict: true` to OpenAI API requests for schema generation calls
- The LLM is mathematically constrained to only output field types from the approved enum:
  `["text","longtext","number","decimal","boolean","date","datetime","email","phone","status","reference"]`
- Invalid type bug becomes structurally impossible at the model level

**API change (OpenAI):**
```json
{
  "response_format": {
    "type": "json_schema",
    "json_schema": {
      "name": "ScaffoldSpec",
      "strict": true,
      "schema": { ... AppBana entity schema ... }
    }
  }
}
```

**Implementation notes:**
- Add `chatStructured(String prompt, String jsonSchema)` method to `OpenAiLlmService`
- The `theokanning/openai-java` library may need upgrade or direct HTTP call (check library version support)
- If the library doesn't support `response_format`, use `java.net.http.HttpClient` directly (same pattern as `ScaffoldAppTool`)

---

### Phase 3 — Dynamic Prompt Builder (Missing Info Checklist)
**File:** `ai-builder/src/main/java/com/appbana/ai/dialogue/ConversationSpec.java` (new)  
**File:** `ai-builder/src/main/java/com/appbana/ai/llm/AdvancedPromptEngine.java` (modify)  
**Effort:** ~1 day  
**Risk:** Medium — touches prompt construction

**What it does:**
- Tracks a `ConversationSpec` per session: `{ entitiesDiscussed, rolesDiscussed, relationshipsDiscussed, userConfirmed }`
- `AdvancedPromptEngine` injects a dynamic checklist of what's still missing
- LLM naturally asks about missing items without being forced into a rigid sequence

**Example injected prompt segment:**
```
CURRENT SPEC STATUS:
  ✓ Core entities identified: Customer, Order, Product
  ✗ User roles/access: not discussed yet
  ✗ Data relationships: unclear
  ✗ Reporting/analytics needs: not asked

Ask about missing items naturally before proceeding to build.
```

**Flexibility preserved:** Conversation flows freely. The checklist is a nudge, not a gate.

---

### Phase 4 — RAG Domain Examples
**File:** `ai-builder/src/main/java/com/appbana/ai/agent/tool/ScaffoldAppTool.java` (modify)  
**File:** `builder-database/` (add domain schema templates)  
**Effort:** ~1-2 days  
**Risk:** Low — additive only

**What it does:**
- Before generating a schema, retrieves similar domain examples from Qdrant vector store
- Injects them as few-shot examples in the scaffold prompt
- LLM produces business-correct schemas by learning from proven patterns

**Example retrieval:**
```
User wants: "spice shop"
Retrieved: E-commerce schema template (Product, Category, Order, OrderItem)
Injected as few-shot example in scaffold prompt
```

**Data needed:**  
Add 10-15 domain schema templates to `builder-database/` covering:
- E-commerce, CRM, HR, Project Management, Healthcare, Education, Restaurant, Finance

---

## File Change Summary

| File | Change | Phase |
|------|--------|-------|
| `SchemaEnricher.java` | New class | 1 |
| `ScaffoldAppTool.java` | Call SchemaEnricher after LLM response | 1, 4 |
| `OpenAiLlmService.java` | Add `chatStructured()` method | 2 |
| `AiAgent.java` | Pass `response_format` for scaffold calls | 2 |
| `ConversationSpec.java` | New class (session state) | 3 |
| `AdvancedPromptEngine.java` | Inject checklist into system prompt | 3 |
| `builder-database/11-domain-schemas.json` | Domain schema templates | 4 |

---

## Testing

| Test | What to verify |
|------|---------------|
| `SchemaEnricherTest.java` | Baseline fields always present, no duplicates |
| `StructuredGenerationTest.java` | Invalid types rejected at API level |
| `DynamicPromptTest.java` | Checklist correctly identifies missing items |
| Manual end-to-end | Scaffold a spice shop, verify `decimal` used for price, `id` present |

---

## Success Criteria

- [ ] Schema with `price: currency` from LLM → auto-corrected to `price: decimal`
- [ ] Every scaffolded entity has `id`, `created_at`, `updated_at`
- [ ] LLM cannot output a field type not in the approved enum (Phase 2)
- [ ] Agent asks about user roles before confirming spec (Phase 3)
- [ ] Spice shop scaffold produces `weight_grams: number`, `price: decimal` not `VARCHAR` (Phase 4)
