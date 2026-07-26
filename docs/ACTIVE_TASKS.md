# AppBana AI Builder - Active Tasks

## 🎯 Forward Plan (post-Stage-4) — Path to First Customer

**Approved 2026-07-26 · Revised 2026-07-26 three times: (1) Phase D inserted after enterprise-app comparison, (2) reordered to A → B → D → C to put fundamentals before enterprise polish, (3) reordered again to A → B → C → D — product before packaging.** The AI-Native UI Rebuild is complete through Stage 4 (`app-bana-ui/` retired). Four phases now stand between us and the first paying customer. Stage 5 (subdomain deploy) runs in parallel, gated on ops rather than code.

| Phase | Goal | Plan doc | Status |
|-------|------|----------|--------|
| **A — Quality Sprint** | Runtime UX Sprint 2: real date picker, sidebar redesign, empty states, loading skeletons, inline validation, status pills, user menu, page actions, WCAG AA, responsive breakpoints | [RUNTIME_UX_OVERHAUL_PLAN.md §Sprint 2](./planning/RUNTIME_UX_OVERHAUL_PLAN.md#sprint-2--make-it-feel-professional) | ⏳ Not started — plan already specced |
| **B — Complex UI Epic** | 5 sub-phases (B1..B5): wizards · conditional fields · file upload · master-detail · list views (filter/group/saved views) | [COMPLEX_UI_PLAN.md](./planning/COMPLEX_UI_PLAN.md) | 📝 Plan drafted 2026-07-26 |
| **C — Maker-Checker Epic** | 5 sub-phases (C1..C5): DB + role model · state machine + guard · approval UI + audit · AI Builder integration · notifications (C5 is v1.1-optional — ships in D3 if deferred) | [MAKER_CHECKER_PLAN.md](./planning/MAKER_CHECKER_PLAN.md) | 📝 Plan drafted 2026-07-26 |
| **D — Enterprise Capabilities** | 4 sub-phases (D1..D4): enterprise SSO (OIDC/Azure B2C) · dashboards + 6 widget primitives · notifications system · multi-level sidebar + header actions + branded login | [ENTERPRISE_CAPABILITIES_PLAN.md](./planning/ENTERPRISE_CAPABILITIES_PLAN.md) | 📝 Plan drafted 2026-07-26 |
| **Stage 5 — Subdomain deploy** | Parallel ops track — DNS, reverse proxy, HTTPS, `Host`-based app resolution | [AI_NATIVE_UI_REBUILD_PLAN.md §Stage 5](./planning/AI_NATIVE_UI_REBUILD_PLAN.md#stage-5--subdomain-deployment) | ⏳ Ops-heavy, tiny code footprint |

**Execution order:** A → B (B1..B5 serial) → C (C1..C5 mostly serial, C4 parallel with C2 — C1+C2 can start in the background while B is landing if we need to compress schedule) → D (D1 + D4 parallelizable, D2 standalone, D3 after D2).

**Total code effort (plan-authored):** ~10 hr (A) + ~29 hr (B) + ~30 hr (C, minus optional C5 ≈ 25 hr) + ~125 hr (D) = **~189–194 hours of focused engineering** to first-enterprise-customer-ready. To *differentiated demo-able product* (A + B + C without D) it's **~64–69 hours**.

**Why C goes before D:** approvals are the *product* (AppBana's differentiator — AI-generated regulated-industry workflows). SSO, dashboards, and enterprise shell are *packaging*. Ship the product first, then package it. Concrete reasoning: (a) every SaaS has SSO, few have AI-generated maker-checker — C is why a customer picks us over the incumbent; (b) C is ~30 hr, D is ~125 hr — C-first means a differentiator lands in ~1 week vs. 4–5 weeks of D infrastructure with nothing new user-visible; (c) prospects will PoC with local auth, they won't PoC with broken approvals; (d) D's specifics (which OIDC provider, group→role mapping, branding) are better co-designed with a real prospect than guessed — delaying D means D1 lands against real requirements.

**Why B goes before C:** B is the foundation approvals stand on. Real approval workflows have file attachments (B3), conditional fields (B2), parent-child records (B4), and a checker inbox that is a list view with filter/group/saved views (B5). C shipped before B would approve a single row of text fields — approval theatre, not the real thing.

**C5 notification tradeoff:** C5 is v1.1-optional. If shipped inside C, it uses a simple polling badge (2–3 hr of throwaway code). Once D3 lands, C5 gets replaced with the durable rule-driven notification substrate. Total cost of the throwaway: negligible.

**Optional schedule compression:** C1 (DB + role model) and C2 (state machine + guard) are pure backend work and could run in parallel with B if we want to shave ~1 week. C3 onward genuinely wants B done first. Not doing this by default — keeping the phases clean — but flagging the option.

**Why Phase D was ever added:** a screenshot-by-screenshot comparison with a live enterprise SaaS on 2026-07-26 showed that A + B + C alone deliver a solid CRUD builder with a differentiated approval workflow, but not a product an enterprise IT department procures without a fight. Enterprise SSO is a categorical gate; dashboards are the first screen executives see; a branded login + multi-level sidebar is what makes an app feel "real" in the first 5 seconds. D is now positioned as the final polish before first-enterprise-customer close, not as a prerequisite for the differentiator.

---

## 🚧 In Progress: AI-Native UI Rebuild

**Primary reference:** [`docs/planning/AI_NATIVE_UI_REBUILD_PLAN.md`](./planning/AI_NATIVE_UI_REBUILD_PLAN.md)

Rebuild the AppBana Studio as an AI-native frontend (chat drives everything — no canvas, no palette, no inspector) and segregate the current monolithic `app-bana-ui/` into three pnpm workspace packages: `app-bana-shared`, `app-bana-studio` (port 5174), `app-bana-runtime` (port 5175). Backend gains SSE streaming for the agent, tenant branding, and an app-context resolver.

| Stage | Summary | Status |
|-------|---------|--------|
| Stage 0 — Backend prep | SSE streaming (`/api/ai/chat/agent/stream`), tenant branding endpoint, app-context resolver, verify `ComponentNode.id` stability | ✅ Done — see notes below |
| Stage 1 — Workspace + Studio MVP | pnpm workspace, `app-bana-shared`, `app-bana-studio` MVP with streaming chat + tool cards + preview iframe of old runtime, `data-appbana-*` attrs on old runtime | ✅ Done + fixes applied (auth response shape, localStorage key, deploy btn, page-nav postMessage, SSE regex) |
| Stage 2 — Standalone runtime | `app-bana-runtime` (React port with tenant-branded login), studio iframe repointed 5173 → 5175 | ✅ Done — runtime live at port 5175, E2E passes |
| Stage 3 — Studio v1.1 | Data drawer, session picker upgrade, image paste in chat | ✅ Done — commit `c9eb4fc`, see notes below |
| Stage 3.5 — Runtime UX Overhaul Sprint 1 (gate) | Design-system foundations + "not embarrassing" fixes (page titles, formatted dates, resolved FK labels, status pills, sticky action bar, toasts). See [Runtime UX Overhaul Plan](./planning/RUNTIME_UX_OVERHAUL_PLAN.md). | ✅ Done — 8/10 tasks shipped, 2 partial by design |
| Stage 4 — Retire `app-bana-ui/` | Delete old UI, full rewrite of copilot-instructions Sections 2, 3 & 5 | ✅ Done — commit `6edd19a` |
| Stage 5 — Subdomain deploy | DNS + reverse proxy + `Host`-based app resolution | ⏳ Not started — ops track, parallel with Phase B/C |
| Stage 6 — Select-and-instruct | Runtime overlay, selection chips in composer, undo/history drawer | ⏳ Deferred to post-launch |

**Stage 0 notes:**
- `ComponentNode.id` is stable — page IDs derived from page name, node IDs are deterministic counter strings. No fix needed.
- `tenants` table did not exist; created as `appbana_tenants` in V12 Liquibase migration.
- `token` SSE event fires **once at the end** with the full assistant message (not incremental chunks). True LLM token streaming requires adding `stream: true` to `OpenAiLlmService` — deferred to Stage 1.5 (isolated change). Tool call events (`tool_call_start`, `tool_call_end`) do fire in real time as each tool runs.
- `emitter.complete()` is in a `finally` block — client `EventSource` always receives the terminal `done` event even if history storage or dialogue state update fails.

**Stage 3 notes:**
- Flyway V005 migration: `app_id` column on `ai_conversations` + new `ai_chat_session_meta` table (title, is_deleted, updated_at). Backward compatible — `mapResultSetToConversation` handles pre-V005 rows.
- New endpoints on ai-builder (port 8081):
  - `GET /api/ai/chat/sessions?userId=X&appId=Y&limit=N` — returns `{sessionId, title, appId, lastActivity, turnCount}` per session. Filters out soft-deleted rows. Falls back to truncated first message when no explicit title.
  - `PUT /api/ai/chat/sessions/{sessionId}` (body `{userId, title}`) — UPSERT into meta.
  - `DELETE /api/ai/chat/sessions/{sessionId}?userId=X` — soft-delete via `is_deleted=TRUE`.
- Frontend `app-bana-studio`:
  - `features/data-drawer/DataDrawer.tsx` — slide-in from right, entity list, first 25 rows paged, schema-driven Add-row form, "Ask AI to seed" bridges back to composer via `studio:composer:set` custom event. (Folder is `data-drawer/` not `data/` because `.gitignore` has a global `**/data/**` rule.)
  - `features/sessions/SessionPicker.tsx` — header dropdown with search, click-to-hydrate, hover-to-reveal rename/delete.
  - `stores/drawer.ts` — `useDrawerStore` for UI-only drawer state.
  - `stores/chat.ts` extended with `loadHistory`, `attachments[]`, `addAttachment/removeAttachment/clearAttachments`.
  - `ChatPane.tsx` — `onPaste` extracts image blobs to base64 (max 5 MB), thumbnails render above textarea, sent as `ChatPayload.images[]` (SSE endpoint already accepts this shape).
  - `Header.tsx` mounts new Data + Sessions buttons; `App.tsx` mounts `DataDrawer`.
- Shared: `listSessions(userId, token, {appId, limit})` (breaking signature change), plus new `getSessionHistory`, `renameSession`, `deleteSession`, `getEntitySchema`, `insertEntityRow`.
- Smoke tests confirmed: 400 on missing userId / bad UUID, list/rename/soft-delete round-trip works end-to-end against the dev DB.
- **Known caveats** (not blockers): Data drawer doesn't auto-refresh entity list when chat scaffolds new entities (user must close/reopen or switch apps). Renaming a session that has zero conversations creates an orphan meta row (harmless, won't appear in list). Backend agent doesn't yet consume `images[]` — visual grounding is a follow-up beyond Stage 3.

**Stage 0-3 audit fixes (post-`51a4418`):**
- **Bug: duplicate `done` SSE event** — `AgentStreamController.buildEmitter().complete()` fired a second empty `done` after the agent's own terminal `done`, causing `setSessionId('')` to race the real value on the client. Fixed by tracking whether `done` was already emitted inside `emit()` and making `complete()` a no-op in that case. Confirmed via smoke test: `1` done event per SSE response (was `2`).
- **Bug: unguarded `window.parent.postMessage` in runtime error path** — `AppRuntimeShell.tsx`'s `loadApp` catch block called `window.parent.postMessage(..., STUDIO_ORIGIN)` unconditionally, causing a benign self-post + console spam when the runtime ran standalone. Extracted `postToStudio()` helper with `isEmbedded()` guard; all three send sites now use it.
- **Stage 2 contract gap: missing `setMode`/`highlight` handlers** — runtime's postMessage switch only handled `token`/`setPage`/`reload`. Plan requires acknowledging `setMode` (browse↔inspect) and `highlight` (nodeId) even though Stage 6 will drive them. Added handler cases that store values in refs so Stage 6 can promote them to state.
- **Stage 3 deviation: entity list missing row counts** — plan says "entity list with row counts", implementation showed names only. Added new `fetchEntityRowCount(entityKey, token)` in `@appbana/shared` (uses `?_count=true`), DataDrawer now issues parallel count fetches after listing entities and shows a pill next to each name.
- **Stage 3 deviation: table not sortable** — plan says "first N rows paged sortable", implementation was paged only. Added click-to-sort headers with a 3-state cycle (asc → desc → cleared), passes `_sort=col:dir` to `fetchEntityRows`, resets to page 0 on sort change, and shows an ↑/↓ indicator on the active column.
- **Non-fixes / intentional deviations from plan wording:**
  - Plan says `AppContext` response should have keys `tenantBranding` + `appMeta`; implementation uses `branding` (no `appMeta`). Frontend + backend + shared type are internally consistent, no consumer needs `appMeta` yet, so renaming is churn without benefit. Left as-is with this note.
  - Plan mentions `getSnapshot` as a studio→runtime postMessage. Not needed until Stage 6; not implemented to avoid speculative contract.
  - `token` SSE event still fires once at the end (not per-chunk streaming from OpenAI). Documented under Stage 0 notes above — remains deferred to a future opt-in change.

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
