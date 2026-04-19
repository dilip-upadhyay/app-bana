# AppBana AI Builder - Active Tasks

## ✅ Completed: AI Schema Quality Stack (feature/ai-schema-quality)

**Branch**: `feature/ai-schema-quality` — 4 commits, ready to review/merge into `main`

| Phase | Summary | Status |
|-------|---------|--------|
| Phase 1 — SchemaEnricher | Type coercion (10 aliases) + baseline field injection (`id`, `created_at`, `updated_at`) in `ScaffoldAppTool` | ✅ Done |
| Phase 2 — Structured Generation | `chatWithJsonMode()` + `chatStructured()` in `OpenAiLlmService`; JSON mode in `AiAgent.think()` | ✅ Done |
| Phase 3 — Dynamic Prompt Builder | `ConversationSpec.java` keyword tracker — injects ✓/✗ spec coverage checklist into every scaffold prompt | ✅ Done |
| Phase 4 — RAG Domain Examples | 8 domain templates in `AppBanaSchemaLoader`; `getDomainExamples()` in `KnowledgeBaseService`; few-shot injection in `AiAgent.buildAgentPrompt()` | ✅ Done |

---

## Current Epic: Intelligent Dialogue (Epic 3)

### 🚀 Next Up: Story 3.1 - Implement Dialogue Manager
**Goal:** Implement a strict state machine `DialogueManager` into `AiChatController` to orchestrate deterministic phases of the app building process, preventing the `AiAgent` LLM from relying purely on autonomous system prompting.

#### Tasks to Complete:
- [ ] **State Machine Logic**: Enhance `com.appbana.ai.dialogue.DialogueManager` to classify active user intents (`INITIAL`, `GATHERING_INFO`, `CONFIRMING_DETAILS`, `CREATING`).
- [ ] **Controller Integration**: Update `com.appbana.ai.api.AiChatController` to proxy requests through `DialogueManager` state checks *before* delegating execution to the `AiAgent`.
- [ ] **Prompt Trimming**: Modify `AiAgent.java` (`buildAgentPrompt`) to dynamically constrain available tools based on the active `ConversationState`. (e.g., Only expose `scaffold_app` when state is `CREATING`).
- [ ] **Tests**: Create Unit tests covering LLM integration with `DialogueManagerTest.java`.

### 📌 Backlog
- Merge `feature/ai-schema-quality` into `main`
- Refine LLM Error Recovery loops (preventing max iteration starvation on failed API calls).
- Enhance Visual Feedback (Vite UI) to show typing indicators while the `AiAgent` executes long-running internal thoughts.
