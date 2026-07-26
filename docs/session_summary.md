# Session Summary — 2026-07-26

**Working branch:** `feature/ui-rebuild`

---

## What shipped this session

### Commit `723a193` — fix(studio): show login screen when session expires
`AuthGate.tsx` now listens for `appbana:auth:expired` and re-shows the login screen instead of freezing the studio on an empty shell.

### Commit `6edd19a` — chore(stage-4): retire `app-bana-ui/`
Deleted the LitElement studio + runtime, rewrote `start-everything.*` to a 4-stage flow, updated [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) §2/§3/§5. Verified `mvn compile`, `mvn -pl app-bana-service package`, and `tsc --noEmit` all pass.

### Commit `eca9f3a` — docs(plan): add Complex UI and Maker-Checker epic plans
- [`planning/COMPLEX_UI_PLAN.md`](./planning/COMPLEX_UI_PLAN.md) — Phase B (5 sub-phases, ~29 hr).
- [`planning/MAKER_CHECKER_PLAN.md`](./planning/MAKER_CHECKER_PLAN.md) — Phase C (5 sub-phases, ~30 hr).
- Added A/B/C forward-plan table to top of [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md).

### Commit `e6efc47` — docs: consolidate documentation, add currency banners
Intermediate consolidation pass. Superseded by the next commit.

### Commit `5455c9a` — docs: purge all stale/historical documentation
Deleted 19 stale files (canvas-era architecture, features, guides, plans, specs). Fixed the last stale port/folder/framework references in the 3 kept reference docs. Every doc under `docs/` is now current.

### Pending commit — docs(plan): add Enterprise Capabilities epic (Phase D)
**Trigger:** Screenshot-by-screenshot comparison with a live enterprise SaaS (global shipping/logistics platform, ~10k concurrent users) exposed the categorical gap between AppBana today and what an enterprise IT department procures.

**Verdict:** Phase A + B + C alone deliver a polished CRUD builder, not a product an enterprise buyer signs a contract for. Four blockers stand between the current stack and enterprise-customer credibility:

1. **Enterprise SSO** — Azure B2C / OIDC / SAML. Local email/password auth is a categorical no for enterprise procurement.
2. **Dashboards + widgets** — every executive-facing screen is a KPI dashboard. Missing this makes stakeholder demos land flat.
3. **Notifications** — durable per-user notification system with bell + unread badge + flyout. Phase C's C5 depends on this substrate.
4. **Enterprise shell** — multi-level sidebar with groups + icons + user dropdown + header action-slots + fully branded login.

**Deliverable:** [`planning/ENTERPRISE_CAPABILITIES_PLAN.md`](./planning/ENTERPRISE_CAPABILITIES_PLAN.md) — full plan with metadata contract additions, per-sub-phase file-level change maps, security notes, backwards-compat guarantees, and exit criteria. Total scope ~125 hr / 4 sub-phases.

Also updated:
- [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md) — forward-plan table now A → **D** → B → C. Total effort revised: ~64 hr → ~189 hr.
- [`README.md`](./README.md) — Phase D added to navigation, folder-layout diagram, and current-state summary.
- [`COMPLEX_UI_PLAN.md`](./planning/COMPLEX_UI_PLAN.md) + [`MAKER_CHECKER_PLAN.md`](./planning/MAKER_CHECKER_PLAN.md) — cross-plan headers now list Phase D as prerequisite (C5 notifications reuse D3 substrate).

---

## Current forward plan

```
Phase A — Runtime UX Sprint 2         (~10 hr)  — customer-demo-ready UI
     ↓
Phase D — Enterprise Capabilities     (~125 hr) — SSO · dashboards · notifications · enterprise shell
     ↓
Phase B — Complex UI Epic             (~29 hr)  — wizards · upload · master-detail · list views
     ↓
Phase C — Maker-Checker Epic          (~30 hr)  — approvals + audit (C5 reuses D3)
     ↓
🚀 First enterprise customer live   (Stage 5 subdomain deploy runs in parallel)
```

**Total: ~194 hr / ~5–6 focused weeks.** Single source of truth: [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md).

---

## Next session goal

**Start Phase A, Task 2.1** — replace the runtime's native `<input type="date">` with `react-day-picker` in a popover. Format via `date-fns`, emit ISO 8601 on submit. See [`planning/RUNTIME_UX_OVERHAUL_PLAN.md`](./planning/RUNTIME_UX_OVERHAUL_PLAN.md) Sprint 2 §2.1.

After A ships, D1 (SSO) and D4 (enterprise shell) can start in parallel. D2 (dashboards) is the highest-visibility win — recommend leading with D2 for the next customer demo.

---

## Consistency rules (unchanged)

1. [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md) — single source for status.
2. [`README.md`](./README.md) — single source for navigation.
3. [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) §2 + §3 + §5 — single source for "how the system runs today".
