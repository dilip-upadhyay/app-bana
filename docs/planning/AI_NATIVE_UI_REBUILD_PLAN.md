# AI-Native UI Rebuild — Implementation Plan

**Status:** ✅ Stages 0-4 shipped · ⏳ Stage 5 (Production Deploy — rescoped 2026-07-26) awaiting execution · ⏳ Stage 6 (select-and-instruct) deferred to post-launch
**Approved:** 2026-07-25 · **Stage 4 shipped:** 2026-07-26 (commit `6edd19a`)
**Owner:** AppBana core team
**Primary reference:** This document is the single source of truth for the AI-native UI rebuild. All other docs (ACTIVE_TASKS, session summaries, roadmap, `.github/copilot-instructions.md`) link back here.

**Related active plans:**
- [Runtime UX Overhaul Plan](./RUNTIME_UX_OVERHAUL_PLAN.md) — Sprint 1 gated Stage 4 (✅ done). Sprint 2 = **Phase A** in the current forward plan.
- [Complex UI Plan](./COMPLEX_UI_PLAN.md) — **Phase B**: wizards, conditional fields, file upload, master-detail, list views.
- [Maker-Checker Plan](./MAKER_CHECKER_PLAN.md) — **Phase C**: approval workflows.
- Live status: [`ACTIVE_TASKS.md`](../ACTIVE_TASKS.md).

---

## Table of Contents

