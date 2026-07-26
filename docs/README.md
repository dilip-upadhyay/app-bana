# AppBana Documentation

**Last reviewed for consistency:** 2026-07-26

Centralized documentation for the AppBana platform. Every markdown file for humans lives under this folder — nothing else in the repo contains prose documentation.

This README is the **single navigation source of truth**. Every other document either (a) is listed here with its current status, or (b) does not exist. If you find a `.md` file not indexed below, treat it as a bug and add it.

---

## 📍 Where should I start?

| I am… | Read this first |
|---|---|
| **A business leader** — want to know launch readiness | [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md) — the forward-plan table at the top |
| **A new developer** — want to run the stack | [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) §3 (How to Start) |
| **A developer resuming a session** | [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md) + [`session_summary.md`](./session_summary.md) |
| **An architect** — want the current system model | [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) §2 + §5 |
| **A contributor to the AI Agent** | [`features/ai-agent.md`](./features/ai-agent.md) + [`features/ai-builder-service.md`](./features/ai-builder-service.md) |
| **A contributor to the runtime UI** | [`planning/RUNTIME_UX_OVERHAUL_PLAN.md`](./planning/RUNTIME_UX_OVERHAUL_PLAN.md) + [`planning/COMPLEX_UI_PLAN.md`](./planning/COMPLEX_UI_PLAN.md) |
| **A contributor to approval workflows** | [`planning/MAKER_CHECKER_PLAN.md`](./planning/MAKER_CHECKER_PLAN.md) |
| **Wanting the historical why** | [`planning/AI_NATIVE_UI_REBUILD_PLAN.md`](./planning/AI_NATIVE_UI_REBUILD_PLAN.md) — motivation + all past stages |

---

## 🎯 Current State (2026-07-26)

**Path to first customer** — three phases, plans linked below.

