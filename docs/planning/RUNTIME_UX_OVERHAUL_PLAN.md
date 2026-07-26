# Runtime UX Overhaul — Implementation Plan

**Status:** ✅ Sprint 1 shipped (8/10 tasks done, 2 partial by design) · ⏳ Sprint 2 = **Phase A** in the current forward plan · ⏳ Sprint 3 deferred to post-launch
**Owner:** AppBana core team
**Position in master roadmap:** Sprint 1 gated Stage 4 of the [AI-Native UI Rebuild Plan](./AI_NATIVE_UI_REBUILD_PLAN.md). Stage 4 shipped 2026-07-26; Sprint 2 is now the leading edge of the post-Stage-4 forward plan ("Phase A").
**Trigger:** Design review of the deployed Customer Onboarding App runtime on 2026-07-26 revealed severity-1 UX defects that would cause a prospective client to reject the product on sight. Before we throw away the old UI, the new one must be visibly better — not merely functionally equivalent.

**Related active plans:**
- [AI-Native UI Rebuild Plan](./AI_NATIVE_UI_REBUILD_PLAN.md) — the master rebuild plan. Stage 5 (Production Deploy) runs after A/B/C/D and includes containerization + Redis + observability.
- [Complex UI Plan](./COMPLEX_UI_PLAN.md) — **Phase B** (next after A).
- [Maker-Checker Plan](./MAKER_CHECKER_PLAN.md) — **Phase C** (last before launch).
- Live status: [`ACTIVE_TASKS.md`](../ACTIVE_TASKS.md).

---

## Table of Contents

