# Session Summary — 2026-07-27

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
- Phase A (Runtime UX Sprint 2): Tasks 2.1–2.10 shipped
- Phase A2 (Runtime Foundations Sprint 3): Tasks 3.1–3.12 shipped
- `94714d6` — fix: clear 23 pre-existing test failures (207/207 pass)
- Phase B Complex UI Epic: B1 wizards (`8efc539`), B2 conditional fields (`e8a9c9a`), B3 file upload (`dd84257`), B4 master-detail (`60a64aa`), B5 list views (`4ce56d0`)
- Phase B.H Hardening Sprint: H1 file tenant isolation (`cb7a4d1`), H2 auto-inject parentId + ChildTable (`d73bfcd`), H3 wire FilterBar + SavedViewsBar (`e3a129a`), H4 real FK constraints (`f3b3a2c`), H5 hidden-field validation strip (`02ad025`), H6 SQL GROUP BY (`a0702ca`), H7 Playwright hardening suite (`83bcc6b`)

### Hardening Sprint (B.H) — Summary

The technical-architect review of Phase B flagged 8 hardening gaps. All 8 are now resolved:

| # | Item | Fix |
|---|------|-----|
| H1 | File upload lacked tenant isolation | `FileRoutes` enforces `tenant_id` scope on upload/download |
| H2 | Child records missing auto-injected `parentId` | `ChildTable.tsx` auto-injects `parentId` from parent context |
| H3 | FilterBar + SavedViewsBar not wired into StudioTableLive | Both components integrated into `StudioTableLive.tsx` |
| H4 | No real FK constraints in PostgreSQL | `SchemaManager` creates `FOREIGN KEY` constraints for reference fields |
| H5 | Hidden conditional fields still validated | Hidden fields stripped before validation on form submit |
| H6 | GROUP BY aggregation limited to current page | SQL GROUP BY runs across full dataset via `/api/{entity}/aggregate` |
| H7 | No Playwright tests for complex UI features | 5 spec files, 8 tests covering wizard, upload, master-detail, filter, saved views |
| H8 | Documentation out of date | copilot-instructions.md, docs/README.md, session_summary.md refreshed |

Backend: 220/220 tests pass · Runtime Vitest: 147/147 · E2E Playwright: 8/8 discoverable.

---

## Current forward plan

```
[✅ Done]     Phase A  — Runtime UX Sprint 2
[✅ Done]     Phase A2 — Runtime Foundations (Sprint 3)
[✅ Done]     Phase B  — Complex UI Epic (B1–B5)
[✅ Done]     Phase B.H — Hardening Sprint (H1–H8)
     ↓
[⏳ Next]    Phase C  — Maker-Checker Epic         (~30 hr)
     ↓
🎯 Demo-able differentiated product
     ↓
[⏳ Planned] Phase D  — Enterprise Capabilities    (~125 hr)
[⏳ Planned] Stage 5  — Production Deploy          (~50 hr)
     ↓
🚀 First enterprise customer live
     ↓
[📝 Backlog] Phase E  — Integration + Advanced     (~87 hr, post-launch)
```

**Single source of truth:** [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md).

---

## Next session goal

**Start Phase C — Maker-Checker Epic, sub-phase C1** (DB migration + role model). See [`planning/MAKER_CHECKER_PLAN.md`](./planning/MAKER_CHECKER_PLAN.md).

Tasks:
- C1.1 — Liquibase changesets for `appbana_approvals` + `appbana_user_roles`
- C1.2 — `SchemaEnricher` approval column injection
- C1.3 — `SchemaManager` persistence of `approvalRequired` flag
- C1.4 — `UserRoleService` CRUD
- C1.5 — Bootstrap: app creator gets `role: 'both'`
- C1.6 — `RoleRoutes.java` REST endpoints

---

## Consistency rules (unchanged)

1. [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md) — single source for status.
2. [`README.md`](./README.md) — single source for navigation.
3. [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) §2 + §3 + §5 — single source for "how the system runs today".
