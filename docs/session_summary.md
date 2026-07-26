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

### Commit `c3793ff` — docs(plan): add Enterprise Capabilities epic (Phase D)
**Trigger:** Screenshot-by-screenshot comparison with a live enterprise SaaS (global shipping/logistics platform, ~10k concurrent users) exposed the categorical gap between AppBana today and what an enterprise IT department procures.

**Deliverable:** [`planning/ENTERPRISE_CAPABILITIES_PLAN.md`](./planning/ENTERPRISE_CAPABILITIES_PLAN.md) — 4 sub-phases (D1 SSO ~35 hr · D2 dashboards + widgets ~40 hr · D3 notifications ~30 hr · D4 enterprise shell ~20 hr) totalling ~125 hr. Original ordering committed as A → D → B → C.

### Pending commit — docs(plan): re-order forward plan to A → B → C → D
**Decision (2026-07-26):** After frank discussion with the product owner, the forward plan re-ordered twice more:

1. First revision (same day): A → **B** → **D** → C. Reasoning: B is table-stakes (conditional fields, file upload, master-detail, list views). No amount of enterprise SSO saves a product that can't build a working onboarding form. Fundamentals compound; enterprise polish doesn't compound backwards onto broken fundamentals.

2. **Final revision (same day)**: A → B → **C** → **D**. Reasoning: approvals are the *product* (AppBana's AI-generated regulated-industry workflow differentiator), D is *packaging*. Ship the product first, package it second. Concrete points:
    - Every SaaS has SSO; few have AI-generated maker-checker. C is why customers pick us over the incumbent.
    - C is ~30 hr, D is ~125 hr. C-first means a differentiator lands in ~1 week vs. 4–5 weeks of D infrastructure before anything new is user-visible.
    - Prospects will PoC with local auth; they won't PoC with broken approvals.
    - D's specifics (which OIDC provider, group→role mapping, branding) are better co-designed with a real prospect than guessed. Delaying D means D1 lands against real requirements.

**Tradeoff:** C5 (notifications on maker-checker transitions) ships inside C with a simple polling badge (~2–3 hr of throwaway code). Once D3 lands, C5's substrate is replaced with the durable rule-driven notification system. Negligible cost.

**Files updated for the re-order:**
- [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md) — forward-plan table rows re-ordered A → B → C → D; execution-order and rationale paragraphs rewritten.
- [`README.md`](./README.md) — persona-table row order + forward-plan bullets + folder-layout tree all reflect A → B → C → D.
- [`planning/MAKER_CHECKER_PLAN.md`](./planning/MAKER_CHECKER_PLAN.md) — header now says "runs before Phase D"; C5 substrate story updated to "ships with polling badge, D3 swaps in later."
- [`planning/ENTERPRISE_CAPABILITIES_PLAN.md`](./planning/ENTERPRISE_CAPABILITIES_PLAN.md) — header now says "**Last** epic — depends on A + B + C"; explicit "Why D goes after C (not before)" paragraph added; execution-order + C5 dependency mentions corrected.
- [`planning/COMPLEX_UI_PLAN.md`](./planning/COMPLEX_UI_PLAN.md) — cross-plan header now says D "runs *after* Phase C".

---

## Current forward plan

```
Phase A — Runtime UX Sprint 2        (~10 hr)   — customer-demo-ready UI
     ↓
Phase B — Complex UI Epic            (~29 hr)   — wizards · conditional fields · upload · master-detail · list views
     ↓
Phase C — Maker-Checker Epic         (~30 hr)   — approvals + audit (the differentiator)
     ↓
🚀 Demo-able differentiated product  (~69 hr total, ~2 weeks of focused work)
     ↓
Phase D — Enterprise Capabilities    (~125 hr)  — SSO · dashboards · notifications · enterprise shell
     ↓
🚀 First enterprise customer live    (~194 hr total; Stage 5 subdomain deploy runs in parallel)
```

**Single source of truth:** [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md).

---

## Next session goal

**Start Phase A, Task 2.1** — replace the runtime's native `<input type="date">` with `react-day-picker` in a popover. Format via `date-fns`, emit ISO 8601 on submit. See [`planning/RUNTIME_UX_OVERHAUL_PLAN.md`](./planning/RUNTIME_UX_OVERHAUL_PLAN.md) Sprint 2 §2.1.

After A ships: begin B1 (wizards). C1+C2 (backend-only) can optionally start in parallel with B if we want to compress schedule; C3 onward genuinely wants B done first.

---

## Consistency rules (unchanged)

1. [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md) — single source for status.
2. [`README.md`](./README.md) — single source for navigation.
3. [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) §2 + §3 + §5 — single source for "how the system runs today".
