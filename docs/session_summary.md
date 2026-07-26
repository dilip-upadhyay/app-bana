# Session Summary — 2026-07-26

**Working branch:** `feature/ui-rebuild`

---

## What shipped this session

Recent commits (freshest last):
- `723a193` — fix(studio): show login screen when session expires
- `6edd19a` — chore(stage-4): retire `app-bana-ui/`
- `eca9f3a` — docs(plan): add Complex UI + Maker-Checker epic plans (B & C)
- `e6efc47` — docs: consolidate documentation
- `5455c9a` — docs: purge all stale documentation
- `c3793ff` — docs(plan): add Enterprise Capabilities epic (Phase D)
- `3c0752d` — docs(plan): re-order forward plan to A → B → C → D
- `0ad115f` — docs(plan): backend audit → rescope Stage 5 as Production Deploy, add Phase E backlog

### Latest commit — docs(plan): backend audit + Stage 5 rescope + Phase E backlog (`0ad115f`)

**Trigger:** Product owner asked frank questions about backend readiness — file upload, external API integrations, S3/Azure Blob, containerization, cloud deploy. The forward plan up to that point focused only on frontend + workflow features, leaving backend production-readiness undocumented.

**Approach:** Rather than invent a monolithic "Phase E" backend plan and repeat work already scoped inside A/B/C/D, ran a per-phase backend audit against the actual code:

| Audited capability | Already in phase? |
|---|---|
| File upload + `FileStorageAdapter` + local disk | ✅ B3 (`appbana_files` table, `POST /api/files/upload`, `GET /api/files/{id}`, `SchemaManager` `type:"file"`) |
| SMTP email adapter | ✅ C5.4 (`NotificationChannel` + `EmailChannel`) |
| SSE broadcaster + `NotificationSseHub` | ✅ D3 (already file-mapped) |
| Widget query cache | ✅ D2 (60 s in-process cache) |
| Containerization, Redis, secrets, observability | ❌ Not in any phase — needed to *deploy* AppBana |
| Cloud storage adapters (S3, Azure Blob) | ❌ Deferred by B3.2 "plug in later" |
| Outbound integration framework | ❌ Nowhere. `WorkflowEngine` has a stub |
| Async job queue, CSV import, FTS, GDPR, WebSocket, API versioning | ❌ Nowhere. No current driver |

**Two structural changes made:**

1. **Stage 5 rescoped** from ~5 hr ("subdomain deploy — ops-heavy, tiny code footprint") to ~50 hr ("**Production Deploy**"). Now includes 5 sub-tasks:
    - **5.1** — original subdomain hosting scope (~5 hr)
    - **5.2** — Containerization: Dockerfile per service + `docker-compose.yml` + Azure Container Apps Bicep or K8s manifests (~15 hr)
    - **5.3** — Secrets externalization: 12-factor env vars + `SecretsProvider` interface (Env / Azure Key Vault / AWS Secrets Manager) (~6 hr)
    - **5.4** — Redis-backed sessions + rate limit + optional distributed cache (~15 hr)
    - **5.5** — JSON structured logs + correlation IDs + Micrometer `/metrics` + deep `/health` + OpenTelemetry stubs (~10 hr)

2. **New doc — [`planning/BACKEND_BACKLOG.md`](./planning/BACKEND_BACKLOG.md) (Phase E)** — lean backlog with 8 items (E1..E8) ordered by likely customer demand, ~87 hr total, no committed order. Nothing pulled into active plan until a customer or strategic driver appears. Explicit "not in this backlog" section lists what already lives elsewhere so future readers don't re-add duplicates.

**Files modified for these changes:**
- [`planning/AI_NATIVE_UI_REBUILD_PLAN.md`](./planning/AI_NATIVE_UI_REBUILD_PLAN.md) — Stage 5 section rewritten with 5 sub-tasks; anchor renamed `stage-5--subdomain-deployment` → `stage-5--production-deploy`; ToC + status line updated.
- [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md) — forward-plan table's Stage 5 row rewritten to describe the 5 sub-tasks; new Phase E row added; execution-order line extended with Stage 5 sub-task deps; total effort revised ~194 hr → ~244 hr; added backend-audit rationale paragraph.
- [`README.md`](./README.md) — forward-plan bullets, persona-table, and folder-layout tree all include Stage 5 (rescoped) + Phase E.
- [`planning/RUNTIME_UX_OVERHAUL_PLAN.md`](./planning/RUNTIME_UX_OVERHAUL_PLAN.md) — cross-plan reference updated to reflect Stage 5 as post-D not in-parallel-with-B/C.
- **New:** [`planning/BACKEND_BACKLOG.md`](./planning/BACKEND_BACKLOG.md) — Phase E backlog.

---

## Current forward plan (final)

```
Phase A — Runtime UX Sprint 2        (~10 hr)   — customer-demo-ready UI
     ↓
Phase B — Complex UI Epic            (~29 hr)   — wizards · conditional fields · upload · master-detail · list views
                                                  (file upload backend already scoped in B3 — local disk only)
     ↓
Phase C — Maker-Checker Epic         (~30 hr)   — approvals + audit + in-app notifications + SMTP email adapter (C5.4)
     ↓
🚀 Demo-able differentiated product  (~69 hr total, ~2 weeks focused)
     ↓
Phase D — Enterprise Capabilities    (~125 hr)  — SSO · dashboards + 60 s in-process cache · SSE-pushed notifications · enterprise shell
     ↓
Stage 5 — Production Deploy          (~50 hr)   — subdomain hosting + containerize + secrets + Redis + observability
                                                  (5.2/5.3 pullable-forward if we need ACA earlier for a customer demo)
     ↓
🚀 First enterprise customer live    (~244 hr total)
     ↓
Phase E — Integration + Advanced Backlog (~87 hr, post-launch, customer-driven)
     ↓ cloud storage adapters (E1) · outbound integration framework (E2) · async job queue (E3)
     ↓ CSV/Excel (E4) · Postgres FTS (E5) · GDPR (E6) · WebSocket (E7) · API versioning (E8)
```

**Single source of truth:** [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md).

---

## Next session goal

**Start Phase A, Task 2.1** — replace the runtime's native `<input type="date">` with `react-day-picker` in a popover. Format via `date-fns`, emit ISO 8601 on submit. See [`planning/RUNTIME_UX_OVERHAUL_PLAN.md`](./planning/RUNTIME_UX_OVERHAUL_PLAN.md) Sprint 2 §2.1.

---

## Consistency rules (unchanged)

1. [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md) — single source for status.
2. [`README.md`](./README.md) — single source for navigation.
3. [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) §2 + §3 + §5 — single source for "how the system runs today".
