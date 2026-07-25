# AppBana AI Builder - Active Tasks

## 🚧 In Progress: AI-Native UI Rebuild

**Primary reference:** [`docs/planning/AI_NATIVE_UI_REBUILD_PLAN.md`](./planning/AI_NATIVE_UI_REBUILD_PLAN.md)

Rebuild the AppBana Studio as an AI-native frontend (chat drives everything — no canvas, no palette, no inspector) and segregate the current monolithic `app-bana-ui/` into three pnpm workspace packages: `app-bana-shared`, `app-bana-studio` (port 5174), `app-bana-runtime` (port 5175). Backend gains SSE streaming for the agent, tenant branding, and an app-context resolver.

| Stage | Summary | Status |
|-------|---------|--------|
| Stage 0 — Backend prep | SSE streaming (`/api/ai/chat/agent/stream`), tenant branding endpoint, app-context resolver, verify `ComponentNode.id` stability | ✅ Done — see notes below |
| Stage 1 — Workspace + Studio MVP | pnpm workspace, `app-bana-shared`, `app-bana-studio` MVP with streaming chat + tool cards + preview iframe of old runtime, `data-appbana-*` attrs on old runtime | ✅ Done + fixes applied (auth response shape, localStorage key, deploy btn, page-nav postMessage, SSE regex) |
| Stage 2 — Standalone runtime | `app-bana-runtime` (React port with tenant-branded login), studio iframe repointed 5173 → 5175 | ✅ Done — runtime live at port 5175, E2E passes |
| Stage 3 — Studio v1.1 | Data drawer, session picker upgrade, image paste in chat | ⏳ Not started |
| Stage 4 — Retire `app-bana-ui/` | Delete old UI, full rewrite of copilot-instructions Sections 2 & 3 | ⏳ Not started |
| Stage 5 — Subdomain deploy | DNS + reverse proxy + `Host`-based app resolution | ⏳ Not started |
| Stage 6 — Select-and-instruct | Runtime overlay, selection chips in composer, undo/history drawer | ⏳ Not started |

**Stage 0 notes:**
- `ComponentNode.id` is stable — page IDs derived from page name, node IDs are deterministic counter strings. No fix needed.
- `tenants` table did not exist; created as `appbana_tenants` in V12 Liquibase migration.
- `token` SSE event fires **once at the end** with the full assistant message (not incremental chunks). True LLM token streaming requires adding `stream: true` to `OpenAiLlmService` — deferred to Stage 1.5 (isolated change). Tool call events (`tool_call_start`, `tool_call_end`) do fire in real time as each tool runs.
- `emitter.complete()` is in a `finally` block — client `EventSource` always receives the terminal `done` event even if history storage or dialogue state update fails.

**Locked decisions** (do not renegotiate without approval): pnpm workspaces · SSE streaming (backend work in-scope) · `postMessage` handshake for token (no URL hash) · Runtime own login with tenant branding · React + Vite + Tailwind + shadcn + Zustand · Native `fetch`/`EventSource` (no Vercel AI SDK) · Feature parity NOT required — bar is "AI-native flow fully functional".

See the [full plan](./planning/AI_NATIVE_UI_REBUILD_PLAN.md) for stage-by-stage exit criteria and file-level change lists.

---

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
