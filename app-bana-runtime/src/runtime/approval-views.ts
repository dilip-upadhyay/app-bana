/**
 * approval-views.ts — Task C3.6.
 *
 * The system-provided saved views for an approval-required list page.
 *
 * These are synthesised rather than seeded into `appbana_saved_views`. Seeding
 * would mean writing a row per entity per app at schema-creation time, leaving
 * them to drift when the workflow changes and letting a user delete a view the
 * product depends on. Synthesised views cost nothing, cannot go stale and
 * cannot be removed by accident.
 *
 * They filter through `_approvalStatus`, the dedicated parameter, rather than a
 * bare `approval_status` field filter. Both doors reach the same predicate and
 * both are guarded (C2.7), but the dedicated one is validated explicitly and
 * forces the advanced query path, which is the only path that honours filters.
 *
 * C3.9 — the caller scoping on "Needs rework" is expressed as a `filter=`
 * clause. It used to be a bare `submitted_by` param, which the backend never
 * reads: the scoping was dropped in transit and every maker saw every other
 * maker's rejected records under a heading that said they were theirs. See
 * entity-query.ts.
 */
import type { SavedViewRecord } from '@appbana/shared';
import { exact } from './entity-query';

export const APPROVAL_VIEW_PREFIX = '__approval__';

/**
 * Build the system views for a maker.
 *
 * "Needs rework" is scoped to the caller: a REJECTED record must have been
 * submitted, so `submitted_by` reliably identifies whose it is.
 *
 * Drafts are deliberately *not* scoped, and deliberately not called "My
 * drafts". There is no `created_by` column on entity tables — the approval
 * machinery only records `submitted_by` — so a draft that has never been
 * submitted cannot be attributed to anyone. Filtering drafts by `submitted_by`
 * would silently hide every brand-new draft, which is the majority of them, and
 * labelling an unscoped list "mine" would be a lie the user cannot detect.
 */
export function buildApprovalSystemViews(userId: string | null | undefined): SavedViewRecord[] {
  const views: SavedViewRecord[] = [
    // C3.9 — "All" is the way back. Without it, selecting a view that returns
    // no rows left the user on an empty table with the filter still applied and
    // no affordance to clear it: FilterBar renders nothing when the page meta
    // declares no filters, which is the common case for scaffolded pages.
    {
      viewId: `${APPROVAL_VIEW_PREFIX}all`,
      name: 'All',
      view: { filters: {} },
    },
    {
      viewId: `${APPROVAL_VIEW_PREFIX}drafts`,
      name: 'Drafts',
      view: { filters: { _approvalStatus: 'DRAFT' } },
    },
  ];

  if (userId) {
    views.push({
      viewId: `${APPROVAL_VIEW_PREFIX}rework`,
      name: 'Needs rework',
      // `exact` matters here: string filters default to a substring match, so
      // scoping to the user "bob" without it would also return everything
      // submitted by "bobby".
      view: { filters: { _approvalStatus: 'REJECTED', submitted_by: exact(userId) } },
    });
  }

  return views;
}

/** True for a synthesised view, which must not offer a delete affordance. */
export function isSystemView(viewId: string): boolean {
  return viewId.startsWith(APPROVAL_VIEW_PREFIX);
}
