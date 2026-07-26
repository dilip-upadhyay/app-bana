# Session Summary: Story 3.1 — DialogueManager Implementation

## What Was Built

### Core State Machine (`DialogueManager.java` — full rewrite)
- **Per-session state** stored in `ConcurrentHashMap<String, ConversationState>` — true isolation per tab/user
- **`resolveState(sessionId, history, message)`** — auto-transitions via `ConversationSpec` keyword analysis
- **State ladder**: `GREETING` → `GATHERING_REQUIREMENTS` → `CONFIRMING` → `GENERATING` → `COMPLETED`
- **`notifyScaffolding()`** / **`notifyCompleted()`** — controller-driven hooks for post-tool transitions
- `GENERATING` and `COMPLETED` are locked — `resolveState()` cannot auto-regress them

### Hard Tool Filtering (`ToolRegistry.java` + `AiAgent.java`)
- Added `getToolDescriptions(Set<String> allowedTools)` overload to `ToolRegistry`
- `AiAgent.buildAgentPrompt()` now reads `conversation_state` from `AgentContext` and calls the filtered overload
- **In `GREETING` / `GATHERING_REQUIREMENTS`**: `scaffold_app`, `create_app`, `deploy_app`, `generate_mock_data`, etc. are **completely hidden from the LLM** — not just a prompt hint, a hard filter
- **In `CONFIRMING` and beyond**: all tools unlocked

### Controller Integration (`AiChatController.java`)
- `DialogueManager` injected as a constructor parameter
- Before every agent call: `resolveState()` → stored in `AgentContext` as `"conversation_state"`
- After success: checks response text for build keywords → triggers `notifyScaffolding()` / `notifyCompleted()`
- API response now includes `"conversationState": "GATHERING_REQUIREMENTS"` etc.

### Tests (`DialogueManagerTest.java` — 16 tests, all green)
- GREETING → GATHERING_REQUIREMENTS on entity keywords
- GATHERING_REQUIREMENTS → CONFIRMING on confirmation keywords
- `notifyScaffolding/notifyCompleted` explicit hooks
- State locking (GENERATING/COMPLETED don't auto-regress)
- Tool-set gating per state (build tools hidden/exposed correctly)
- Session isolation (different UUIDs are fully independent)

## Next Session Goal
- **Backlog**: Merge `feature/ai-schema-quality` → `main`
- **Next Epic 3 Story**: LLM Error Recovery loops (preventing max iteration starvation on failed API calls)
- **Enhancement**: Visual Feedback — Vite UI typing indicator while `AiAgent` executes

---

# Session Summary: AI-Native UI Rebuild — Planning Phase (2026-07-25)

## What Was Decided

Approved and locked the implementation plan for a full **AI-native UI rebuild** of the AppBana Studio. Primary reference document: [`docs/planning/AI_NATIVE_UI_REBUILD_PLAN.md`](./planning/AI_NATIVE_UI_REBUILD_PLAN.md).

### Direction
- Rebuild the Studio as an AI-native frontend — chat drives everything, no canvas / palette / property inspector.
- Segregate the current monolithic `app-bana-ui/` into three pnpm workspace packages:
  - `app-bana-shared` — types + api client + postMessage schema + app-context resolver
  - `app-bana-studio` (port **5174**) — the AI-native builder (streaming chat + tool cards + preview iframe + data drawer)
  - `app-bana-runtime` (port **5175**) — standalone renderer for deployed apps (own login, tenant-branded)
- `PageMeta` / `ComponentNode` schema is the boundary contract; studio and runtime communicate only via URL + `postMessage`, never direct imports.

### Locked Stack
- Vite 5 + React 18 + TypeScript + Tailwind + shadcn/ui + Zustand
- Native `fetch` + `ReadableStream` for streaming (Vercel AI SDK dropped — custom event shape)
- pnpm workspaces

### Backend Additions (in scope, Stage 0)
1. **SSE streaming** — `POST /api/ai/chat/agent/stream` on ai-builder with events: `token`, `tool_call_start`, `tool_call_end`, `state`, `done`
2. **Tenant branding** — Liquibase changeset + public `GET /api/tenants/{id}/branding`
3. **App-context resolver** — `GET /api/app-context` (subdomain-ready)
4. **Verify `ComponentNode.id` stability** across `generate_page` re-runs

### Delivery Stages (see plan doc for full detail)
| Stage | Summary |
|-------|---------|
| 0 | Backend prep — SSE, branding, app-context, node-id verification |
| 1 | Workspace skeleton + Studio MVP (embeds old runtime via iframe) |
| 2 | Standalone runtime package (React port with tenant-branded login) |
| 3 | Studio v1.1 — data drawer, session picker upgrade, image paste |
| 4 | Retire `app-bana-ui/` entirely |
| 5 | Subdomain deployment |
| 6 | Select-and-instruct UX (click element in preview → attach to chat) |

### Cross-cutting Decisions
- Runtime has **its own login screen** with tenant branding loaded pre-login (`GET /api/tenants/{id}/branding` is public).
- Studio → Runtime token passing via **postMessage handshake**, NOT URL hash (security).
- Foundations for future "select and instruct" baked into Stages 1–2 (`data-appbana-node|entity|field|page` attrs + postMessage bridge).
- Feature parity with old UI is **NOT** required — bar is "AI-native flow fully functional".
- Old `app-bana-ui/` runs unchanged through Stages 1–3, retired in Stage 4.
- Ports: studio 5174, runtime 5175, old ui 5173, backend 8080, ai 8081.

## Documentation Updated
- **NEW** [`docs/planning/AI_NATIVE_UI_REBUILD_PLAN.md`](./planning/AI_NATIVE_UI_REBUILD_PLAN.md) — primary reference (comprehensive, phase-wise)
- **UPDATED** [`docs/ACTIVE_TASKS.md`](./ACTIVE_TASKS.md) — added "🚧 In Progress" section pointing to the plan
- **UPDATED** [`docs/README.md`](./README.md) — added link in Planning section
- **UPDATED** [`docs/planning/03-ROADMAP.md`](./planning/03-ROADMAP.md) — added AI-Native UI Rebuild phase reference
- **UPDATED** [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) — pointer note in Section 12 (Active Work). Full rewrite of Sections 2 & 3 deferred to end of Stage 1 (workspace layout won't exist until then).

## Next Session Goal
**Begin Stage 0 (Backend prep):**
1. Read `AiAgent.java` and `AiChatController.java` to understand current tool-call emission shape.
2. Read `metadata.ts` and `GeneratePageTool.java` to verify `ComponentNode.id` stability.
3. Check if a `tenants` table exists (decides Liquibase strategy).
4. Implement `AgentStreamController` (SSE) with the 5-event schema.
5. Add tenant branding columns + `TenantBrandingRoutes`.
6. Add `AppContextRoutes` for path + Host resolution.
