# Session Summary — 2026-07-26

**Working branch:** `feature/ui-rebuild` (pushed through commit `eca9f3a`, then documentation-consolidation commit on top)

---

## What shipped this session

### Commit `723a193` — fix(studio): show login screen (not empty UI) when session expires
- `AuthGate.tsx` gained a global `appbana:auth:expired` listener; on 401 from any request, session storage clears and the login screen returns with a "your session expired" banner. Previously the studio froze on an empty shell.

### Commit `6edd19a` — chore(stage-4): retire `app-bana-ui/` (LitElement studio + runtime)
- Deleted the entire `app-bana-ui/` folder (Maven module + LitElement source + build output + `pom.xml` dependency).
- Removed from root [`pom.xml`](../pom.xml), [`pnpm-workspace.yaml`](../pnpm-workspace.yaml), [`app-bana-service/pom.xml`](../app-bana-service/pom.xml) dependency, and the `dev:ui` script in root [`package.json`](../package.json).
- Deleted [`scripts/start-ui.bat`](../scripts/start-ui.bat) + [`scripts/start-ui.sh`](../scripts/start-ui.sh). Rewrote [`scripts/start-everything.bat`](../scripts/start-everything.bat) + [`scripts/start-everything.sh`](../scripts/start-everything.sh) to a 4-stage flow (AI Builder → Backend → Studio → Runtime).
- Updated [`e2e/playwright.config.ts`](../e2e/playwright.config.ts) `baseURL` → Studio 5174. Rewrote [`e2e/README.md`](../e2e/README.md). Deleted the obsolete shadow-DOM AuthGuard spec `e2e/tests/ai-builder-chat.spec.ts`.
- Rewrote [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) §2 (Monorepo Structure), §3 (How to Start), and §5 (Metadata-Driven Flow diagram) to the four-service topology.
- Verified: `mvn compile`, `mvn -pl app-bana-service package -DskipTests`, and `tsc --noEmit` across `@appbana/shared` + `app-bana-studio` + `@appbana/runtime` all pass.

### Commit `eca9f3a` — docs(plan): add Complex UI and Maker-Checker epic plans
- Created [`docs/planning/COMPLEX_UI_PLAN.md`](./planning/COMPLEX_UI_PLAN.md) — Phase B, 5 sub-phases (wizards, conditional fields, file upload, master-detail, list views), ~29 hr scoped, full file-level change map.
- Created [`docs/planning/MAKER_CHECKER_PLAN.md`](./planning/MAKER_CHECKER_PLAN.md) — Phase C, 5 sub-phases (DB+roles, state machine, approval UI, AI Builder integration, notifications), ~30 hr scoped (C5 optional for v1). State machine drawn, security notes, backward-compat guarantees.
- Updated [`docs/ACTIVE_TASKS.md`](./ACTIVE_TASKS.md) — new forward-plan table at top (Phase A / B / C / Stage 5); Stage 3.5 and Stage 4 statuses corrected to `Done`.

### Documentation consolidation commit (this session's final commit)
- Rewrote [`docs/README.md`](./README.md) as the **single navigation source of truth** — every doc labelled ✅ Current / ⚠️ Partial / 📚 Historical with links to the current authoritative source.
- Updated headers of the four active plan docs to cross-link to each other and to `ACTIVE_TASKS.md` and to reflect current status (not "DRAFT").
- Added HISTORICAL / PARTIAL banners to 13 stale docs pointing readers to the current source:
  - `docs/architecture/01-ARCHITECTURE.md`
  - `docs/architecture/ARCHITECTURE_VISUAL_SUMMARY.md`
  - `docs/architecture/ENTITY_FORM_BINDING_ARCHITECTURE.md`
  - `docs/features/ai-agent.md`
  - `docs/features/ai-builder-service.md`
  - `docs/features/multi-tenant-design.md`
  - `docs/features/page-templates.md`
  - `docs/features/SECURITY_FEATURES.md`
  - `docs/guides/02-DEVELOPMENT_GUIDE.md`
  - `docs/guides/SESSION_RESUME_GUIDE.md`
  - `docs/guides/04-USER_MANUAL.md`
  - `docs/planning/03-ROADMAP.md`
  - `docs/planning/IMPLEMENTATION_STORIES.md`
  - `docs/specs/WORKFLOW.md`
- Refreshed this `session_summary.md` (was still tracking Story 3.1 + 2026-07-25 planning session).

---

## Current forward plan (single source: [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md))

```
┌──────────────────────────────────────────────────────────────────┐
│  Phase A — Quality Sprint  (Runtime UX Sprint 2)                 │
│  Goal: customer-demo-ready UI                                    │
│  Duration: ~10 focused hours                                     │
│  Deliverable: existing apps look defensible                      │
└──────────────────────────────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│  Phase B — Complex UI Epic (~29 hr)                              │
│    B1 Wizards · B2 Conditional fields · B3 File upload           │
│    B4 Master-detail · B5 List views (filter/group/saved views)   │
└──────────────────────────────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│  Phase C — Maker-Checker Epic (~30 hr, C5 optional for v1)       │
│    C1 DB+roles · C2 State machine · C3 Approval UI               │
│    C4 AI Builder integration · C5 Notifications                  │
└──────────────────────────────────────────────────────────────────┘
                            ▼
                    🚀 First customer live
                    (Stage 5 subdomain deploy runs in parallel)
```

---

## Next session goal

**Start Phase A: [Runtime UX Sprint 2](./planning/RUNTIME_UX_OVERHAUL_PLAN.md#sprint-2--make-it-feel-professional), Task 2.1** — replace native `<input type="date">` with `react-day-picker` in a popover. Format displayed value with `date-fns` per locale. Emit ISO 8601 on submit so the backend contract doesn't change.

Then serially through 2.2–2.10 as batch-shippable PRs.

---

## Documentation consistency rules established this session

1. **[`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md) is the single source for status.** Plans track their own exit criteria; status labels live in one place.
2. **[`docs/README.md`](./README.md) is the single source for navigation.** New docs must be added there or they don't exist.
3. **[`.github/copilot-instructions.md`](../.github/copilot-instructions.md) §2 + §3 + §5 is the single source for "how the system runs today".** Detailed dives live in `features/` and `architecture/` (with currency banners).

When these rules are broken, fix by editing `docs/README.md` + `ACTIVE_TASKS.md` first, then propagate to individual docs. Never the reverse.