1. [TL;DR](#tldr)
2. [Why we are rebuilding](#why-we-are-rebuilding)
3. [Non-goals](#non-goals)
4. [Target architecture](#target-architecture)
5. [Locked decisions](#locked-decisions)
6. [Stage 0 — Backend prep](#stage-0--backend-prep)
7. [Stage 1 — Workspace skeleton + Studio MVP](#stage-1--workspace-skeleton--studio-mvp)
8. [Stage 2 — Standalone runtime package](#stage-2--standalone-runtime-package)
9. [Stage 3 — Studio v1.1 enhancements](#stage-3--studio-v11-enhancements)
10. [Stage 4 — Retire `app-bana-ui/`](#stage-4--retire-app-bana-ui)
11. [Stage 5 — Production Deploy](#stage-5--production-deploy)
12. [Stage 6 — Select-and-instruct UX](#stage-6--select-and-instruct-ux)
13. [Cross-cutting concerns](#cross-cutting-concerns)
14. [Open verifications](#open-verifications)
15. [File-level change list](#file-level-change-list)

---

## TL;DR

Rebuild the AppBana Studio as an **AI-native** frontend: chat drives everything — no canvas, no palette, no property inspector. Segregate the current monolithic [`app-bana-ui/`](../../app-bana-ui) into three pnpm workspace packages:

| Package | Purpose | Port |
|---|---|---|
| `app-bana-shared` | Types + api client + postMessage schema + app-context resolver | — |
| `app-bana-studio` | The AI-native builder (chat + streaming tool cards + preview iframe + data drawer) | 5174 |
| `app-bana-runtime` | Standalone renderer for deployed apps (own login, tenant-branded) | 5175 |

The `PageMeta` / `ComponentNode` metadata schema is the boundary contract between studio and runtime — communication happens over `postMessage` (never direct imports), which keeps the runtime deployable independently and unblocks future subdomain hosting.

Backend gains three additions in Stage 0: SSE streaming for the agent, tenant branding, and an app-context resolver.

---

## Why we are rebuilding

The current [`app-bana-ui/`](../../app-bana-ui) mixes three concerns in one project:

1. **A drag-and-drop Studio builder** (canvas, component library, inspector, tree store, token/theme editor, page manager, workflow designer)
2. **An AI chat side panel** ([`src/components/ai-builder/ai-chat-builder.ts`](../../app-bana-ui/src/components/ai-builder/ai-chat-builder.ts)) bolted into the Studio's left tab
3. **A runtime renderer** ([`src/runtime/`](../../app-bana-ui/src/runtime)) that walks a `PageMeta` tree and renders live pages against the backend's dynamic entity CRUD APIs

The canvas era left a lot of code that no longer serves an AI-native flow where the human never touches a canvas — they just chat, and the app appears and evolves. The rebuild throws away the canvas/palette/inspector, keeps the metadata contract, and reshapes the whole experience around conversation with the agent.

Additional benefits of the split:

| Reason | Impact |
|---|---|
| Deployed apps ship no builder code | Smaller bundle, faster load for end users |
| Independent release cadence | Iterate on studio UX without risking production runtime |
| Security surface | Runtime never needs to know about `/api/ai/*` or builder auth tokens |
| Reusability | Same runtime can later power a mobile shell, embedded widget, or whitelabel host |
| AI-friendliness | Each project has one job → AI agents (and humans) reason about it faster |
| Clean contract | The `PageMeta` schema becomes an actual API boundary, not an accidental one |

---

## Non-goals

Explicitly out of scope for this rebuild:

- Feature parity with old [`app-bana-ui/`](../../app-bana-ui) canvas / palette / inspector / workflow-designer / schema-builder
- Migrating LitElement components as-is (all UI is React from Stage 1 onwards)
- Real-time collaborative editing
- Mobile-native shell (unblocked by runtime segregation, but not delivered here)
- Keeping the old drag-and-drop UX alive after Stage 4

The bar for "done" is **"AI-native flow is fully functional"**, not feature parity.

---

## Target architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                            USER'S BROWSER                             │
│                                                                       │
│  ┌────────────────── app-bana-studio (5174) ─────────────────────┐  │
│  │  Header  · App switcher · New · Deploy · User                 │  │
│  │  ┌──────────────────────┬─────────────────────────────────┐   │  │
│  │  │  Chat pane            │  Preview pane                   │   │  │
│  │  │  ─────────────────    │  ┌───────────────────────────┐  │   │  │
│  │  │  streaming SSE        │  │ iframe                    │  │   │  │
│  │  │  tool call cards      │  │ src=…/run/{tenant}/{app}  │  │   │  │
│  │  │  session picker       │  │                           │  │   │  │
│  │  │  suggestion chips     │  │ (postMessage bridge)      │  │   │  │
│  │  │  composer + attach    │  │                           │  │   │  │
│  │  │                       │  │ [Data drawer, Stage 3]    │  │   │  │
│  │  └──────────────────────┴─────────────────────────────────┘   │  │
│  └───────────────────────────┬───────────────────────────────────┘  │
│                              │ iframe                                 │
│  ┌───────────────────────────▼───────────────────────────────────┐  │
│  │              app-bana-runtime (5175)                          │  │
│  │  ┌─────────────────────────────────────────────────────────┐  │  │
│  │  │  Own login (tenant-branded, pre-login /api/tenants/     │  │  │
│  │  │  {id}/branding call)                                    │  │  │
│  │  │  React port of Renderer / StudioTableLive / AppRuntimeShell │  │
│  │  │  Emits data-appbana-node|entity|field|page              │  │  │
│  │  │  postMessage listener (mode, highlight, selection)      │  │  │
│  │  └─────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────┬───────────────────────────────────┘  │
└──────────────────────────────┼───────────────────────────────────────┘
                               │ HTTP + SSE
      ┌────────────────────────┼─────────────────────────┐
      │                        │                         │
┌─────▼──────────────┐  ┌──────▼──────────┐  ┌──────────▼────────────┐
│  ai-builder (8081) │  │ app-bana-service│  │  Postgres / Qdrant    │
│  /api/ai/chat/     │  │  (8080)         │  │                       │
│    agent/stream    │  │  /api/tenants/  │  │                       │
│  (SSE)             │  │    {id}/branding│  │                       │
│                    │  │  /api/app-context│  │                       │
│                    │  │  /appbana-studio│  │                       │
│                    │  │  /api/{entity}  │  │                       │
└────────────────────┘  └─────────────────┘  └───────────────────────┘
```

### Package boundary rules

- `app-bana-studio` **never imports code from `app-bana-runtime`**. They communicate only via URL + `postMessage`.
- Both packages import types + api-client from `app-bana-shared`.
- Backend contracts do not change signatures (only additions: streaming endpoint, branding, app-context).

---

## Locked decisions

| Item | Choice |
|---|---|
| Package manager | pnpm workspaces |
| Structure | 3 packages: `app-bana-shared`, `app-bana-studio`, `app-bana-runtime` |
| Streaming | SSE via new endpoint on ai-builder (backend work IS in-scope) |
| Client streaming lib | Native `fetch` + `ReadableStream` / `EventSource` — **NOT** Vercel AI SDK (event shape is custom) |
| Studio → Runtime token | `postMessage` handshake (NOT URL hash — security) |
| Runtime login | Yes, with tenant branding loaded pre-login |
| App resolution | Pluggable function: path today (`/run/:tenant/:app`), hostname later (subdomain) |
| Subdomain deploy | Design in v1, delivered as Stage 5.1 inside the rescoped Stage 5 (Production Deploy) — see [§Stage 5](#stage-5--production-deploy) |
| Tenant branding | Small backend addition: columns on tenants + public `GET /api/tenants/{id}/branding` |
| Data drawer, session picker, image paste | Stage 3 (v1.1) |
| Select-and-instruct feature | Stage 6 (v2) — foundations (`data-appbana-*` attrs + postMessage) baked into Stages 1–2 |
| ComponentNode ID stability | Verify + fix in Stage 0 |
| Old [`app-bana-ui/`](../../app-bana-ui) | Kept running during Stages 1–2, retired in Stage 4 |
| Feature parity check | NOT required — bar is "AI-native flow fully functional" |
| Ports | studio 5174, runtime 5175, old ui 5173, backend 8080, ai 8081 |
| UX stance | Preview = first-class INPUT surface (hover highlight from day one), not just output |

### Frontend framework stack

- Vite 5 + React 18 + TypeScript
- Tailwind CSS + shadcn/ui
- Zustand (state)
- React Router (or TanStack Router — decided at Stage 1 scaffold time)
- Native `fetch` / `EventSource` for streaming (no AI SDK)

---

## Stage 0 — Backend prep

**Mandatory before Stage 1.** All work in [`ai-builder/`](../../ai-builder) and [`app-bana-service/`](../../app-bana-service).

### 0.1 — Verify `ComponentNode.id` stability

- Read [`app-bana-ui/src/models/metadata.ts`](../../app-bana-ui/src/models/metadata.ts)
- Read [`ai-builder/src/main/java/com/appbana/ai/agent/tool/GeneratePageTool.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/GeneratePageTool.java)
- Confirm every node written by the agent gets a stable `id` that survives regeneration
- **Fix** if unstable — the `data-appbana-node` attribute story and future select-and-instruct UX depend on this

### 0.2 — SSE streaming endpoint on ai-builder

**New route:** `POST /api/ai/chat/agent/stream`
**New class:** `AgentStreamController` in [`ai-builder/src/main/java/com/appbana/ai/api/`](../../ai-builder/src/main/java/com/appbana/ai/api)
**Modify:** [`AiAgent`](../../ai-builder/src/main/java/com/appbana/ai/agent/AiAgent.java) to accept an optional `EventEmitter` callback

Event schema (SSE `event:` + JSON `data:`):

| Event | Payload |
|---|---|
| `token` | `{ text: "..." }` — streamed LLM text chunks |
| `tool_call_start` | `{ id, name, args }` — when a tool begins execution |
| `tool_call_end` | `{ id, status: "ok"\|"error", result }` — when a tool completes |
| `state` | `{ conversationState: "GATHERING_REQUIREMENTS" }` — DialogueManager state changes |
| `done` | `{ conversationId, finalMessage }` — final marker |

- Keep old sync `/api/ai/chat/agent` intact for the current UI (backward compat)
- Verify SSE flushes correctly through OpenAI's own streaming API

### 0.3 — Tenant branding storage

- **Liquibase changeset** in [`app-bana-service/src/main/resources/db/changelog/`](../../app-bana-service/src/main/resources/db/changelog)
  - Add `display_name`, `logo_url`, `primary_color` (all nullable) to `tenants` table
  - If no `tenants` table exists yet, create a minimal one keyed by `tenant_id`
- **New route:** `GET /api/tenants/{tenantId}/branding`
  - **PUBLIC** (no auth required — must work pre-login for the runtime)
  - Returns `{ tenantId, displayName, logoUrl, primaryColor }` with sane defaults if row missing
- **New class:** `TenantBrandingRoutes` in [`app-bana-service/src/main/java/com/appbana/server/routes/`](../../app-bana-service/src/main/java/com/appbana/server/routes)

### 0.4 — App context resolver (subdomain-ready)

- **New route:** `GET /api/app-context?host=...&path=...`
- Returns `{ tenantId, appId, tenantBranding, appMeta }`
- Runtime calls this on boot to determine which app to render
- Design supports both `/run/:tenant/:app` path parsing and future `spice-shop.tenant42.apps.appbana.com` hostname parsing
- **New class:** `AppContextRoutes` in [`app-bana-service/src/main/java/com/appbana/server/routes/`](../../app-bana-service/src/main/java/com/appbana/server/routes)

### Exit criteria — Stage 0

- `curl` proves SSE streaming works with all 5 event types
- Branding endpoint returns JSON for any tenant id (defaults for missing rows)
- Context endpoint resolves both path-style and Host-header-style requests
- Node IDs verified stable across `generate_page` re-runs

---

## Stage 1 — Workspace skeleton + Studio MVP

Frontend workspace work. Ships the AI-native studio, embedding the **existing** [`app-bana-ui`](../../app-bana-ui) runtime on 5173 via iframe. Nothing in the old UI is retired yet.

### 1.1 — Root workspace setup

- Create `pnpm-workspace.yaml` at repo root
- Update root `package.json` — declare workspaces + scripts (`dev:studio`, `dev:runtime`, `build:all`)
- Migrate [`app-bana-ui/`](../../app-bana-ui) to a workspace member (its `node_modules` moves under pnpm-managed structure)
- Verify `pnpm --filter app-bana-ui dev` still works — the old UI must remain unbroken

### 1.2 — `app-bana-shared/`

New package. Pure TypeScript, no React, no framework code.

- `src/metadata.ts` — port `PageMeta`, `ComponentNode`, `Layout`, `Node` types from [`app-bana-ui/src/models/metadata.ts`](../../app-bana-ui/src/models/metadata.ts)
- `src/entities.ts` — `EntitySchema`, `FieldType` enum
- `src/api-client.ts` — typed fetch wrappers for backend endpoints (auth, apps, pages, entity CRUD, branding, context)
- `src/postmessage.ts` — `AppBanaPostMessage` union type shared between studio and runtime
- `src/app-context.ts` — `resolveAppContext(location)` pluggable resolver (path today, hostname later)
- Publishes as `@appbana/shared` inside workspace (no npm publish yet)

### 1.3 — `app-bana-studio/`

The AI-native builder MVP. Runs on port **5174**.

**Stack:** Vite + React 18 + TS + Tailwind + shadcn/ui + Zustand

**Features shipped in this stage:**

- **Login screen** — calls `/api/auth/login` on backend, stores JWT in localStorage + memory
- **Header** — logo, app switcher dropdown, `＋ New app`, Deploy button, user menu
- **Chat pane (left):**
  - Consumes new SSE endpoint via `fetch` + `ReadableStream` reader
  - Message bubbles (user right, assistant left, markdown-rendered)
  - **Tool call cards** — one card per `tool_call_start`, updates in-place on `tool_call_end` (badge ✓/✗), collapsible details
  - Composer: textarea, Send/Stop button (Stop cancels the SSE stream)
  - Suggestion chips (context-aware, static list for now)
  - Session picker (minimal: dropdown of past chats for current app via `GET /api/ai/chat/sessions`)
- **Preview pane (right):**
  - `<iframe src="http://localhost:5173/run/{tenant}/{app}">` pointing at existing [`app-bana-ui`](../../app-bana-ui) runtime
  - Toolbar: page tabs (from app metadata), refresh, device size toggle, open-in-new-tab
  - **postMessage handshake:** iframe posts `{type: 'ready'}` on load, studio replies with `{type: 'token', jwt}` and `{type: 'setMode', mode: 'browse'}`
  - Preview auto-refreshes after any tool card reports success
  - Empty-state overlay when no app selected
- **Tenant branding** — fetch on boot, apply logo to header + `primaryColor` as CSS var accent

**Data model:**
- `Draft { text: string; attachments: SelectionRef[] }` — attachments empty in v1 (populated in Stage 6)
- Chat payload includes optional `context.selections` (backend ignores in v1 — makes future work zero-migration)

### 1.4 — `data-appbana-*` attributes on old runtime

Small edit to existing [`app-bana-ui/src/runtime/renderer/Renderer.ts`](../../app-bana-ui/src/runtime/renderer/Renderer.ts) and [`StudioTableLive.ts`](../../app-bana-ui/src/runtime/renderer/StudioTableLive.ts) to emit `data-appbana-node|entity|field|page` on every rendered element.

- Old UI still works
- Studio iframe now has selection foundations for later stages

### 1.5 — postMessage bridge stubs on both sides

Studio ⇄ old runtime message types (defined in `app-bana-shared/src/postmessage.ts`):

- `ready` — runtime → studio, on load
- `token` — studio → runtime, JWT delivery
- `setMode` — studio → runtime, `'browse' | 'inspect'`
- `highlight` — runtime → studio, hover metadata
- `selection` — runtime → studio, click selection (stub in Stage 1)

Small edit to [`app-bana-ui/src/runtime/shell/AppRuntimeShell.ts`](../../app-bana-ui/src/runtime/shell/AppRuntimeShell.ts) to accept postMessage token instead of only relying on localStorage.

### 1.6 — Launch scripts

- New: `scripts/start-studio.bat` / `.sh` (port 5174)
- Update: `scripts/start-everything.bat` / `.sh` to launch studio after ui
- Existing scripts untouched

### 1.7 — E2E test

- New: `e2e/tests/ai-builder-chat.studio.spec.ts`
  - Playwright test on `http://localhost:5174/`
  - Register → studio login → prompt "contact list app" → assert tool card appears → assert iframe reloads with new app
- Old [`ai-builder-chat.spec.ts`](../../e2e/tests/ai-builder-chat.spec.ts) stays green

### Exit criteria — Stage 1

A non-technical user can:
1. Register
2. Log into studio at `http://localhost:5174`
3. Describe an app in chat (e.g. "I want a contact list app")
4. Watch tool cards stream live as the agent scaffolds
5. See the preview iframe render the new app
6. Click Deploy

…all through the studio, no manual page building.

---

## Stage 2 — Standalone runtime package

New `app-bana-runtime/` package on port **5175**. React port of the runtime, deployable independently.

### 2.1 — Vite + React project scaffold

- Same stack as studio (Vite + React 18 + TS + Tailwind)
- Runs on port **5175**

### 2.2 — Login screen with tenant branding

- Loads `GET /api/tenants/{tenantId}/branding` **before** rendering the login form
- Renders tenant logo, name, primary-color accent
- Calls `/api/auth/login`, stores JWT
- If loaded via studio iframe with `postMessage` token, **skips login entirely**

### 2.3 — React port of the runtime

- `Renderer.tsx` — port of [`app-bana-ui/src/runtime/renderer/Renderer.ts`](../../app-bana-ui/src/runtime/renderer/Renderer.ts)
- `StudioTableLive.tsx` — port of [`app-bana-ui/src/runtime/renderer/StudioTableLive.ts`](../../app-bana-ui/src/runtime/renderer/StudioTableLive.ts)
- `AppRuntimeShell.tsx` — port of [`app-bana-ui/src/runtime/shell/AppRuntimeShell.ts`](../../app-bana-ui/src/runtime/shell/AppRuntimeShell.ts)
- Emits `data-appbana-node|entity|field|page` on every element
- Uses `@appbana/shared` types + api client — no other framework deps beyond React

### 2.4 — postMessage listener

Receives from studio parent:
- `token` — accept JWT, skip login
- `setMode` — switch between `'browse'` and `'inspect'`
- `highlight` — force-highlight a node by id
- `getSnapshot` — dump current app state

Sends to studio parent:
- `ready` — on mount
- `selection` — user clicked a `data-appbana-*` element (Stage 6 primarily, wired up but not user-facing in Stage 2)
- `error` — runtime encountered an error

### 2.5 — `resolveAppContext()`

Path-based today (`/run/:tenant/:app`), hostname-ready for Stage 5. Implemented in `app-bana-shared/src/app-context.ts` and consumed here.

### 2.6 — Repoint studio iframe

- Studio iframe URL changes from `http://localhost:5173/run/...` → `http://localhost:5175/run/...`
- Add `scripts/start-runtime.bat` / `.sh`
- Update `scripts/start-everything.bat` / `.sh`

### 2.7 — E2E

Extend studio spec to also verify against runtime on 5175 (parallel assertion so we don't lose old-UI coverage yet).

### Exit criteria — Stage 2

- Deployed apps served entirely by `app-bana-runtime` (no `app-bana-ui` code involved in serving `/run/*`)
- Studio previews via new runtime
- Tenant branding visible on runtime's login screen

---

## Stage 3 — Studio v1.1 enhancements

All additive to `app-bana-studio/`. Not blocking any other stage.

### 3.1 — Data drawer (read-only + basic add)

- Slide-in from right of preview
- Entity list with row counts
- Table view of first N rows per entity (paged, sortable)
- **"Add row"** button — inline form generated from entity schema
- **"Ask AI to seed"** button — inserts prompt into chat

### 3.2 — Session picker upgrade

- Search across all sessions
- Filter by app
- Rename, delete sessions

### 3.3 — Image paste in chat composer

- Attach + preview thumbnail
- Sent as base64 in chat payload
- Agent may ignore initially (backend vision handling is separate Phase 2 work)

### Exit criteria — Stage 3

Studio matches the full desired v1 feature list (see [Locked decisions](#locked-decisions) row for "Data drawer, session picker, image paste").

---

## Stage 4 — Retire `app-bana-ui/`

Repo cleanup. Blocked by Stages 2 & 3 **and** by Sprint 1 of the [Runtime UX Overhaul Plan](./RUNTIME_UX_OVERHAUL_PLAN.md). Deleting the old UI is only safe once the new runtime clears the "client-ready" bar defined there.

- Verify studio + runtime cover every user-facing capability that matters
- Remove `app-bana-ui/` from `pnpm-workspace.yaml`
- `git rm -r app-bana-ui/`
- Update `scripts/start-everything.*` — remove ui launch
- Update backend static file serving (currently serves [`app-bana-service/src/main/resources/ui/dist/`](../../app-bana-service/src/main/resources/ui/dist)) to point to runtime dist
- Delete obsolete e2e tests referencing shadow-DOM `AuthGuard`
- Update [`.github/copilot-instructions.md`](../../.github/copilot-instructions.md) — full rewrite of Section 2 (Monorepo Structure) and Section 3 (How to Start)

### Exit criteria — Stage 4

- Repo has no LitElement code left
- All frontend is React + workspace-managed

---

## Stage 5 — Production Deploy

**Rescoped 2026-07-26** from "subdomain deploy (ops-heavy, tiny code footprint)" to **"Production Deploy"** after a backend readiness audit. The original scope was correct but incomplete: to ship into production we also need containerization, externalized state, secrets management, and observability — none of which A/B/C/D absorbs. Total: ~50 hr.

Five sub-tasks. 5.1 is the original subdomain scope; 5.2–5.5 are the backend-readiness items folded in.

### 5.1 — Subdomain deploy (original scope, ~5 hr)

Ops-heavy, tiny code footprint.

- DNS: wildcard `*.apps.appbana.com` → reverse proxy
- Reverse proxy config (Caddy / nginx) — route by Host to runtime
- Backend `resolveAppContext` reads `Host` header path
- HTTPS certs via wildcard cert or Let's Encrypt DNS-01
- CORS lock-down per subdomain
- Runtime `resolveAppContext()` swaps its resolution strategy — one config change, no code rewrite

### 5.2 — Containerization (~15 hr)

- `Dockerfile` for `app-bana-service` (multi-stage: Maven build → slim JRE 21 runtime image; final image `< 250 MB`)
- `Dockerfile` for `ai-builder` (same pattern)
- `docker-compose.yml` at repo root for local dev: Postgres + Qdrant + Redis + backend + ai-builder + studio + runtime, single `docker compose up`
- Deployment manifests for one target — **Azure Container Apps** (Bicep) is the default; K8s manifests + Helm chart optional for on-prem customers
- Graceful shutdown hook: drain in-flight requests, close HikariCP pool, flush audit log buffer
- Health/readiness probes wired to `/health` (see 5.5)

### 5.3 — Secrets & config externalization (~6 hr)

- Every setting currently in `config.json` becomes readable from environment variables (12-factor). `config.json` remains as dev-mode default only.
- New `SecretsProvider` interface with three implementations:
  - `EnvVarSecretsProvider` (default; reads `APPBANA_*` env vars)
  - `AzureKeyVaultSecretsProvider` (uses `com.azure:azure-security-keyvault-secrets`)
  - `AwsSecretsManagerProvider` (uses `software.amazon.awssdk:secretsmanager`)
- Boot-time schema validation — if any required secret is missing, boot fails fast with a clear error message (no partial-start).
- **Compliance:** `openaiApiKey`, JWT signing key, DB password, SMTP password all move out of `config.json` in prod.

### 5.4 — Redis externalized state (~15 hr)

Blocks horizontal scale — sessions and rate-limit counters currently live in `ConcurrentHashMap` and die on restart.

- Add `redis.clients:jedis` dependency.
- New `SessionStore` interface. `InMemorySessionStore` remains as dev default. New `RedisSessionStore` — key `session:{sessionId}` → JSON with 24 h TTL.
- New `RateLimitStore` interface. `RedisRateLimitStore` uses `INCR` + `EXPIRE` (atomic per-window counter).
- Optional `RedisCache` for widget queries (D2 currently uses in-process cache; Redis is a Phase E upgrade if D2 hits multi-pod scale).
- Config flag `state.backend` = `memory` (default) or `redis`. Redis URL from `SecretsProvider`.

### 5.5 — Observability (~10 hr)

- Swap `slf4j-simple` for `logback-classic` with **JSON-formatted structured logs** (`net.logstash.logback:logstash-logback-encoder`).
- Every request gets a `X-Correlation-Id` (generated if absent, propagated on cross-service calls to ai-builder).
- Add `io.micrometer:micrometer-registry-prometheus`. Expose `/metrics` on a separate port (9090) — HTTP request rate, JDBC pool stats, HikariCP metrics, custom counters (agent tool executions, LLM tokens, notifications sent).
- `/health` becomes a **deep** health check: DB connect + Qdrant ping + configured LLM provider ping. Returns per-dep status. Fails fast on any critical dep down.
- OpenTelemetry: pull in `io.opentelemetry:opentelemetry-api` + `-sdk` + `-exporter-otlp` as no-op stubs (real backend wiring is dep-config only). Both services emit spans for HTTP + JDBC + outbound LLM calls once an OTLP endpoint is configured.

### Exit criteria — Stage 5

- A deployed app is reachable at `spice-shop.tenant42.apps.appbana.com` with tenant branding, over HTTPS.
- Both services run as containers, brought up in ~30 s via `docker compose up` locally, deployed to Azure Container Apps in prod.
- Two backend pods behind a load balancer share sessions and rate-limit counters (login on pod A, request routed to pod B still authenticated).
- No secret appears in any committed file. Every secret is env-var-injected or Key Vault-resolved at boot.
- `/health` returns a JSON dep status matrix; `/metrics` exposes Prometheus-format counters.
- On-call engineer can pull the last 15 min of logs for a given `X-Correlation-Id` from Azure Log Analytics (or equivalent) and reconstruct a request path across studio → ai-builder → core → Postgres.

---

## Stage 6 — Select-and-instruct UX

The visionary feature. Users click / lasso an element in the live preview and attach a natural-language instruction to that specific element.

### 6.1 — Runtime overlay

New component in `app-bana-runtime/` that captures pointer events when studio sets `mode: 'inspect'`:

- Click → send `selection` postMessage with `{pageId, nodeId, entityName?, fieldName?, bbox, screenshot?}`
- Lasso (drag) → same but multiple nodes

### 6.2 — Studio composer accepts selection chips

- Chips render in composer's attachments row (same row used for image paste)
- ✕ to remove
- Click to focus preview on that element

### 6.3 — Chat payload populated

Populate `context.selections` field (already reserved in Stage 1 payload shape):

```json
{
  "message": "make this column sortable",
  "context": {
    "selections": [
      { "pageId": "customers", "nodeId": "node-8f2a", "entity": "Customer", "field": "email" }
    ]
  }
}
```

### 6.4 — ai-builder agent reads selections

Update [`AiAgent.buildAgentPrompt()`](../../ai-builder/src/main/java/com/appbana/ai/agent/AiAgent.java) to inject:

> The user has selected: **Customers** page → **email** column of `Customer` entity.

Agent now knows to call `batch_update_entities` on that exact field.

### 6.5 — Undo / history drawer

Becomes essential once fine-grained edits are common. New drawer in studio:
- Timeline of agent actions
- **Undo last** button (agent-reversed, not a raw DB rollback)

### Exit criteria — Stage 6

User can circle a table column, type "make this sortable and hide on mobile", and the agent modifies exactly that node.

---

## Cross-cutting concerns

### Security

- JWT never in URL / URL hash (postMessage handshake only)
- Runtime login endpoint is the SAME `/api/auth/login` as studio → same JWT works for both
- `GET /api/tenants/{id}/branding` is public — only exposes display fields, no secrets
- Post-Stage 5, subdomain isolation adds cookie / storage boundaries

### Backwards compatibility

- Old sync `/api/ai/chat/agent` endpoint is preserved through all stages
- Old [`app-bana-ui/`](../../app-bana-ui) keeps running through Stages 1–3
- All backend contracts are **additive** — no removed or changed signatures

### Observability

- Every SSE event logged server-side with `sessionId` + `messageIndex`
- Studio logs postMessage traffic in dev mode (behind a `?debug=1` flag)

### Testing

- Playwright smoke test in Stage 1 (`ai-builder-chat.studio.spec.ts`)
- Stage 2 extends smoke test to runtime
- Unit tests colocated per package (Vitest for React packages, JUnit for Java)

---

## Open verifications

To be resolved during Stage 0, non-blocking on other stages:

1. **Streaming shape confirmation** — read [`AiAgent`](../../ai-builder/src/main/java/com/appbana/ai/agent/AiAgent.java) to see how tool calls surface today internally; SSE event schema may need one small revision
2. **`tenants` table existence** — decides "add columns" vs "create table" for the Liquibase changeset
3. **`ComponentNode.id` stability** — across `generate_page` re-runs (see [Stage 0.1](#01--verify-componentnodeid-stability))

---

## File-level change list

### Documentation (this task)

**NEW**
- `docs/planning/AI_NATIVE_UI_REBUILD_PLAN.md` — this document

**UPDATE**
- `docs/ACTIVE_TASKS.md` — add "🚧 In Progress" section pointing here
- `docs/session_summary.md` — append new section (keep DialogueManager history)
- `docs/README.md` — add link in Planning section
- `docs/planning/03-ROADMAP.md` — align roadmap phases with Stages 0–6
- `.github/copilot-instructions.md` — pointer note in Section 12 (Active Work)

### Backend — Stage 0

**NEW**
- `ai-builder/src/main/java/com/appbana/ai/api/AgentStreamController.java`
- `app-bana-service/src/main/java/com/appbana/server/routes/TenantBrandingRoutes.java`
- `app-bana-service/src/main/java/com/appbana/server/routes/AppContextRoutes.java`
- Liquibase changeset in `app-bana-service/src/main/resources/db/changelog/`

**MODIFY**
- `ai-builder/src/main/java/com/appbana/ai/agent/AiAgent.java` (callback plumbing for SSE)

### Frontend — Stage 1

**NEW**
- `pnpm-workspace.yaml` at repo root
- `app-bana-shared/` (full package: `package.json`, `tsconfig.json`, `src/*`)
- `app-bana-studio/` (full package: Vite config, Tailwind config, shadcn setup, `src/*`)
- `scripts/start-studio.bat` / `.sh`
- `e2e/tests/ai-builder-chat.studio.spec.ts`

**MODIFY**
- Root `package.json` — declare workspaces + scripts
- `app-bana-ui/src/runtime/renderer/Renderer.ts` — add `data-appbana-*` attributes
- `app-bana-ui/src/runtime/renderer/StudioTableLive.ts` — add `data-appbana-*` attributes
- `app-bana-ui/src/runtime/shell/AppRuntimeShell.ts` — accept postMessage token
- `scripts/start-everything.bat` / `.sh` — launch studio

### Frontend — Stage 2

**NEW**
- `app-bana-runtime/` (full package)
- `scripts/start-runtime.bat` / `.sh`

**MODIFY**
- Studio's preview iframe URL → 5175
- `scripts/start-everything.bat` / `.sh` — launch runtime

### Frontend — Stages 3–4

Additive changes inside `app-bana-studio/`; then `git rm -r app-bana-ui/` in Stage 4.

---

*Last updated: 2026-07-25. See also: [`docs/ACTIVE_TASKS.md`](../ACTIVE_TASKS.md), [`docs/session_summary.md`](../session_summary.md), [`.github/copilot-instructions.md`](../../.github/copilot-instructions.md).*