| Phase | Plan | Status |
|---|---|---|
| **A — Quality Sprint** (Runtime UX Sprint 2) | [`RUNTIME_UX_OVERHAUL_PLAN.md`](./planning/RUNTIME_UX_OVERHAUL_PLAN.md#sprint-2--make-it-feel-professional) | ⏳ Ready to execute |
| **B — Complex UI Epic** | [`COMPLEX_UI_PLAN.md`](./planning/COMPLEX_UI_PLAN.md) | 📝 Spec approved 2026-07-26 |
| **C — Maker-Checker Epic** | [`MAKER_CHECKER_PLAN.md`](./planning/MAKER_CHECKER_PLAN.md) | 📝 Spec approved 2026-07-26 |
| **Stage 5** — Subdomain deploy (parallel ops) | [`AI_NATIVE_UI_REBUILD_PLAN.md`](./planning/AI_NATIVE_UI_REBUILD_PLAN.md#stage-5--subdomain-deployment) | ⏳ Ops-heavy, tiny code |

For the definitive live roadmap, see [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md). Everything else in this repo is either historical context or reference material for individual subsystems.

---

## 📁 Folder Layout

| Folder | Purpose |
|--------|---------|
| [`planning/`](./planning/) | Forward-looking plans (the four active plans + historical ones) |
| [`architecture/`](./architecture/) | System design references (mostly historical; current architecture lives in `.github/copilot-instructions.md`) |
| [`features/`](./features/) | Deep dives into shipped subsystems |
| [`guides/`](./guides/) | How-to guides for developers, testers, users |
| [`specs/`](./specs/) | Feature specifications (auth, workflow) |

Top-level files:

- [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md) — **live** forward-plan table + status tracker (**canonical status source**)
- [`session_summary.md`](./session_summary.md) — most recent working session notes
- [`README.md`](./README.md) — this file

---

## 📝 Planning (four active plans + historical)

### ✅ Active — the path to launch

| Doc | Owner phase | Status |
|---|---|---|
| [`RUNTIME_UX_OVERHAUL_PLAN.md`](./planning/RUNTIME_UX_OVERHAUL_PLAN.md) | Sprint 1 = pre-Stage-4 gate (✅ done). Sprint 2 = **Phase A**. Sprint 3 = post-launch. | Sprint 1 ✅ · Sprint 2 ⏳ · Sprint 3 deferred |
| [`COMPLEX_UI_PLAN.md`](./planning/COMPLEX_UI_PLAN.md) | **Phase B** — wizards, conditional fields, file upload, master-detail, list views | Spec approved · code ⏳ |
| [`MAKER_CHECKER_PLAN.md`](./planning/MAKER_CHECKER_PLAN.md) | **Phase C** — approval workflows, audit trail, roles | Spec approved · code ⏳ |
| [`AI_NATIVE_UI_REBUILD_PLAN.md`](./planning/AI_NATIVE_UI_REBUILD_PLAN.md) | The master rebuild plan — Stages 0-4 shipped; Stages 5-6 remain | Stages 0-4 ✅ · Stage 5 ⏳ ops · Stage 6 deferred |

### 📚 Historical (retained for context)

| Doc | Why kept |
|---|---|
| [`03-ROADMAP.md`](./planning/03-ROADMAP.md) | Q4 2025 canvas-era roadmap. Superseded by the AI-Native rebuild — see banner at the top of that file. |
| [`AI_AGENT_IMPLEMENTATION_PLAN.md`](./planning/AI_AGENT_IMPLEMENTATION_PLAN.md) | Original AI agent build plan. Story 3.1 (DialogueManager) shipped; rest complete. |
| [`AI_AGENT_STORIES.md`](./planning/AI_AGENT_STORIES.md) | User stories for the AI agent. Reference material. |
| [`AI_SCHEMA_QUALITY_PLAN.md`](./planning/AI_SCHEMA_QUALITY_PLAN.md) | 4-phase schema-quality upgrade plan. All 4 phases shipped. Kept for reference on how the SchemaEnricher works. |
| [`IMPLEMENTATION_STORIES.md`](./planning/IMPLEMENTATION_STORIES.md) | Canvas-era story backlog. Superseded by the four active plans above. |

---

## 🏗️ Architecture

> **The single most current architectural reference is [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) §2 (Monorepo Structure), §3 (How to Start), §5 (Metadata-Driven Flow).** The docs below are deeper-dive references from before the AI-Native rebuild; each has a banner noting its currency.

| Doc | Currency | Purpose |
|---|---|---|
| [`architecture/01-ARCHITECTURE.md`](./architecture/01-ARCHITECTURE.md) | 📚 Historical (Oct 2025) | Full canvas-era architecture reference. Core primitives (metadata-driven flow, multi-tenant model, schema-driven CRUD) are still accurate; the UI section is superseded. |
| [`architecture/ARCHITECTURE_VISUAL_SUMMARY.md`](./architecture/ARCHITECTURE_VISUAL_SUMMARY.md) | 📚 Historical | Visual diagrams. Backend + agent diagrams remain valid; UI diagrams superseded. |
| [`architecture/ENTITY_FORM_BINDING_ARCHITECTURE.md`](./architecture/ENTITY_FORM_BINDING_ARCHITECTURE.md) | 📚 Historical | How canvas-era forms bound to entities. Runtime binding today is described inline in `app-bana-runtime/src/runtime/Renderer.tsx`; a new doc will land during Phase B. |
| [`architecture/datasource-adapters.md`](./architecture/datasource-adapters.md) | ✅ Current | Universal datasource adapter system — unaffected by the UI rebuild. |

---

## 🎁 Features (subsystem reference material)

| Doc | Currency | Purpose |
|---|---|---|
| [`features/ai-agent.md`](./features/ai-agent.md) | ✅ Current (agent logic unchanged) — port diagrams superseded | Agent Think/Act/Observe loop, tool system, dialogue state machine |
| [`features/ai-builder-service.md`](./features/ai-builder-service.md) | ✅ Current | The `ai-builder` microservice (port 8081) |
| [`features/builder-database.md`](./features/builder-database.md) | ✅ Current | Knowledge base seeded from `builder-database/*.json` |
| [`features/KNOWLEDGE_BASE.md`](./features/KNOWLEDGE_BASE.md) | ✅ Current | RAG knowledge store (Qdrant) |
| [`features/multi-tenant-design.md`](./features/multi-tenant-design.md) | ✅ Current — physical isolation model unchanged | Multi-tenant table naming + isolation |
| [`features/SECURITY_FEATURES.md`](./features/SECURITY_FEATURES.md) | ⚠️ Partial (file paths refer to retired `app-bana-ui/`) — security concepts still apply, need path refresh | Auth, RBAC, FLS, CSRF, rate limiting |
| [`features/page-templates.md`](./features/page-templates.md) | 📚 Historical (canvas-era templates) — page generation now driven by `GeneratePageTool` in the AI Builder | System page templates |

---

## 🛠 Guides

| Doc | Currency | Purpose |
|---|---|---|
| [`guides/02-DEVELOPMENT_GUIDE.md`](./guides/02-DEVELOPMENT_GUIDE.md) | ⚠️ Partial — build commands + ports superseded by [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) §3 | Legacy dev workflow reference |
| [`guides/04-USER_MANUAL.md`](./guides/04-USER_MANUAL.md) | 📚 Historical (canvas-era Studio manual) | Kept as historical UX reference |
| [`guides/SESSION_RESUME_GUIDE.md`](./guides/SESSION_RESUME_GUIDE.md) | ⚠️ Partial (references retired `app-bana-ui/` file paths) | Session-resume checklist |
| [`guides/JAVA21_QUICK_REFERENCE.md`](./guides/JAVA21_QUICK_REFERENCE.md) | ✅ Current | Java 21 idioms used in the codebase |
| [`guides/API_VERIFICATION.md`](./guides/API_VERIFICATION.md) | ✅ Current | API verification procedures |
| [`guides/api-client.md`](./guides/api-client.md) | ✅ Current — runtime uses `app-bana-shared/src/api-client.ts` (moved from `app-bana-ui/`) | Frontend API client & interceptor system |
| [`guides/automation-testing.md`](./guides/automation-testing.md) | ✅ Current — see also `e2e/README.md` | End-to-end automation testing notes |

---

## 📋 Specs

| Doc | Currency | Purpose |
|---|---|---|
| [`specs/AUTH.md`](./specs/AUTH.md) | ✅ Current | Authentication & RBAC specification |
| [`specs/WORKFLOW.md`](./specs/WORKFLOW.md) | 📚 Historical — replaced by [`planning/MAKER_CHECKER_PLAN.md`](./planning/MAKER_CHECKER_PLAN.md) for the approval-flow use case | Original workflow-engine spec |

---

## 🔗 Documentation currency legend

- ✅ **Current** — accurate to today's codebase. Safe to follow as gospel.
- ⚠️ **Partial** — core concept still valid; specific file paths or ports are stale. Cross-check against the doc named in the row.
- 📚 **Historical** — describes a system state that no longer exists. Retained for context / rationale / audit. Do not follow instructions inside.

Every historical or partial doc has a banner at the top pointing to the current authoritative source.

---

## ✅ How we keep this consistent

Three rules:

1. **`ACTIVE_TASKS.md` is the single source for status.** Plans track their own exit criteria; status labels live in one place.
2. **`docs/README.md` (this file) is the single source for navigation.** New docs must be added here or they don't exist.
3. **[`.github/copilot-instructions.md`](../.github/copilot-instructions.md) §2 + §3 + §5 is the single source for "how the system runs today".** Detailed dives live in `features/` and `architecture/`.

When these rules are broken, fix by editing this README + `ACTIVE_TASKS.md` first, then propagate to individual docs. Never the reverse.
