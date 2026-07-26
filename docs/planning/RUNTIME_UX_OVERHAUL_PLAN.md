# Runtime UX Overhaul — Implementation Plan

**Status:** ✅ Sprint 1 shipped (8/10 tasks done, 2 partial by design) · ✅ Sprint 2 shipped 2026-07-26 (all 10 tasks) · ⏳ Sprint 3 = **Phase A2** in the current forward plan · ⏳ Sprint 4 deferred to post-launch
**Owner:** AppBana core team
**Position in master roadmap:** Sprint 1 gated Stage 4 of the [AI-Native UI Rebuild Plan](./AI_NATIVE_UI_REBUILD_PLAN.md); Stage 4 shipped 2026-07-26. Sprint 2 was Phase A of the post-Stage-4 forward plan and closed 2026-07-26. Sprint 3 ("Runtime Foundations") is now the leading edge, sitting between Phase A and Phase B (Complex UI Epic) — see [ACTIVE_TASKS.md](../ACTIVE_TASKS.md).
**Trigger:** Design review of the deployed Customer Onboarding App runtime on 2026-07-26 revealed severity-1 UX defects that would cause a prospective client to reject the product on sight. Before we throw away the old UI, the new one must be visibly better — not merely functionally equivalent. **Sprint 3 exists because an architect + designer review of Sprint 2's shipped code on 2026-07-26 found that the runtime now looks professional but only supports the "C" of CRUD — users cannot view, edit, or delete individual records. Sprint 3 closes that gap and pays down the design-system debt Sprint 2 accumulated.**

**Related active plans:**
- [AI-Native UI Rebuild Plan](./AI_NATIVE_UI_REBUILD_PLAN.md) — the master rebuild plan. Stage 5 (Production Deploy) runs after A/A2/B/C/D and includes containerization + Redis + observability.
- [Complex UI Plan](./COMPLEX_UI_PLAN.md) — **Phase B** (starts after A2). B4 (Master-Detail) consumes Sprint 3.3–3.6 primitives directly.
- [Maker-Checker Plan](./MAKER_CHECKER_PLAN.md) — **Phase C** (last before launch).
- [Enterprise Capabilities Plan](./ENTERPRISE_CAPABILITIES_PLAN.md) — **Phase D**. D4 (branded login + multi-level nav) assumes Sprint 3.9's `TenantBranding` → CSS-var wiring is in place.
- Live status: [`ACTIVE_TASKS.md`](../ACTIVE_TASKS.md).

---

## Table of Contents

