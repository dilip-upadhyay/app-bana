/**
 * ApprovalViews.test.tsx — Task C3.6.
 */
import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { buildApprovalSystemViews, isSystemView, APPROVAL_VIEW_PREFIX } from './approval-views';
import { SavedViewsBar } from './SavedViewsBar';

describe('buildApprovalSystemViews', () => {
  it('always offers a drafts view', () => {
    const views = buildApprovalSystemViews('alice');
    expect(views.map((v) => v.name)).toContain('Drafts');
  });

  it('scopes needs-rework to the caller', () => {
    const rework = buildApprovalSystemViews('alice').find((v) => v.name === 'Needs rework');
    expect(rework?.view.filters).toEqual({ _approvalStatus: 'REJECTED', submitted_by: 'alice' });
  });

  /**
   * A rejected record must have been submitted, so submitted_by identifies it.
   * A draft may never have been submitted and there is no created_by column, so
   * drafts cannot be attributed and must not claim to be.
   */
  it('does not scope drafts by submitter, and does not call them "mine"', () => {
    const drafts = buildApprovalSystemViews('alice').find((v) => v.name === 'Drafts');
    expect(drafts?.view.filters).toEqual({ _approvalStatus: 'DRAFT' });
    expect(drafts?.name.toLowerCase()).not.toContain('my');
  });

  it('omits needs-rework when the user is unknown, rather than showing everyone\'s', () => {
    expect(buildApprovalSystemViews(null).map((v) => v.name)).toEqual(['Drafts']);
    expect(buildApprovalSystemViews(undefined).map((v) => v.name)).toEqual(['Drafts']);
  });

  /**
   * `_approvalStatus` is validated explicitly and forces the advanced query
   * path, which is the only path that honours filters.
   */
  it('filters through the dedicated parameter, not a bare field filter', () => {
    for (const v of buildApprovalSystemViews('alice')) {
      expect(Object.keys(v.view.filters ?? {})).toContain('_approvalStatus');
      expect(Object.keys(v.view.filters ?? {})).not.toContain('approval_status');
    }
  });

  it('marks its views as system views', () => {
    for (const v of buildApprovalSystemViews('alice')) {
      expect(isSystemView(v.viewId)).toBe(true);
    }
  });

  it('does not treat a user-created view as a system view', () => {
    expect(isSystemView('4f0e-user-view')).toBe(false);
  });

  it('gives every view a distinct id', () => {
    const ids = buildApprovalSystemViews('alice').map((v) => v.viewId);
    expect(new Set(ids).size).toBe(ids.length);
    expect(ids.every((id) => id.startsWith(APPROVAL_VIEW_PREFIX))).toBe(true);
  });
});

describe('SavedViewsBar system views', () => {
  const base = {
    tenantId: 't',
    appId: 'a',
    entityKey: 't_a_Invoice',
    onSelect: () => {},
  };

  it('renders system views passed to it', () => {
    const html = renderToStaticMarkup(
      <SavedViewsBar {...base} systemViews={buildApprovalSystemViews('alice')} />
    );
    expect(html).toContain('Drafts');
    expect(html).toContain('Needs rework');
  });

  /** They are not the user's to remove, and a delete that reappeared on
   *  reload would be worse than no delete at all. */
  it('gives system views no delete affordance', () => {
    const html = renderToStaticMarkup(
      <SavedViewsBar {...base} systemViews={buildApprovalSystemViews('alice')} />
    );
    expect(html).not.toContain('Delete view Drafts');
  });

  it('marks the applied view as pressed', () => {
    const html = renderToStaticMarkup(
      <SavedViewsBar
        {...base}
        systemViews={buildApprovalSystemViews('alice')}
        activeViewId={`${APPROVAL_VIEW_PREFIX}drafts`}
      />
    );
    expect(html).toContain('aria-pressed="true"');
  });

  it('renders no system chips for a non-approval entity', () => {
    const html = renderToStaticMarkup(<SavedViewsBar {...base} />);
    expect(html).not.toContain('data-system-view');
    expect(html).not.toContain('Needs rework');
  });
});
