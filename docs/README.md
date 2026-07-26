# AppBana Documentation

Every document under `docs/` is **current** and describes the system as it ships today. Anything that was stale or superseded has been removed. If you find something that doesn't match the code, that's a bug — please open an issue or fix it directly.

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
| Understand the AI Builder microservice | [`features/ai-builder-service.md`](./features/ai-builder-service.md) |
| Understand security (auth / CSRF / RBAC / rate limit) | [`features/SECURITY_FEATURES.md`](./features/SECURITY_FEATURES.md) + [`specs/AUTH.md`](./specs/AUTH.md) |
| Understand the datasource adapter model | [`architecture/datasource-adapters.md`](./architecture/datasource-adapters.md) |
| Understand the AI knowledge base + builder-database rules | [`features/KNOWLEDGE_BASE.md`](./features/KNOWLEDGE_BASE.md) + [`features/builder-database.md`](./features/builder-database.md) |

---

## Current state (2026-07-26)

**Shipped**
- Stages 0–4 of the AI-Native UI Rebuild (see [`planning/AI_NATIVE_UI_REBUILD_PLAN.md`](./planning/AI_NATIVE_UI_REBUILD_PLAN.md)) — the legacy `app-bana-ui/` is retired; Studio (5174) + Runtime (5175) + shared package are the frontend.
- Runtime UX Sprint 1 (see [`planning/RUNTIME_UX_OVERHAUL_PLAN.md`](./planning/RUNTIME_UX_OVERHAUL_PLAN.md)) — 8/10 tasks done.

**Forward plan** (single source: [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md))
- **Phase A** — Runtime UX Sprint 2 (~10 hr) — customer-demo-ready UI.
- **Phase B** — Complex UI Epic (~29 hr) — wizards, conditional fields, upload, master-detail, list views.
- **Phase C** — Maker-Checker Epic (~30 hr) — approvals with state machine, roles, audit trail.
- **Phase D** — Enterprise Capabilities Epic (~125 hr) — SSO, dashboards, notifications, enterprise shell.
- **Stage 5** — Subdomain deploy (parallel ops track).

---

## Folder layout

```
docs/
├── README.md                       ← this file (navigation)
├── ACTIVE_TASKS.md                 ← status of every workstream
├── session_summary.md              ← what shipped this session
├── planning/                       ← the five active epic plans (execution order A → B → C → D)
│   ├── AI_NATIVE_UI_REBUILD_PLAN.md
│   ├── RUNTIME_UX_OVERHAUL_PLAN.md
│   ├── COMPLEX_UI_PLAN.md
│   ├── MAKER_CHECKER_PLAN.md
│   └── ENTERPRISE_CAPABILITIES_PLAN.md
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
