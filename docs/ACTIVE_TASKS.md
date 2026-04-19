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

## ✅ Completed: Intelligent Dialogue — Story 3.1 (Dialogue Manager)

| Task | Summary | Status |
|------|---------|--------|
| State Machine | `DialogueManager` rewritten — `ConcurrentHashMap` per-session, `resolveState()` auto-transitions via `ConversationSpec` | ✅ Done |
| Controller Integration | `AiChatController` injects `DialogueManager`, resolves state before agent call, returns `conversationState` in response | ✅ Done |
| Prompt Trimming | `AiAgent.buildAgentPrompt()` uses `toolRegistry.getToolDescriptions(allowedTools)` filtered by state | ✅ Done |
| Tests | 16 unit tests in `DialogueManagerTest` — all green | ✅ Done |

---

## 📌 Backlog

- **Merge `feature/ai-schema-quality` into `main`**
- Story 3.2 — LLM Error Recovery loops (prevent max iteration starvation on repeated tool failures)
- Story 3.3 — UI Visual Feedback: typing indicator in Vite UI while `AiAgent` executes long-running thoughts
