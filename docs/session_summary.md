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
[🟡 In Prog] Phase C  — Maker-Checker Epic (C1 + C2 complete; C3 next)
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

**Start Phase C sub-phase C3 — Runtime approval UI.** See [`planning/MAKER_CHECKER_PLAN.md`](./planning/MAKER_CHECKER_PLAN.md) §C3.

Nothing in `app-bana-runtime/` is approval-aware yet — no `Approval*.tsx`, `Checker*.tsx` or `Audit*.tsx` exists.

Tasks:
- C3.1 — `ApprovalStatusPill.tsx`, auto-wired into the `StudioTableLive` cell renderer
- C3.2 — `FormActions.tsx`: `Save as draft` + `Submit for approval`
- C3.3 — `CheckerQueuePage.tsx` + `checker_queue` PageMeta layout
- C3.4 — `ApprovalDetail.tsx` + `RejectDialog.tsx` (side-by-side diff for revisions)
- C3.5 — `AuditDrawer.tsx`
- C3.6 — "My Drafts" / "Needs Rework" saved views
- C3.7 — global "Pending my approval" badge
- C3.8 — toasts on submit/approve/reject

The backend contract C3 builds on is now complete: revision rows expose `approval_parent_id`,
`?_approvalStatus=` narrows list pages, and `.../records/{id}/approvals/audit` returns the timeline
including the pre-merge snapshot for revision approvals.

---

## C2 gap closure (2026-07-28)

C2 was signed off with two plan items silently unimplemented. Both are now closed:

| # | Was | Now |
|---|-----|-----|
| C2.3 | `PUT` on an `APPROVED` row overwrote the live row in place and flipped it to `DRAFT`, destroying approved data | The live row is untouched; a separate `DRAFT` revision is written with `approval_parent_id`. On approve, `ApprovalService.approveRecord` merges the revision into the parent (parent id preserved so B.H4 foreign keys survive) and deletes the revision — one transaction |
| C2.7 | `?_approvalStatus=` did not exist | Implemented on all three GET list route families, with `PENDING` restricted to checkers. The `?filter=approval_status:…` side door is routed through the same check |
| — | `APPROVAL_COLUMNS` omitted `approval_parent_id`, so clients could forge it | Added to the strip-list |

New test file `RevisionFlowTest.java` (20 tests). Backend suite: **269/269 pass** (was 249/249).

A code review of the first commit (`88dbbaa`) surfaced four further defects, fixed in `62957f8`:
missing-parent merge reported a dead row id as live; `clearRevisionPointer` swallowed an exception
inside an open transaction and turned a rollback into a reported success; concurrent PUTs on the
same parent each inserted a revision (now serialised by a `SELECT … FOR UPDATE` mutex); and 64KB
audit-diff truncation destroyed the irreplaceable `before` snapshot (now sheds `after` first).

Deliberate deviation: no `superseded_by` column. See the note in
[`planning/MAKER_CHECKER_PLAN.md`](./planning/MAKER_CHECKER_PLAN.md) §C2.

---

## Consistency rules (unchanged)

1. [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md) — single source for status.
2. [`README.md`](./README.md) — single source for navigation.
3. [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) §2 + §3 + §5 — single source for "how the system runs today".