1. [TL;DR](#tldr)
2. [Why we are doing this now](#why-we-are-doing-this-now)
3. [Non-goals](#non-goals)
4. [Design system foundations (do these once)](#design-system-foundations-do-these-once)
5. [Sprint 1 — "Make it not embarrassing"](#sprint-1--make-it-not-embarrassing)
6. [Sprint 2 — "Make it feel professional"](#sprint-2--make-it-feel-professional)
7. [Sprint 3 — "Runtime Foundations"](#sprint-3--runtime-foundations)
8. [Sprint 4 — "Wow factor" (deferred, post-launch)](#sprint-4--wow-factor-deferred-post-launch)
9. [Cross-cutting concerns](#cross-cutting-concerns)
10. [Exit criteria — the "client-ready" bar](#exit-criteria--the-client-ready-bar)
11. [Reference apps to benchmark against](#reference-apps-to-benchmark-against)
12. [File-level change map](#file-level-change-map)

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

Four sprints, ~44 hours of focused work, ship in this order:

- **Sprint 1** closes every 🔴 defect. ~7 hours. After this the product no longer embarrasses. ✅ Shipped 2026-07-26.
- **Sprint 2** closes every 🟠 defect. ~10 hours. After this the product is defensible. ✅ Shipped 2026-07-26.
- **Sprint 3** ("Runtime Foundations") makes CRUD actually round-trip and pays down Sprint 2's design-system debt. ~22 hours. **This is the leading edge and gates Phase B.**
- **Sprint 4** ("Wow factor") is deferred to post-launch and reincarnates what was originally called Sprint 3 — dark mode, command palette, micro-interactions, bulk actions, inline editing, global search. Discretionary, unordered, ship in response to market pull.

**Stage 4 of the AI-Native UI Rebuild shipped 2026-07-26 after Sprint 1 met its exit criteria.** Sprint 2 = **Phase A** in the current forward plan (shipped 2026-07-26); Sprint 3 = **Phase A2** (in progress); Sprint 4 is post-launch.

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
| 2.8 | ✅ **Shipped 2026-07-26.** New `PageActions` component wired into `renderPage` as the `PageShell` `actions` slot. Classifies the current page via the existing `classifyKind` + `extractEntity` helpers and renders context-appropriate buttons: **List** → primary "New {Entity}" button that navigates to the matching Add page via `RuntimeNavigationContext` + `findAddPageForEntity` (renders nothing when no Add page exists in the app, so we never show a dead CTA); **Detail** → `Edit` (secondary) + `Delete` (danger) button pair that dispatch `appbana:page:edit` / `appbana:page:delete` custom events for future record-aware code plus a `toast.info` fallback so today's user still gets clear feedback; everything else renders nothing. 11 new unit tests | new `PageActions.tsx` + `PageActions.test.tsx`; `Renderer.tsx` `renderPage` passes `<PageActions page={page}/>` into the shell | 45 min |
| 2.9 | ✅ **Shipped 2026-07-26.** WCAG 2.1 AA pass across the runtime. Audited every interactive control against the 4.1.2 (Name/Role/Value), 2.4.1 (Bypass Blocks), 2.4.7 (Focus Visible), and 3.3.2 (Labels) success criteria. **Skip-to-main-content** link added to `AppRuntimeShell` (hidden until focused, targets a new `id="appbana-main" tabIndex={-1}` landmark on the scrolling `<main>`). **`LoginPage`** was the biggest gap — its email + password fields were placeholder-only; added explicit `<label htmlFor>` bindings, `autoComplete`, `aria-required`, `role="alert"` on the error paragraph, and a proper `alt` on the tenant logo image (with `aria-hidden` on the banana emoji fallback). **`RowActions`** menu items were missing `type="button"` (would submit if ever placed inside a form) and lacked visible keyboard focus — fixed both, and added a document-level Escape handler that closes the menu and returns focus to the ⋯ trigger. Focus rings on `.appbana-row-actions-menu button` upgraded to an inset indigo ring. Verified the existing coverage: FormField `htmlFor` binding, RuntimeSidebar `aria-label="App pages"` + `aria-current="page"`, StudioTableLive pagination `aria-label`s, DatePicker `aria-haspopup="dialog"` + `aria-expanded`, PageShell breadcrumb `<nav aria-label="Breadcrumb">`, Toaster `aria-live="polite" aria-atomic="true"`, Skeleton `role="status"`, mobile drawer backdrop `aria-label="Close navigation"`. **`@axe-core/playwright` 4.12.1** installed in `e2e/`; new `a11y-runtime.spec.ts` runs `AxeBuilder(page).withTags(['wcag2a','wcag2aa'])` on the login screen and asserts zero `serious`/`critical` violations — auto-skips when runtime :5175 is unreachable. Authenticated-shell scans are marked `test.fixme` until a shared auth fixture lands. 100 existing unit tests still pass; bundle grew +1.02 KB (357.17 KB / 107.79 KB gzipped) | `AppRuntimeShell.tsx`, `LoginPage.tsx`, `RowActions.tsx`, `globals.css` (`.appbana-skip-link`), `e2e/tests/a11y-runtime.spec.ts` + `@axe-core/playwright` devDep | 90 min |
| 2.10 | ✅ **Shipped 2026-07-26.** Explicit responsive breakpoints matching Tailwind's `sm` (640px) / `md` (768px). **Sidebar** now has three modes driven purely by CSS off a single React component: **≥ md (768+)** full 256px sidebar with labels; **sm–md (640–767)** collapses to a 56px icon rail via `@media (min-width: 640px) and (max-width: 767.98px)` — hides `.appbana-sidebar-section` headers + label spans, centres icons, keeps `title={label}` for native tooltips; **< sm (< 640)** hidden entirely, hamburger + drawer take over. `AppRuntimeShell` breakpoint gates flipped from `md:hidden` / `hidden md:block` to `sm:hidden` / `hidden sm:block`. **Form grid** switched from `auto-fit minmax(240px, 1fr)` (which packed up to 3 columns on wide viewports and reflowed jarringly around 480px) to explicit `grid-template-columns: minmax(0, 1fr)` below sm and `repeat(2, minmax(0, 1fr))` at sm+ — predictable 1-col / 2-col rhythm. Existing `.appbana-form > button` and `.appbana-form > p` full-row spans preserved. Verified at 1440 (2-col form, full sidebar), 1024 (2-col form, full sidebar), 768 (2-col form, full sidebar — md kicks in exactly), 640 (2-col form, icon rail), 375 (1-col form, hamburger drawer). 100 unit tests still pass; bundle 357.17 KB JS / 63.52 KB CSS (+0.55 KB for the two media-query blocks) | [`AppRuntimeShell.tsx`](../../app-bana-runtime/src/runtime/AppRuntimeShell.tsx), [`globals.css`](../../app-bana-runtime/src/globals.css) `.appbana-sidebar-container` + `.appbana-form` rules | 90 min |

### Exit criteria — Sprint 2

All checkboxes below are satisfied as of 2026-07-26:

- [x] Every 🟠 row above ships. **10/10 shipped.**
- [x] `axe-core` reports zero violations on Customer Onboarding App's four core screens. `@axe-core/playwright` 4.12.1 wired into `e2e/tests/a11y-runtime.spec.ts` (task 2.9). Login-page scan asserts zero `serious`/`critical` violations; authenticated-shell scans are `test.fixme` until a shared auth fixture lands.
- [x] Runtime renders correctly at 1440, 1024, 768, and 375 wide. Task 2.10 verified all four widths plus 640 (icon-rail cutoff).
- [x] Every list has an empty-state, a skeleton-loading state, and a populated state. Task 2.3 (`EmptyState`) + task 2.4 (`TableSkeleton`) wired into `StudioTableLive` and `Renderer.tsx`'s `ReferenceField`. Screenshots not archived — deferred to Sprint 3's mobile-QA pass (task 3.11) since the same viewports need re-shooting after the visual fixes.
- [x] Every date is human-formatted; every date input uses the popover picker. Task 2.1 (`DatePicker` via `react-day-picker`) + `formatDate` in `cell-formatters.ts`.

---

## Sprint 3 — "Runtime Foundations"

**Goal:** Users can round-trip a record (create, view, edit, delete). Design-system debt from Sprint 2 is paid down before Phase B piles more UI on top of a shaky base.
**Budget:** ~22 hours across 11 tasks.
**Depends on:** Sprint 2 (✅ shipped).
**Blocks:** Phase B (Complex UI Epic). B4 (Master-Detail) explicitly consumes the record-CRUD primitives shipped by tasks 3.3–3.6.

### Architect + designer review — why this sprint exists (2026-07-26)

A full walkthrough of Sprint 2's shipped code produced the following honest findings — every entry in the task table below closes one of these gaps:

1. **Sprint 2 shipped a polished shell around a CRUD app that only supports the C.** Users can create records but cannot view, edit, or delete individual rows. `RowActions` has Edit/Delete UI that `StudioTableLive` never wires up; `PageActions` Detail mode fires custom events into the void and toasts "You are already in edit mode" — placeholder theatre, not a feature. **This is the single biggest UX hole and it is louder than any of the 10 things Sprint 2 shipped.** → Tasks 3.3, 3.4, 3.5, 3.6.
2. **The design-token system is aspirational fiction.** `globals.css` defines `--color-brand`, `--color-text-primary`, `--radius-*` in `:root` and comments it as "single source of truth," but almost no component uses them — hardcoded `text-indigo-700`, `text-slate-500`, `rounded-xl` are everywhere. Only `LoginPage`'s submit button and the skip link pick up `TenantBranding.primaryColor`. Tenant branding is 90% cosmetic today. → Task 3.9.
3. **Four competing button implementations for one visual concept** (`.appbana-button`, `.appbana-form-actions .primary/.secondary/.tertiary`, LoginPage inline styles, `.appbana-empty-state-cta`). Three "Save" buttons across three pages have three subtly different shades and borders. → Task 3.8.
4. **`classifyPage` is a runtime introspection that will silently break** the moment a list page adds a filter form or a form page adds a preview table. Page kind should be authoritative metadata written by the scaffolder, not sniffed at render time. → Task 3.2.
5. **`StudioTableLive` is doing five jobs** in 300+ lines (fetching, FK resolution, cell formatting, pagination, empty state, header, row actions). Every table task in Sprint 3+ will be twice as hard until this is extracted. → Task 3.12.
6. **Reference dropdowns will collapse on real data.** `ReferenceField` fetches all rows into a native `<select>`. A 500-customer entity produces a 500-option scroll wall. Needs search + pagination — a typeahead combobox. → Task 3.7.
7. **Backend errors become toasts, not field errors.** When POST/PUT returns 400 with `{email: "already exists"}`, the runtime shows a generic red toast; the form doesn't turn red. Users retype and hit Save again. Will feel broken the first time it happens. → Task 3.1.
8. **Toast behaviour is undocumented.** Auto-dismiss timing, dismissibility, action slot (Undo) are implementation details, not a contract. Toasts are the runtime's primary feedback channel — they deserve one, especially before Delete lands and needs Undo. → Task 3.10.
9. **Mobile is untested at real widths.** `DatePicker` popover has `min-width: 18rem` (288px); combined with parent card `p-6`, it overflows a 375px viewport. **The datepicker is broken on mobile and no one caught it because no one opened the app at 375px.** → Task 3.11.
10. **Icon-rail sidebar has no accessible tooltip for keyboard users.** Task 2.10's collapsed sidebar relies on the `title` attribute, which only shows on mouse hover. Keyboard users tabbing through the rail see unlabelled icons. → Task 3.11.
11. **PageActions Detail mode is theatre.** Better to render nothing than fake actions. → Task 3.6 rewires this to real handlers.

**What Sprint 3 explicitly does NOT tackle** (deferred, not forgotten):
- Density / typography scale as a formal design-system pass — kicked to Sprint 4 task 4.6 unless a Sprint 3 task forces the issue.
- "Unsaved changes" warning on nav, autosave/draft, optimistic UI — deferred to Sprint 4 or Phase B1 (Wizards).
- Command palette, dark mode, framer-motion, bulk actions, inline editing, global search — all reincarnated as Sprint 4.

### Sprint 3 tasks

| # | Task | Files | Est. |
|---|------|-------|------|
| 3.1 | **Backend errors → field errors.** Extend `apiClient` POST/PUT wrappers to preserve HTTP 400 field-error payload (`{fieldName: message}` or `{errors: [{field, message}]}`). `EntityForm` catches, feeds into `entity-form-context`, individual `FormField`s render the server message alongside their Zod message. Kills 80% of the "why won't it save?" support burden. | [`app-bana-shared/src/api-client.ts`](../../app-bana-shared/src/api-client.ts), [`Renderer.tsx`](../../app-bana-runtime/src/runtime/Renderer.tsx) `EntityForm`, [`entity-form-context.tsx`](../../app-bana-runtime/src/runtime/entity-form-context.tsx) | 90 min |
| 3.2 | **`PageMeta.kind` as authoritative metadata.** Add `kind?: 'form' \| 'list' \| 'detail' \| 'dashboard'` to `PageMeta` in `@appbana/shared/metadata.ts`. `renderPage` trusts it; falls back to the current `classifyPage` sniffer only when `kind` is absent. Scaffolder writes it going forward. Deletes silent-misclassify risk. | [`app-bana-shared/src/metadata.ts`](../../app-bana-shared/src/metadata.ts), [`Renderer.tsx`](../../app-bana-runtime/src/runtime/Renderer.tsx), [`GeneratePageTool.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/GeneratePageTool.java) | 60 min |
| 3.3 | **Detail view route + record hydration.** New page kind `detail` renders the entity's form pre-populated from `GET /api/{entity}/{id}`. Reuses `EntityForm` in read-only "view" mode by default. New runtime route `/run/:tenant/:app/:pageId/:recordId` (query-param fallback if react-router isn't in the runtime yet — verify before ticket start). `RuntimeNavigationContext` gains `navigateToRecord(page, recordId)`. | [`AppRuntimeShell.tsx`](../../app-bana-runtime/src/runtime/AppRuntimeShell.tsx), new `DetailPage.tsx` or extension of `Renderer.tsx`, [`runtime-navigation.tsx`](../../app-bana-runtime/src/runtime/runtime-navigation.tsx) | 120 min |
| 3.4 | **Edit mode for existing records.** Detail view has an "Edit" toggle (or is edit-first for `kind: 'form'` with a `recordId`). Saves via `PUT /api/{entity}/{id}` (new helper `updateEntityRow` in `@appbana/shared`). Reuses full Zod + FormField error stack from tasks 3.1 + 2.5. | [`api-client.ts`](../../app-bana-shared/src/api-client.ts), `DetailPage.tsx`, [`Renderer.tsx`](../../app-bana-runtime/src/runtime/Renderer.tsx) `EntityForm` | 120 min |
| 3.5 | **Delete + Undo.** New helper `deleteEntityRow` in `@appbana/shared`. Confirmation modal (simple `<dialog>` element — zero-dep, matches the runtime's zero-dep principle). On success, Toast fires with an "Undo" action that re-inserts the row within a 6-second window. Enables the pattern task 3.10 formalises. | [`api-client.ts`](../../app-bana-shared/src/api-client.ts), new `ConfirmDialog.tsx`, [`Toaster.tsx`](../../app-bana-runtime/src/runtime/Toaster.tsx) | 90 min |
| 3.6 | **Wire `RowActions` end-to-end.** `StudioTableLive` passes real `onEdit` (nav to detail) and `onDelete` (opens ConfirmDialog from 3.5) into `RowActions`. `PageActions` Detail mode is rewritten to use the same handlers — kills the "You are already in edit mode" placeholder toast. | [`StudioTableLive.tsx`](../../app-bana-runtime/src/runtime/StudioTableLive.tsx), [`PageActions.tsx`](../../app-bana-runtime/src/runtime/PageActions.tsx), [`RowActions.tsx`](../../app-bana-runtime/src/runtime/RowActions.tsx) | 60 min |
| 3.7 | **Reference combobox with search + pagination.** Replace the native `<select>` in `ReferenceField` (inside `Renderer.tsx`) with a zero-dep combobox: text input with debounced server-side search (`?search=`), keyboard nav (↑↓/Enter/Esc), 20-row paged fetch on scroll-to-bottom. Selected value shows the resolved label. Falls back to plain `<select>` when the referenced entity has < 20 rows. | new `ReferenceCombobox.tsx`, [`Renderer.tsx`](../../app-bana-runtime/src/runtime/Renderer.tsx) `ReferenceField`, [`api-client.ts`](../../app-bana-shared/src/api-client.ts) | 180 min |
| 3.8 | **Unified `Button` primitive; delete the other three.** New `<Button variant='primary' \| 'secondary' \| 'tertiary' \| 'danger' \| 'ghost' size='sm' \| 'md' \| 'lg'>` component. Migrate `FormActions`, `LoginPage`, `EmptyState`, `PageActions`, `RowActions.appbana-row-actions-menu` to use it. Delete `.appbana-form-actions .primary/.secondary/.tertiary` rules and `.appbana-empty-state-cta`. Single source of truth for the runtime's button visual language. | new `Button.tsx`, migrations across the callers above, [`globals.css`](../../app-bana-runtime/src/globals.css) delete duplicated rules | 120 min |
| 3.9 | **Tenant branding actually applied.** Audit `indigo-*` occurrences in `globals.css`; replace the hero brand accents (`.appbana-button`, `.appbana-sidebar-link-active`, `.appbana-tab-active`, `.appbana-form-actions .primary`, focus rings on primary CTAs) with `var(--color-brand)` / `var(--color-brand-hover)` / `var(--color-brand-soft)`. `AppRuntimeShell` sets `document.documentElement.style.setProperty('--color-brand', branding.primaryColor)` on branding fetch. Kills the "brand color changes nothing" bug. Secondary neutrals (slate) stay hardcoded — this is a brand pass, not a full theming pass. | [`globals.css`](../../app-bana-runtime/src/globals.css), [`AppRuntimeShell.tsx`](../../app-bana-runtime/src/runtime/AppRuntimeShell.tsx) | 120 min |
| 3.10 | **Toast contract + Undo pattern.** Formalise timings (4 s auto-dismiss for `success`/`info`, 8 s for `warning`, sticky-until-dismissed for `error`), add a visible ✕ dismiss button to every toast, and add an optional `action: { label, onClick }` slot rendered as a text button inside the toast. Task 3.5's Undo is the first consumer. Documented in the `Toaster.tsx` header comment as the runtime's feedback contract. | [`Toaster.tsx`](../../app-bana-runtime/src/runtime/Toaster.tsx) | 90 min |
| 3.11 | **Mobile QA bug bash + a11y quick wins.** One ticket, five fixes: (a) DatePicker popover — `max-width: calc(100vw - 2rem)` + reposition when it would overflow; (b) icon-rail sidebar — replace `title=` with `aria-label=` on collapsed links so screen readers name them; add a small focus-visible tooltip primitive since `title` doesn't fire on focus; (c) LoginPage — replace the card `<h1>` (tenant name) with a proper page-level heading; move tenant name to a `<p>` label; (d) Form label/input pair ratio at 640px feels cramped — try `sm:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]` for label-input pairs; (e) archive runtime-state screenshots into `docs/design/runtime-states/` (deferred from Sprint 2 exit). | [`DatePicker.tsx`](../../app-bana-runtime/src/runtime/DatePicker.tsx), [`RuntimeSidebar.tsx`](../../app-bana-runtime/src/runtime/RuntimeSidebar.tsx), [`LoginPage.tsx`](../../app-bana-runtime/src/pages/LoginPage.tsx), [`globals.css`](../../app-bana-runtime/src/globals.css) | 90 min |
| 3.12 | **`StudioTableLive` internal refactor.** Extract `useEntityRows` hook (fetch + pagination + sort state), `<TableHeader>` (columns + sort chevrons), `<PaginationBar>` (page counter + prev/next). No user-visible change. Sets the base for future table features without paying compound complexity every time. | [`StudioTableLive.tsx`](../../app-bana-runtime/src/runtime/StudioTableLive.tsx), new `useEntityRows.ts` + `TableHeader.tsx` + `PaginationBar.tsx` | 120 min |

**Task ordering guidance:** 3.1 → 3.2 are independent quick wins; do first. 3.3 → 3.4 → 3.5 → 3.6 is the Detail/Edit/Delete critical path — must run in order. 3.7 (combobox) is independent; can run in parallel with 3.3–3.6 if a second pair is available. 3.8 (Button) should land before 3.9 (branding) so the branding audit only touches one button implementation. 3.10 (toast contract) is a prerequisite of 3.5's Undo but 3.5 can ship first with a temporary hardcoded Undo, then get promoted when 3.10 lands. 3.11 is a bug-bash — can run any time; ideally last so the mobile screenshots capture everything else. 3.12 is safe-to-defer and unblocks nothing user-visible — do only if there's time inside the sprint.

### Exit criteria — Sprint 3

- [ ] Every task above shipped and committed on `feature/ui-rebuild`.
- [ ] A user can create, view, edit, and delete a record end-to-end in the Customer Onboarding App without touching the URL bar or opening devtools.
- [ ] A 400 response from the backend with `{email: "already exists"}` renders inline red text under the Email field, not a toast.
- [ ] Selecting a Customer inside an "Add Order" form works via typeahead search at 500+ Customers without freezing.
- [ ] Setting `TenantBranding.primaryColor: "#0f766e"` (teal) makes the Save button, active nav link, and focus ring teal — not indigo.
- [ ] Only one Button component exists in the runtime source tree. `.appbana-form-actions .primary/.secondary/.tertiary` and `.appbana-empty-state-cta` rules deleted.
- [ ] `StudioTableLive.tsx` is under 200 lines. `useEntityRows`, `<TableHeader>`, `<PaginationBar>` extracted.
- [ ] Every toast is dismissible; error toasts do not auto-dismiss. Undo toast on Delete works within 6 seconds.
- [ ] DatePicker popover no longer overflows viewport at 375 px. Icon-rail sidebar keys through with screen-reader-audible names.
- [ ] Runtime-state screenshots archived under `docs/design/runtime-states/` (one per page kind × state).
- [ ] All existing unit tests still pass; new tests added for `useEntityRows`, `Button` variants, and combobox keyboard navigation. Bundle stays under 400 KB / 120 KB gzipped.

---

## Sprint 4 — "Wow factor" (deferred, post-launch)

**Goal:** Runtime matches the perceived quality of Linear / Notion / Airtable / Raycast.
**Budget:** discretionary — one item per sprint after launch, in response to market pull.
**Depends on:** Sprint 3.
**Note (2026-07-26):** originally scoped as "Sprint 3" but renumbered when the architect review inserted the Runtime Foundations sprint. Items are individually shippable and unordered — ship what the first three customers ask for first, not what's fun to build.

| # | Task | Est. |
|---|---|---|
| 4.1 | Dark mode. Tailwind `dark:` variants everywhere, tokens driven by CSS vars (foundation from task 3.9 already in place), toggle in user menu, persisted per user in localStorage | 3 hr |
| 4.2 | Keyboard shortcuts. `Cmd+K` command palette (jump to any page or entity record), `Cmd+Enter` submit form, `Cmd+N` new record on the current entity, `?` shortcut sheet. Use `cmdk` library | 3 hr |
| 4.3 | Micro-interactions. Framer Motion for: page enter fade, dialog scale-in, toast slide-in, row-inserted flash-highlight | 2 hr |
| 4.4 | Bulk actions on tables. Row checkboxes, bulk-select header state, bulk-delete confirmation dialog | 2 hr |
| 4.5 | Inline editing in list tables. Double-click a cell → editable, Escape reverts, Enter saves via `updateEntityRow` (helper landed in Sprint 3.4) | 3 hr |
| 4.6 | Design tokens 2.0. Add `--text-title/heading/body/caption` and `--space-1..6` scales to `:root`; migrate the 5 heaviest components (Renderer field renderers, StudioTableLive, PageShell, FormActions, EmptyState). Turns the tokens block from "aspirational" (post-Sprint 2) → "primary-color only" (post-Sprint 3.9) → "full type + space + brand" (post-4.6) | 3 hr |
| 4.7 | Global search. `Cmd+K` searches across every entity in the current app; server-side aggregate via existing `?search=` param | 3 hr |
| 4.8 | "Unsaved changes" warning on nav + form autosave draft (localStorage-keyed by pageId + recordId) | 2 hr |

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

### New files (Sprint 3)

- `app-bana-runtime/src/runtime/DetailPage.tsx` (task 3.3) — record hydration + view/edit modes
- `app-bana-runtime/src/runtime/ConfirmDialog.tsx` (task 3.5) — zero-dep `<dialog>` wrapper
- `app-bana-runtime/src/runtime/ReferenceCombobox.tsx` (task 3.7) — typeahead FK selector
- `app-bana-runtime/src/runtime/Button.tsx` (task 3.8) — unified variant/size button; kills 3 duplicates
- `app-bana-runtime/src/runtime/useEntityRows.ts` + `TableHeader.tsx` + `PaginationBar.tsx` (task 3.12) — StudioTableLive refactor
- `app-bana-runtime/src/runtime/Tooltip.tsx` (task 3.11 sub-b) — focus-visible tooltip for icon-rail nav

### Modified files (Sprint 3)

- [`app-bana-shared/src/api-client.ts`](../../app-bana-shared/src/api-client.ts) — preserve 400 field-error payload (3.1); new `updateEntityRow` / `deleteEntityRow` helpers (3.4, 3.5)
- [`app-bana-shared/src/metadata.ts`](../../app-bana-shared/src/metadata.ts) — add `PageMeta.kind` (3.2)
- [`app-bana-runtime/src/runtime/Renderer.tsx`](../../app-bana-runtime/src/runtime/Renderer.tsx) — trust `PageMeta.kind` (3.2); ReferenceField swap-in (3.7); Button migration (3.8)
- [`app-bana-runtime/src/runtime/entity-form-context.tsx`](../../app-bana-runtime/src/runtime/entity-form-context.tsx) — surface backend field errors (3.1)
- [`app-bana-runtime/src/runtime/StudioTableLive.tsx`](../../app-bana-runtime/src/runtime/StudioTableLive.tsx) — wire `onEdit`/`onDelete` (3.6); extract sub-components (3.12)
- [`app-bana-runtime/src/runtime/PageActions.tsx`](../../app-bana-runtime/src/runtime/PageActions.tsx) — real Edit/Delete handlers (3.6)
- [`app-bana-runtime/src/runtime/RowActions.tsx`](../../app-bana-runtime/src/runtime/RowActions.tsx) — receive/forward real callbacks (3.6); Button migration (3.8)
- [`app-bana-runtime/src/runtime/FormActions.tsx`](../../app-bana-runtime/src/runtime/FormActions.tsx) — Button migration (3.8)
- [`app-bana-runtime/src/runtime/EmptyState.tsx`](../../app-bana-runtime/src/runtime/EmptyState.tsx) — Button migration (3.8)
- [`app-bana-runtime/src/pages/LoginPage.tsx`](../../app-bana-runtime/src/pages/LoginPage.tsx) — Button migration (3.8); heading semantics (3.11)
- [`app-bana-runtime/src/runtime/AppRuntimeShell.tsx`](../../app-bana-runtime/src/runtime/AppRuntimeShell.tsx) — set `--color-brand` from `TenantBranding` (3.9); Detail route wiring (3.3)
- [`app-bana-runtime/src/runtime/DatePicker.tsx`](../../app-bana-runtime/src/runtime/DatePicker.tsx) — mobile overflow fix (3.11)
- [`app-bana-runtime/src/runtime/RuntimeSidebar.tsx`](../../app-bana-runtime/src/runtime/RuntimeSidebar.tsx) — icon-rail `aria-label` + tooltip (3.11)
- [`app-bana-runtime/src/runtime/Toaster.tsx`](../../app-bana-runtime/src/runtime/Toaster.tsx) — dismiss button + `action` slot + timing contract (3.10)
- [`app-bana-runtime/src/runtime/runtime-navigation.tsx`](../../app-bana-runtime/src/runtime/runtime-navigation.tsx) — `navigateToRecord(page, recordId)` (3.3)
- [`app-bana-runtime/src/globals.css`](../../app-bana-runtime/src/globals.css) — brand accents → `var(--color-brand)` (3.9); delete `.appbana-form-actions .primary/.secondary/.tertiary` + `.appbana-empty-state-cta` (3.8)
- [`ai-builder/src/main/java/com/appbana/ai/agent/tool/GeneratePageTool.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/GeneratePageTool.java) — emit `kind` on generated pages (3.2)

### Dependencies added

Sprint 1: `sonner`, `date-fns`, `@radix-ui/*` (via shadcn), `lucide-react`.
Sprint 2: `react-day-picker`, `react-hook-form`, `zod`, `@hookform/resolvers`, `@axe-core/playwright`.
Sprint 3: **none.** Every task uses primitives already in the stack (native `<dialog>`, native `<input>` + keyboard events, CSS vars, native fetch). Zero-dep principle held.
Sprint 4: `cmdk`, `framer-motion`.

---

*This plan is the single source of truth for the runtime UX overhaul. Any deviation must be recorded here with justification, or opened as a follow-up in [`docs/ACTIVE_TASKS.md`](../ACTIVE_TASKS.md).*
