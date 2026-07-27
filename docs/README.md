# AppBana Documentation

Every document under `docs/` is **current** and describes the system as it ships today. Anything that was stale or superseded has been removed. If you find something that doesn't match the code, that's a bug — please open an issue or fix it directly.

---

## What AppBana is (and where we're going)

**AppBana is a metadata-driven, AI-powered application builder.** A non-technical user describes what they want in natural language and the AI agent autonomously:

1. Defines the data model
2. Creates PostgreSQL tables (via `SchemaManager`)
3. Generates REST CRUD APIs (auto-derived from schema)
4. Renders UI pages (via metadata → React runtime)

A single schema definition drives the entire stack. See [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) §5 for the metadata-driven flow.

**The 12-month product goal** is a differentiated enterprise-ready SaaS: the first tool where a business owner can chat-build a real regulated-industry workflow (with approvals, audit, dashboards, SSO) in one afternoon. The forward plan below (A → B → C → D → Stage 5) is the path to first-enterprise-customer-live. Everything post-launch lives in Phase E.

---

## Where should I start?

| I want to... | Read this |
|---|---|
| Understand the whole system in 10 minutes | [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) |
| See what's shipped and what's next | [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md) |
| See the current session's work | [`session_summary.md`](./session_summary.md) |
| Understand the AI-Native rebuild plan | [`planning/AI_NATIVE_UI_REBUILD_PLAN.md`](./planning/AI_NATIVE_UI_REBUILD_PLAN.md) |
| Work on Runtime UX (Phase A) | [`planning/RUNTIME_UX_OVERHAUL_PLAN.md`](./planning/RUNTIME_UX_OVERHAUL_PLAN.md) |
| Build the Complex UI epic (Phase B) | [`planning/COMPLEX_UI_PLAN.md`](./planning/COMPLEX_UI_PLAN.md) |
| Build the Maker-Checker epic (Phase C) | [`planning/MAKER_CHECKER_PLAN.md`](./planning/MAKER_CHECKER_PLAN.md) |
| Build the Enterprise Capabilities epic (Phase D) | [`planning/ENTERPRISE_CAPABILITIES_PLAN.md`](./planning/ENTERPRISE_CAPABILITIES_PLAN.md) |
| See the residual backend backlog (Phase E) | [`planning/BACKEND_BACKLOG.md`](./planning/BACKEND_BACKLOG.md) |
| Understand the AI Builder microservice | [`features/ai-builder-service.md`](./features/ai-builder-service.md) |
| Understand security (auth / CSRF / RBAC / rate limit) | [`features/SECURITY_FEATURES.md`](./features/SECURITY_FEATURES.md) + [`specs/AUTH.md`](./specs/AUTH.md) |
| Understand the datasource adapter model | [`architecture/datasource-adapters.md`](./architecture/datasource-adapters.md) |
| Understand the AI knowledge base + builder-database rules | [`features/KNOWLEDGE_BASE.md`](./features/KNOWLEDGE_BASE.md) + [`features/builder-database.md`](./features/builder-database.md) |
| Review a completion report as Tech Lead / Architect | [`../.github/prompts/code-review.prompt.md`](../.github/prompts/code-review.prompt.md) |
| Bring docs back in sync after a unit of work | [`../.github/prompts/update-docs.prompt.md`](../.github/prompts/update-docs.prompt.md) |

---

## Current state (2026-07-28)

**Shipped**
- Stages 0–4 of the AI-Native UI Rebuild — legacy `app-bana-ui/` retired; Studio (5174) + Runtime (5175) + shared package are the frontend.
- AI Schema Quality Stack — SchemaEnricher, Structured Generation, Dynamic Prompt Builder, RAG Domain Examples.
- Intelligent Dialogue (Story 3.1) — `DialogueManager` state machine.
- **Phase A — Quality Sprint** (Runtime UX Sprint 2) — date picker, sidebar redesign, empty states, loading skeletons, inline validation, status pills, WCAG AA, responsive breakpoints.
- **Phase A2 — Runtime Foundations** (Sprint 3) — R/U/D primitives, reference combobox, unified Button, tenant branding CSS vars, field validation mapping, e2e CRUD specs.
- **Phase B — Complex UI Epic** (B1–B5) — wizards, conditional fields, file upload, master-detail, list views with filter/group/saved views.
- **Phase B.H — Hardening Sprint** (H1–H8) — file tenant isolation, auto-inject parentId + ChildTable, FilterBar + SavedViewsBar in StudioTableLive, real FK constraints, hidden-field validation strip, SQL GROUP BY, Playwright hardening suite, docs refresh.
- **Phase C1 — Approval DB migration + role model** and **Phase C2 — Approval state machine, permission guard, revisions, audit trail**. All C2 exit criteria met.

**Build health** lives in [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md#-build-health-single-source--do-not-duplicate-these-counts-elsewhere) — it is the single source for test counts and CI status.

**Forward plan** (single source: [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md))
- **Phase C** — Maker-Checker Epic (~30 hr) — C1 and C2 complete; **C3 Runtime approval UI is next**, then C4 (AI Builder) and C5 (notifications).
- **Phase D** — Enterprise Capabilities Epic (~125 hr) — SSO, dashboards, notifications, enterprise shell.
- **Stage 5** — Production Deploy (~50 hr) — subdomain hosting + containerization + Redis + secrets + observability.
- **Phase E** — Integration + Advanced Backlog (~87 hr, post-launch, customer-driven).

---

## Folder layout

```
docs/
├── README.md                       ← this file (navigation)
├── ACTIVE_TASKS.md                 ← status of every workstream
├── session_summary.md              ← what shipped this session
├── planning/                       ← the six active epic plans (execution order A → B → C → D → Stage 5 → E)
│   ├── AI_NATIVE_UI_REBUILD_PLAN.md    ← master rebuild + Stage 5 Production Deploy
│   ├── RUNTIME_UX_OVERHAUL_PLAN.md
│   ├── COMPLEX_UI_PLAN.md
│   ├── MAKER_CHECKER_PLAN.md
│   ├── ENTERPRISE_CAPABILITIES_PLAN.md
│   └── BACKEND_BACKLOG.md              ← Phase E residual backlog
├── architecture/
│   └── datasource-adapters.md      ← universal datasource adapter model
├── features/
│   ├── ai-builder-service.md       ← the ai-builder microservice
│   ├── SECURITY_FEATURES.md        ← auth, CSRF, RBAC, rate limit, FLS
│   ├── KNOWLEDGE_BASE.md           ← agent knowledge rules
│   └── builder-database.md         ← builder-database/*.json reference
└── specs/
    └── AUTH.md                     ← auth protocol spec
```

---

## The three consistency rules

1. **[`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md) is the single source for status.** Individual plan docs describe scope + design; status lives in ACTIVE_TASKS.
2. **`docs/README.md` (this file) is the single source for navigation.** New docs must be added here or they don't exist.
3. **[`.github/copilot-instructions.md`](../.github/copilot-instructions.md) sections 2, 3 and 5 are the single source for "how the system runs today"** (monorepo layout, how to start, metadata-driven flow). Deep dives live in `features/` and `architecture/`.

If any doc drifts from these rules, fix by editing this file + `ACTIVE_TASKS.md` first, then propagate down. Never the reverse.
