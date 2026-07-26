# Session Summary — 2026-07-26

**Working branch:** `feature/ui-rebuild`

---

## What shipped this session

### Commit `723a193` — fix(studio): show login screen when session expires
`AuthGate.tsx` now listens for `appbana:auth:expired` and re-shows the login screen instead of freezing the studio on an empty shell.

### Commit `6edd19a` — chore(stage-4): retire `app-bana-ui/`
- Deleted the LitElement studio + runtime (Maven module, pnpm workspace entry, `pom.xml` dependency, `scripts/start-ui.*`, obsolete e2e spec).
- Rewrote [`scripts/start-everything.bat`](../scripts/start-everything.bat) + [`scripts/start-everything.sh`](../scripts/start-everything.sh) to a 4-stage flow (AI Builder → Backend → Studio → Runtime).
- Rewrote [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) §2, §3, §5 to reflect the four-service topology.
- Verified: `mvn compile`, `mvn -pl app-bana-service package -DskipTests`, and `tsc --noEmit` across shared + studio + runtime all pass.

### Commit `eca9f3a` — docs(plan): add Complex UI and Maker-Checker epic plans
- [`planning/COMPLEX_UI_PLAN.md`](./planning/COMPLEX_UI_PLAN.md) — Phase B (5 sub-phases, ~29 hr).
- [`planning/MAKER_CHECKER_PLAN.md`](./planning/MAKER_CHECKER_PLAN.md) — Phase C (5 sub-phases, ~30 hr, C5 optional for v1).
- Added Phase A/B/C forward-plan table to top of [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md).

### Commit `e6efc47` — docs: consolidate documentation, add currency banners
Intermediate pass that labelled every doc ✅ / ⚠️ / 📚 and cross-linked the active plans. Superseded by the next commit which removed all stale docs entirely.

### Pending commit — docs: purge all stale/historical documentation
- **Deleted 19 stale files** (list below). Nothing under `docs/` is stale anymore.
- Fixed the last stale port / folder / framework references in the 3 kept reference docs.
- Rewrote [`README.md`](./README.md) — no more currency labels needed; every remaining doc is current.

**Files deleted this pass:**

| Folder | File | Why removed |
|---|---|---|
| architecture/ | `01-ARCHITECTURE.md` | Canvas-era architecture; current model lives in copilot-instructions §5 |
| architecture/ | `ARCHITECTURE_VISUAL_SUMMARY.md` | Canvas-era diagrams |
| architecture/ | `ENTITY_FORM_BINDING_ARCHITECTURE.md` | Canvas-era LitElement forms |
| features/ | `ai-agent.md` | Redundant with copilot-instructions §6/§7 + stale ports |
| features/ | `multi-tenant-design.md` | Model covered in copilot-instructions §5 + heavy stale content |
| features/ | `page-templates.md` | Canvas-era template picker (replaced by `GeneratePageTool`) |
| guides/ | `02-DEVELOPMENT_GUIDE.md` | Redundant with copilot-instructions §3 |
| guides/ | `04-USER_MANUAL.md` | Canvas-era drag-drop manual |
| guides/ | `SESSION_RESUME_GUIDE.md` | Referenced retired `app-bana-ui/` file paths |
| guides/ | `api-client.md` | Described a retired multi-file api client |
| guides/ | `API_VERIFICATION.md` | Historical verification report |
| guides/ | `automation-testing.md` | Historical test artifact folder |
| guides/ | `JAVA21_QUICK_REFERENCE.md` | Historical "what we implemented" progress note |
| planning/ | `03-ROADMAP.md` | Canvas-era roadmap |
| planning/ | `IMPLEMENTATION_STORIES.md` | Canvas-era multi-tenant migration backlog |
| planning/ | `AI_AGENT_IMPLEMENTATION_PLAN.md` | Jan 2026 plan referencing LitElement stack |
| planning/ | `AI_AGENT_STORIES.md` | Same |
| planning/ | `AI_SCHEMA_QUALITY_PLAN.md` | April 2026 plan superseded by current 4-plan structure |
| specs/ | `WORKFLOW.md` | Superseded by [`planning/MAKER_CHECKER_PLAN.md`](./planning/MAKER_CHECKER_PLAN.md) |

The empty `docs/guides/` folder was also removed.

**Surgical fixes in kept files:**
- `features/ai-builder-service.md` — banner stripped, diagram now reads `Studio (5174) → AI Builder (8081) → AppBana Service (8080)`.
- `features/SECURITY_FEATURES.md` — banner stripped, frontend section rewritten to point at `app-bana-shared/src/api-client.ts` + `app-bana-studio/src/features/auth/AuthGate.tsx`.
- `specs/AUTH.md` — stale "Frontend Components" section (LitElement + MobX example) replaced with a pointer to the current React `AuthGate.tsx`. Implementation checklist updated too.

---

## Current forward plan

```
Phase A — Runtime UX Sprint 2  (~10 hr) — customer-demo-ready UI
     ↓
Phase B — Complex UI Epic       (~29 hr) — wizards, upload, master-detail, list views
     ↓
Phase C — Maker-Checker Epic    (~30 hr, C5 optional) — approvals + audit
     ↓
🚀 First customer live   (Stage 5 subdomain deploy runs in parallel)
```

Single source: [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md).

---

## Next session goal

**Start Phase A, Task 2.1** — replace the runtime's native `<input type="date">` with `react-day-picker` in a popover. Format via `date-fns`, emit ISO 8601 on submit. See [`planning/RUNTIME_UX_OVERHAUL_PLAN.md`](./planning/RUNTIME_UX_OVERHAUL_PLAN.md) Sprint 2 §2.1.

---

## Consistency rules (unchanged)

1. [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md) — single source for status.
2. [`README.md`](./README.md) — single source for navigation.
3. [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) §2 + §3 + §5 — single source for "how the system runs today".