1. [TL;DR](#tldr)
2. [Why we are doing this now](#why-we-are-doing-this-now)
3. [Non-goals](#non-goals)
4. [Design system foundations (do these once)](#design-system-foundations-do-these-once)
5. [Sprint 1 — "Make it not embarrassing"](#sprint-1--make-it-not-embarrassing)
6. [Sprint 2 — "Make it feel professional"](#sprint-2--make-it-feel-professional)
7. [Sprint 3 — "Wow factor"](#sprint-3--wow-factor)
8. [Cross-cutting concerns](#cross-cutting-concerns)
9. [Exit criteria — the "client-ready" bar](#exit-criteria--the-client-ready-bar)
10. [Reference apps to benchmark against](#reference-apps-to-benchmark-against)
11. [File-level change map](#file-level-change-map)

---

## TL;DR

Every screen produced by the AppBana runtime today has one or more of these defects:

| Defect class | Where it shows | Severity |
|---|---|---|
| No page title, subtitle, or breadcrumb | Every form and list page | 🔴 1 |
| Raw ISO timestamps and unresolved FK IDs in list tables | `Onboarding Processes` list | 🔴 1 |
| `status` fields render as free-text inputs (not `<select>`) | `Add Onboarding Process` | 🔴 1 |
| 40–60% of the viewport is empty grey | All screens | 🔴 1 |
| Cramped label/input spacing; orphaned undersized Save button | All forms | 🟠 2 |
| Native `<input type=date>` with `dd-mm-yyyy` placeholder | `Add Onboarding Process` | 🟠 2 |
| Sidebar labels truncate silently ("Onboarding Processes …") | All screens with sidebar | 🟠 2 |
| ALL-CAPS `CREATED AT`-style column headers | All list tables | 🟠 2 |
| No empty states, no loading skeletons, no toast on save | Everywhere | 🟡 3 |
| No status pill colors, monotone grey + one purple | Everywhere | 🟡 3 |
| No user avatar / account menu / tenant switcher | Runtime top bar | 🟡 3 |
| No inline validation feedback | All forms | 🟡 3 |

Three sprints, ~20 hours of focused work, ship in this order:

- **Sprint 1** closes every 🔴 defect. ~7 hours. After this the product no longer embarrasses.
- **Sprint 2** closes every 🟠 defect. ~10 hours. After this the product is defensible.
- **Sprint 3** closes the 🟡 defects + dark mode + keyboard shortcuts. Nice-to-have.

**Stage 4 of the AI-Native UI Rebuild shipped 2026-07-26 after Sprint 1 met its exit criteria.** Sprint 2 = **Phase A** in the current forward plan; Sprint 3 is deferred to post-launch.

---

## Why we are doing this now

1. **Timing.** The old `app-bana-ui/` was retired in Stage 4 (2026-07-26, commit `6edd19a`). The new runtime is now the ONLY UI a customer will see. If it looks worse than the LitElement version did, we regress in perception even if we have advanced in architecture.
2. **The demo test.** A live demo of the current runtime — page title missing, dates in ISO 8601, `status = "New"` typed by hand, empty half-page — would be rejected in a first sales meeting.
3. **The defects are cheap to fix.** Every 🔴 item is a metadata-driven rendering change confined to five files. There is no architectural rework here. The cost is measured in hours, not sprints.
4. **The AI Builder is complicit.** Two 🔴 defects (status-as-text, unlabelled FK columns) are actually AI-Builder / SchemaEnricher gaps that leak into the runtime. Fixing them at the metadata layer means every future app benefits automatically, not just Customer Onboarding.
5. **We now have a design system boundary.** Sprint 0 (below) locks the design tokens and shadcn/ui adoption. Everything after that inherits polish for free.

---

## Non-goals

Explicitly out of scope for this plan:

- Redesigning the Studio (`app-bana-studio`) UI — this plan targets the runtime only.
- Building a WYSIWYG theme editor. Tenant branding lives in the backend (`appbana_tenants`); the runtime only *consumes* it.
- Custom illustrations / marketing pages — commodity illustration sets (unDraw, Pixel True) are acceptable for empty states.
- Mobile / small-screen redesign — desktop-first for v1; responsive is Sprint 3.
- Real-time collaboration cursors or presence.
- Full WCAG AAA — target AA in Sprint 2.
- Animation / motion design beyond simple transitions (hover, focus, page enter).

---

## Design system foundations (do these once)

**Do this first.** Every sprint below assumes these are locked. ~2 hours.

### 0.1 — Adopt shadcn/ui in `app-bana-runtime`

```bash
pnpm --filter @appbana/runtime dlx shadcn init
pnpm --filter @appbana/runtime dlx shadcn add button input label select textarea \
     dialog dropdown-menu tooltip popover toast table skeleton badge card \
     separator tabs form
```

Reason: every 🔴 and 🟠 item below has a shadcn primitive that ships accessible, keyboard-navigable, dark-mode-aware defaults.

### 0.2 — Lock design tokens in [`app-bana-runtime/src/globals.css`](../../app-bana-runtime/src/globals.css)

- **Font**: Inter (via `@fontsource/inter`) or Geist. Pick one, commit to it.
- **Spacing scale**: Tailwind default (4 / 8 / 12 / 16 / 20 / 24 / 32 / 48 / 64). Ban inline `text-[13px]` / `p-[7px]`.
- **Color tokens** (as CSS variables, dual light/dark):
  - `--primary` (violet-600), `--primary-fg`
  - `--surface`, `--surface-2`, `--surface-3`
  - `--border`, `--border-strong`
  - `--text-primary`, `--text-secondary`, `--text-tertiary`
  - `--success/-fg`, `--warning/-fg`, `--danger/-fg`, `--info/-fg` (each with -50/-100/-500/-700 for pill backgrounds)
- **Radii**: `rounded-lg` (8px) for inputs, `rounded-xl` (12px) for cards, `rounded-full` for pills.
- **Elevation**: `shadow-sm` for cards, `shadow-md` for hover/popover, `shadow-lg` for dialogs. No custom mixed shadows.
- **Type scale**: only `text-xs / -sm / -base / -lg / -xl / -2xl / -3xl`.

### 0.3 — Ban inline styles in the Renderer

The current [`Renderer.tsx`](../../app-bana-runtime/src/runtime/Renderer.tsx) passes raw inline `style` strings from page metadata to nodes (e.g. `"min-height: 100px; padding: 0.5rem;"`). This bypasses the design system.

Rule: **Renderer emits Tailwind class names only**. Any node metadata containing an inline `style` string is mapped to the nearest equivalent Tailwind classes at render time, or dropped with a warn-log.

### 0.4 — Establish the `PageShell` component

New file `app-bana-runtime/src/runtime/PageShell.tsx` — a mandatory wrapper for every rendered page:

```tsx
<PageShell
  title={page.title ?? page.name}
  subtitle={page.subtitle}
  breadcrumb={breadcrumbFor(page)}
  actions={<PageActions page={page} />}
>
  {children}
</PageShell>
```

Every list, form, and detail page renders inside this shell. This is the single fix for the #1 defect.

---

## Sprint 1 — "Make it not embarrassing"

**Goal:** No 🔴 defects remain on any screen the runtime produces.
**Budget:** ~7 hours.
**Blocks:** Stage 4 of AI-Native UI Rebuild.

| # | Task | Where | Est. | Owner |
|---|---|---|---|---|
| 1.1 | `PageShell` component: H1 title, optional subtitle, breadcrumb slot, right-aligned actions slot | new `PageShell.tsx`; wrap in [`Renderer.tsx`](../../app-bana-runtime/src/runtime/Renderer.tsx) at page-root level | 45 min | |
| 1.2 | List-table date formatter — `Jul 25, 2026 · 6:26 PM` (short) + tooltip with full ISO. Uses `date-fns` `format` + `Tooltip`. Applied to `date`, `datetime`, `created_at`, `updated_at` columns | [`StudioTableLive.tsx`](../../app-bana-runtime/src/runtime/StudioTableLive.tsx) — new `formatCellValue()` | 45 min | |
| 1.3 | FK label resolution in list tables. When a column is `type: "reference"`, pre-fetch the target entity's rows once per table load and render `optionLabelFor(row)` instead of the raw ID. Falls back to `#<id>` when the label can't be resolved | [`StudioTableLive.tsx`](../../app-bana-runtime/src/runtime/StudioTableLive.tsx) | 60 min | |
| 1.4 | Header row styling: sentence-case title (`Created At`, not `CREATED AT`), `text-xs font-medium text-slate-500 uppercase tracking-wider` visual style is fine but text should not literally be uppercase in the DOM (accessibility + copy-paste) | [`StudioTableLive.tsx`](../../app-bana-runtime/src/runtime/StudioTableLive.tsx) | 15 min | |
| 1.5 | Status-field enforcement in AI Builder: `SchemaEnricher.enrich()` — when a field has `type: "status"` and no `options[]`, either infer common options (`["New","In Progress","Completed","Cancelled"]`) or emit a WARN and downgrade to `text` with a log. `GeneratePageTool.buildFormPage()` — emit `select` node when `fieldType == "status"`. Renderer already handles `<select>` | [`SchemaEnricher.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/SchemaEnricher.java), [`GeneratePageTool.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/GeneratePageTool.java) | 60 min | |
| 1.6 | Form spacing overhaul. Label→input `gap-1.5`, field→field `gap-6`, card `p-8`, input `h-11`. Kill inline styles in favour of a `FormField` wrapper component | [`Renderer.tsx`](../../app-bana-runtime/src/runtime/Renderer.tsx) case `form`/`input`/`container`; new `FormField.tsx` | 45 min | |
| 1.7 | Sticky action bar for forms. Right-aligned primary Save button (`h-11 px-6`), tertiary Cancel on the left, optional `Save & Add Another` in the middle. Loading state swaps label → `[spinner] Saving…` and disables. Sticks to bottom of the form card | new `FormActions.tsx`; wire in `EntityForm` inside [`Renderer.tsx`](../../app-bana-runtime/src/runtime/Renderer.tsx) | 45 min | |
| 1.8 | Toast notifications. Install `sonner`, mount `<Toaster />` in [`AppRuntimeShell.tsx`](../../app-bana-runtime/src/runtime/AppRuntimeShell.tsx). Fire success toast on `appbana:row-inserted` with entity label. Fire error toast on save failure with server-side message | `AppRuntimeShell.tsx`, `EntityForm` | 30 min | |
| 1.9 | Row hover state + row-actions column. `hover:bg-slate-50`, `cursor-pointer`, trailing column with `⋯` dropdown (Edit / Delete / Copy ID). Actions dispatch through the existing PageMeta navigation (no new backend endpoints) | [`StudioTableLive.tsx`](../../app-bana-runtime/src/runtime/StudioTableLive.tsx) | 60 min | |
| 1.10 | Fill the viewport. Form pages: center card with `max-w-2xl` on left, right-side `max-w-sm` helper panel showing recent records of the same entity. List pages: table card expands to `flex-1`, footer with pagination + total count | [`Renderer.tsx`](../../app-bana-runtime/src/runtime/Renderer.tsx), [`StudioTableLive.tsx`](../../app-bana-runtime/src/runtime/StudioTableLive.tsx) | 45 min | |

### Exit criteria — Sprint 1

All of the following must pass on the reference "Customer Onboarding App" (tenant `t-81919f7d`, app `6a9b2abc-eaf6-45de-9594-012c53d999d2`):

- [ ] Every page (Customer List, Add Customer, Onboarding Processes, Add Onboarding Process) has a visible H1 title.
- [ ] Every date column in every list table renders as human-readable text (never raw ISO).
- [ ] The `Customer` column on `Onboarding Processes` shows `"Alice Johnson"` for every row — no numeric FK IDs anywhere in the DOM.
- [ ] `Onboarding Status` on the Add form is a `<select>` populated from the schema, not a free-text input.
- [ ] Every form card fills at least 60% of the viewport height OR has a right-side context panel filling the remainder.
- [ ] Save button is right-aligned in a sticky action bar with a visible Cancel button.
- [ ] Saving a Customer or an Onboarding Process fires a success toast within 500ms and the row appears in the list without a page reload.
- [ ] Hovering any list row shows a background change and reveals the `⋯` actions menu.
- [ ] Regression pack `SchemaEnricherAndPageToolFixTest` still green + new tests for status coercion.
- [ ] Playwright smoke test: full flow (login → Add Customer → Add Onboarding Process with FK dropdown → verify row in list with resolved FK label + formatted date) passes.

**Stage 4 is unblocked when this list is fully checked.**

---

## Sprint 2 — "Make it feel professional"

**Goal:** No 🟠 defects remain. Runtime is defensible in a client demo.
**Budget:** ~10 hours.
**Depends on:** Sprint 1.
**May run in parallel with:** AI-Native UI Rebuild Stage 4 and Stage 5.

| # | Task | Where | Est. |
|---|---|---|---|
| 2.1 | ✅ **Shipped 2026-07-26.** Replaced native `<input type="date">` with `react-day-picker` in a popover. Displays `MMM d, yyyy` (date) or `MMM d, yyyy h:mm a` (datetime) via `date-fns`. Emits ISO 8601 through a hidden input so the backend contract is unchanged. `datetime` variant adds a `<input type="time">` inside the popover | new `DatePicker.tsx`, wired into `Renderer.tsx` `input` case for `date`/`datetime`/`datetime-local` | 90 min |
| 2.2 | ✅ **Shipped 2026-07-26.** Sidebar widened to `w-64`. Pages auto-group by entity ("Add Customer" + "Customer List" + "Customer Detail" cluster under a "Customers" section); Dashboard/Settings/Reports go to an "Other" section. Native `title` tooltips on every link so full labels are visible on hover. Icons follow the plan (List/Add/Detail/Home/Chart/Gear/Doc — zero-dep inline SVGs, Lucide conventions). Ordering inside a group is deterministic: List → Add → Detail | [`RuntimeSidebar.tsx`](../../app-bana-runtime/src/runtime/RuntimeSidebar.tsx), new `RuntimeSidebar.test.ts` (20 tests) | 60 min |
| 2.3 | ✅ **Shipped 2026-07-26.** New `EmptyState` primitive with bundled inline SVG illustrations (`records`, `form`, `customer`, `tasks`) chosen automatically from the entity name. When the current app has a matching "Add {Entity}" page, the empty state renders a solid indigo CTA button that navigates to it through a new `RuntimeNavigationContext`; when no add-page exists, we fall back to guidance text. Wired into `StudioTableLive.tsx` | new `EmptyState.tsx` + `illustrations.tsx` + `runtime-navigation.tsx` + `page-classifier.ts` (extracted from RuntimeSidebar so both consumers share the same regexes). 10 new unit tests | 60 min |
| 2.4 | ✅ **Shipped 2026-07-26.** New zero-dep `Skeleton` primitive (matches shadcn's API) with three composed variants: `TableSkeleton` (5 shimmer rows + head, min 3 cols), `FormSkeleton` (label + input pairs), and `AppLoadingSkeleton` (appbar + sidebar + main pane). `StudioTableLive` now renders `TableSkeleton` during fetch; `ReferenceField` swaps its "Loading options…" disabled `<select>` for a shimmer bar; `AppRuntimeShell` replaces the plain "Loading app…" text with the full-shell skeleton. Pulse animation is disabled under `prefers-reduced-motion`. 11 new unit tests | new `Skeleton.tsx` + `Skeleton.test.tsx`; wired into `StudioTableLive.tsx`, `Renderer.tsx` `ReferenceField`, `AppRuntimeShell.tsx` | 60 min |
| 2.5 | ✅ **Shipped 2026-07-26.** Inline validation via a new `useEntityFormValidation` hook that derives a Zod schema from the form's actual `HTMLFormElement` at submit time (required, min/max, minLength/maxLength, pattern, email/url, and `data-appbana-format="phone"`). New `FormField` wrapper renders the label with a red `*` for required fields, the control, help text, and a `FieldError` (`role="alert"`) paragraph — invalid inputs get `aria-invalid="true"` and a rose-outlined border via `.appbana-field-invalid`. Field-renderer cases in `Renderer.tsx` (`input`, `select`, `textarea`, `reference`) now emit `FormField` + `ValidatedInput`/`ValidatedSelect`/`ValidatedTextarea`; `EntityForm` calls `validate(form)` first, focuses the first invalid input on failure, and toasts "Please fix the highlighted fields". Shipped **zod-only** — react-hook-form was skipped because our field renderers are uncontrolled (defaultValue + FormData readback), so RHF's `register` pattern would require a full refactor of every field type without user-visible upside for what a 150-line DOM validator delivers. RHF remains a future upgrade path for cross-field validation. 9 new unit tests | new `useEntityFormValidation.ts` + `entity-form-context.tsx` + `FieldError.tsx` + `FormField.tsx`; `Renderer.tsx` refactor; `globals.css` `.appbana-field-invalid` / `.appbana-field-error` rules; `+zod ^4.4.3` | 90 min |
| 2.6 | ✅ **Shipped 2026-07-26.** New `StatusPill` component wraps the existing `.appbana-status-pill` markup and delegates tone classification to a re-tuned `classifyStatus` helper so tables and future detail views share one source of truth. Mapping now matches the plan exactly: New / Open / Draft → info (blue), In Progress / Processing / On Hold → warning (amber), Completed / Approved → success (green), Blocked / Cancelled / Failed → danger (red), fallback → neutral (slate). `StudioTableLive` now emits `<StatusPill>` for both `type: 'status'` cells and boolean cells (Yes → success, No → neutral). Empty values render a muted em-dash by default (or `emptyMode='hide'` to render nothing). 10 new unit tests; 5 existing `classifyStatus` tests updated for the new mapping | new `StatusPill.tsx` + `StatusPill.test.tsx`; `cell-formatters.ts` `TONE_RULES` retuned; `StudioTableLive.tsx` cell renderer swapped over | 45 min |
| 2.7 | ✅ **Shipped 2026-07-26.** New `UserMenu` component rendered in the appbar right-slot: an indigo avatar showing the user's initials (from `name`, falling back to the email local-part) opens a 256px panel with the user's primary label, secondary email, tenant display name (fetched via `fetchBranding` with a graceful fallback to `tenantId`), and a "Sign out" row. Shipped **zero-dep** instead of shadcn dropdown-menu — the rest of the runtime is already zero-dep with plain Tailwind classes, so pulling in shadcn's whole primitive set for one menu wasn't worth ~40 KB. Behaviour matches shadcn's contract: `aria-haspopup="menu"` + `aria-expanded` on the trigger, `role="menu"` / `role="menuitem"` inside, outside-click + Escape close (focus returns to the trigger), `<hr>` separator. Sign-out clears `appbana_token` + `appbana_user` and reloads (backend `/api/auth/logout` doesn't exist yet; when added, call it before the reload). 10 new unit tests | new `UserMenu.tsx` + `UserMenu.test.tsx`; `AppRuntimeShell.tsx` wires it into the appbar and fetches tenant branding; `globals.css` adds `.appbana-user-menu-*` rules | 60 min |
| 2.8 | Page-level actions surface. Detail pages get an "Edit" / "Delete" button pair in the PageShell `actions` slot. List pages get a "New <EntityLabel>" primary button in the same slot | [`PageShell.tsx`](../../app-bana-runtime/src/runtime/PageShell.tsx), [`Renderer.tsx`](../../app-bana-runtime/src/runtime/Renderer.tsx) | 45 min |
| 2.9 | AA-level accessibility pass. Focus rings visible on all interactive elements, `aria-label` on icon-only buttons, form-label association via `htmlFor`, keyboard-only navigation of tables, `axe-core` clean in Playwright | across runtime | 90 min |
| 2.10 | Responsive breakpoints. Sidebar collapses to icon rail below `md`, collapses to hamburger below `sm`. Form grid switches from 2-col to 1-col. Tested at 1440 / 1024 / 768 / 375 widths | `RuntimeSidebar.tsx`, `Renderer.tsx` `app-grid` case | 90 min |

### Exit criteria — Sprint 2

- [ ] Every 🟠 row above ships.
- [ ] `axe-core` reports zero violations on Customer Onboarding App's four core screens.
- [ ] Runtime renders correctly at 1440, 1024, 768, and 375 wide.
- [ ] Every list has an empty-state, a skeleton-loading state, and a populated state; screenshots archived in `docs/design/runtime-states/`.
- [ ] Every date is human-formatted; every date input uses the popover picker.

---

## Sprint 3 — "Wow factor"

**Goal:** Runtime matches the perceived quality of Linear / Notion / Airtable.
**Budget:** discretionary — one item per sprint after Sprint 2.
**Depends on:** Sprint 2.

| # | Task | Est. |
|---|---|---|
| 3.1 | Dark mode. Tailwind `dark:` variants everywhere, tokens driven by CSS vars, toggle in user menu, persisted per user in localStorage | 3 hr |
| 3.2 | Keyboard shortcuts. `Cmd+K` command palette (jump to any page or entity record), `Cmd+Enter` submit form, `Cmd+N` new record on the current entity, `?` shortcut sheet. Use `cmdk` library | 3 hr |
| 3.3 | Micro-interactions. Framer Motion for: page enter fade, dialog scale-in, toast slide-in, row-inserted flash-highlight | 2 hr |
| 3.4 | Bulk actions on tables. Row checkboxes, bulk-select header state, bulk-delete confirmation dialog | 2 hr |
| 3.5 | Inline editing in list tables. Double-click a cell → editable, Escape reverts, Enter saves via existing `PUT /api/{entity}/{id}` | 3 hr |
| 3.6 | Global search. `Cmd+K` searches across every entity in the current app; server-side aggregate via existing `?search=` param | 3 hr |

Sprint 3 items are individually shippable and unordered. Ship the ones the market asks for first.

---

## Cross-cutting concerns

### AI Builder contribution (metadata upgrades)

Sprint 1 fixes two things at the AI-Builder metadata layer, not just the runtime:

1. **Status field enforcement** — `SchemaEnricher` and `GeneratePageTool` (task 1.5).
2. **Reference field label hint** — extend the `reference` field metadata with an optional `labelField` so the runtime knows which column to render instead of ID. If absent, runtime falls back to the case-insensitive `name / full_name / title / label / email / code` search that already exists.

Both changes are **backward compatible**. Existing apps continue to work; new apps get better UX automatically.

### Design-time preview

The Studio's preview iframe (Stage 2) shows the runtime as-is. Every polish improvement in this plan is immediately visible in the Studio preview — no separate design-time work needed. Tenant branding (Stage 0) is also honoured.

### Testing strategy

- **Unit**: shadcn primitives are already tested upstream. Test our wrappers (`PageShell`, `FormField`, `StatusPill`, `DatePicker`) with React Testing Library.
- **Integration**: Playwright suite in `e2e/` grows a `runtime-polish.spec.ts` covering: page title present on every page, dates formatted, FK labels resolved, toasts fire, sticky action bar visible.
- **Visual regression**: Playwright screenshot diffing per sprint checkpoint. Archive baseline in `e2e/screenshots/runtime/`.
- **Accessibility**: `@axe-core/playwright` in the same suite. Zero violations required in Sprint 2 exit.

### Rollout safety

Every change lives behind an implicit feature flag — the metadata contract. If a new component regresses an existing app, the fix is a metadata patch, not a code rollback. No apps in production need a "flag flip" to receive polish.

### Documentation

- Screenshots of the "before" state (2026-07-26) archived at `docs/design/before-2026-07-26/` — do not delete. They are the reference point for the polish delta.
- `docs/design/runtime-states/` — populated in Sprint 2 with one screenshot per (entity × state) pair.
- This plan updates `docs/ACTIVE_TASKS.md` in the same commit as Sprint 1 kickoff.

---

## Exit criteria — the "client-ready" bar

The runtime is "client-ready" — and Stage 4 of the AI-Native UI Rebuild may proceed — when **all Sprint 1 exit criteria pass** AND a fresh scaffold of any of these demo prompts renders correctly end-to-end:

- **"Build me an inventory app for a small hardware store"** — expect a Product entity, Category reference, Stock count, at least one status field, dates rendered correctly.
- **"I want to track customer support tickets"** — expect a Ticket entity, Customer reference, Priority status pill, Assigned To reference, created/updated dates.
- **"Simple event booking system for a yoga studio"** — expect Class, Instructor reference, Booking with datetime, capacity numeric, status pill.

For each demo:

- [ ] Every list page has a title, human-formatted dates, resolved FK labels, colored status pills, populated / empty / loading states.
- [ ] Every form page has a title, `<select>` for status fields, popover date picker for date fields, sticky action bar, success toast on save.
- [ ] Sidebar is grouped, no label truncates without a tooltip, user menu is present.
- [ ] Playwright suite green at 1440 / 1024 / 768 widths, zero axe violations.

Fail any single row → block Stage 4 and open a defect ticket referencing this plan.

---

## Reference apps to benchmark against

When implementing any component, pull up the corresponding pattern from these apps first:

| Concern | Reference |
|---|---|
| Form patterns, sticky actions, sidebar grouping | [Linear](https://linear.app) |
| Page headers, breadcrumbs, empty states, quiet chrome | [Notion](https://notion.so) |
| List tables, row hover, FK labels, inline editing | [Airtable](https://airtable.com) |
| Generated-form styling (closest peer) | [Retool](https://retool.com) |
| End-user runtime for a no-code builder | [Glide](https://glideapps.com) |
| Table + detail views, status pills, empty states | [Stripe Dashboard](https://dashboard.stripe.com) |
| Command palette, keyboard shortcuts (Sprint 3) | [Linear](https://linear.app), [Raycast](https://raycast.com) |

Do not invent. Steal-and-adapt from these five apps first.

---

## File-level change map

### New files (Sprint 1)

- `app-bana-runtime/src/runtime/PageShell.tsx`
- `app-bana-runtime/src/runtime/FormField.tsx`
- `app-bana-runtime/src/runtime/FormActions.tsx`
- `app-bana-runtime/src/runtime/cell-formatters.ts` (date / FK / status helpers)
- `app-bana-runtime/src/components/ui/*` (shadcn install output — button, input, label, select, dialog, dropdown-menu, tooltip, table, toast, badge, card, separator, skeleton)

### Modified files (Sprint 1)

- [`app-bana-runtime/src/runtime/Renderer.tsx`](../../app-bana-runtime/src/runtime/Renderer.tsx) — wrap in `PageShell`, use `FormField` + `FormActions`, kill inline styles
- [`app-bana-runtime/src/runtime/StudioTableLive.tsx`](../../app-bana-runtime/src/runtime/StudioTableLive.tsx) — cell formatters, FK label resolution, row hover, actions column, empty-space fill
- [`app-bana-runtime/src/runtime/AppRuntimeShell.tsx`](../../app-bana-runtime/src/runtime/AppRuntimeShell.tsx) — mount `<Toaster />`, breadcrumb source
- [`app-bana-runtime/src/globals.css`](../../app-bana-runtime/src/globals.css) — design tokens
- [`ai-builder/src/main/java/com/appbana/ai/agent/tool/SchemaEnricher.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/SchemaEnricher.java) — status-options enforcement
- [`ai-builder/src/main/java/com/appbana/ai/agent/tool/GeneratePageTool.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/GeneratePageTool.java) — emit `select` for status
- [`ai-builder/src/test/java/com/appbana/ai/agent/tool/SchemaEnricherAndPageToolFixTest.java`](../../ai-builder/src/test/java/com/appbana/ai/agent/tool/SchemaEnricherAndPageToolFixTest.java) — new assertions

### New files (Sprint 2)

- `app-bana-runtime/src/runtime/DatePicker.tsx`
- `app-bana-runtime/src/runtime/EmptyState.tsx`
- `app-bana-runtime/src/runtime/StatusPill.tsx`
- `app-bana-runtime/src/runtime/useEntityForm.ts`
- `app-bana-runtime/src/runtime/UserMenu.tsx`
- `e2e/tests/runtime-polish.spec.ts`

### Modified files (Sprint 2)

- [`app-bana-runtime/src/runtime/Renderer.tsx`](../../app-bana-runtime/src/runtime/Renderer.tsx) — date-input replacement, form validation
- [`app-bana-runtime/src/runtime/RuntimeSidebar.tsx`](../../app-bana-runtime/src/runtime/RuntimeSidebar.tsx) — width, grouping, tooltips, responsive collapse
- [`app-bana-runtime/src/runtime/StudioTableLive.tsx`](../../app-bana-runtime/src/runtime/StudioTableLive.tsx) — empty state, skeleton rows, status pill cell renderer

### Dependencies added

Sprint 1: `sonner`, `date-fns`, `@radix-ui/*` (via shadcn), `lucide-react`.
Sprint 2: `react-day-picker`, `react-hook-form`, `zod`, `@hookform/resolvers`, `@axe-core/playwright`.
Sprint 3: `cmdk`, `framer-motion`.

---

*This plan is the single source of truth for the runtime UX overhaul. Any deviation must be recorded here with justification, or opened as a follow-up in [`docs/ACTIVE_TASKS.md`](../ACTIVE_TASKS.md).*
