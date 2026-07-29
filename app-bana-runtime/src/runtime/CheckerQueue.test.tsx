/**
 * CheckerQueue.test.tsx — Tasks C3.3 / C3.4.
 *
 * Rendered with react-dom/server, matching the rest of this suite (no jsdom,
 * no @testing-library). That limits us to markup assertions, so the behavioural
 * logic that matters — role derivation, own-submission detection — is tested
 * through the pure helpers it lives in.
 */
import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import type { CurrentUser } from '@appbana/shared';
import { buildCurrentUserState } from './useCurrentUser';
import { RejectDialog } from './RejectDialog';
import { RuntimeSidebar } from './RuntimeSidebar';

const noop = () => {};

function user(entityRoles: CurrentUser['entityRoles']): CurrentUser {
  return { userId: 'u1', tenantId: 'default', appId: 'a1', entityRoles };
}

describe('buildCurrentUserState', () => {
  it('lists only entities the user may check', () => {
    const state = buildCurrentUserState(
      user({
        Invoice: { roles: ['checker'], isMaker: false, isChecker: true },
        Draft: { roles: ['maker'], isMaker: true, isChecker: false },
      }),
      false
    );
    expect(state.checkerEntities).toEqual(['Invoice']);
  });

  it('includes entities where the user holds BOTH', () => {
    const state = buildCurrentUserState(
      user({ Vendor: { roles: ['both', 'checker', 'maker'], isMaker: true, isChecker: true } }),
      false
    );
    expect(state.checkerEntities).toEqual(['Vendor']);
    expect(state.isChecker('Vendor')).toBe(true);
    expect(state.isMaker('Vendor')).toBe(true);
  });

  it('sorts entities so nav ordering is stable across reloads', () => {
    const state = buildCurrentUserState(
      user({
        Zebra: { roles: ['checker'], isMaker: false, isChecker: true },
        Apple: { roles: ['checker'], isMaker: false, isChecker: true },
      }),
      false
    );
    expect(state.checkerEntities).toEqual(['Apple', 'Zebra']);
  });

  it('matches entity names case-insensitively', () => {
    // Page metadata and role grants do not always agree on casing; a strict
    // match would silently hide a queue the user is entitled to.
    const state = buildCurrentUserState(
      user({ Invoice: { roles: ['checker'], isMaker: false, isChecker: true } }),
      false
    );
    expect(state.isChecker('invoice')).toBe(true);
    expect(state.isChecker('INVOICE')).toBe(true);
  });

  it('treats a failed lookup as holding no roles', () => {
    const state = buildCurrentUserState(null, false);
    expect(state.checkerEntities).toEqual([]);
    expect(state.isChecker('Invoice')).toBe(false);
    expect(state.isMaker('Invoice')).toBe(false);
  });

  it('reports nothing for an unknown entity', () => {
    const state = buildCurrentUserState(
      user({ Invoice: { roles: ['checker'], isMaker: false, isChecker: true } }),
      false
    );
    expect(state.isChecker('Nope')).toBe(false);
  });
});

describe('RuntimeSidebar — approvals section', () => {
  const pages = [{ id: 'p1', name: 'Invoice List', components: [] }] as never;

  it('is absent when the user checks nothing', () => {
    const html = renderToStaticMarkup(
      <RuntimeSidebar pages={pages} currentPageId={null} onSelect={noop} />
    );
    expect(html).not.toContain('Approvals');
  });

  it('lists a queue link per checkable entity', () => {
    const html = renderToStaticMarkup(
      <RuntimeSidebar
        pages={pages}
        currentPageId={null}
        onSelect={noop}
        checkerEntities={['Invoice', 'Vendor']}
        onSelectQueue={noop}
      />
    );
    expect(html).toContain('Approvals');
    expect(html).toContain('data-approval-queue-link="Invoice"');
    expect(html).toContain('data-approval-queue-link="Vendor"');
  });

  it('marks the open queue as the current nav item', () => {
    const html = renderToStaticMarkup(
      <RuntimeSidebar
        pages={pages}
        currentPageId={null}
        onSelect={noop}
        checkerEntities={['Invoice']}
        currentQueueEntity="Invoice"
        onSelectQueue={noop}
      />
    );
    expect(html).toContain('aria-current="page"');
    expect(html).toContain('appbana-sidebar-link-active');
  });

  it('stays hidden without a queue handler, so it can never be a dead link', () => {
    const html = renderToStaticMarkup(
      <RuntimeSidebar
        pages={pages}
        currentPageId={null}
        onSelect={noop}
        checkerEntities={['Invoice']}
      />
    );
    expect(html).not.toContain('Approvals');
  });
});

describe('RejectDialog', () => {
  it('asks for a reason, which the backend requires and the maker sees', () => {
    const html = renderToStaticMarkup(
      <RejectDialog open recordLabel="Invoice #7" onCancel={noop} onConfirm={noop} />
    );
    expect(html).toContain('Reject Invoice #7?');
    expect(html).toContain('Reason for rejection');
    expect(html).toContain('<textarea');
  });

  it('warns that the reason is permanent', () => {
    const html = renderToStaticMarkup(
      <RejectDialog open onCancel={noop} onConfirm={noop} />
    );
    expect(html).toContain('recorded');
  });

  it('renders reject in the danger variant', () => {
    const html = renderToStaticMarkup(
      <RejectDialog open onCancel={noop} onConfirm={noop} />
    );
    expect(html).toContain('appbana-btn-danger');
  });

  it('disables its actions while the request is in flight', () => {
    const html = renderToStaticMarkup(
      <RejectDialog open submitting onCancel={noop} onConfirm={noop} />
    );
    expect(html).toContain('Rejecting…');
    expect(html).toContain('disabled');
  });

  it('falls back to a generic heading without a record label', () => {
    const html = renderToStaticMarkup(
      <RejectDialog open onCancel={noop} onConfirm={noop} />
    );
    expect(html).toContain('Reject this record?');
  });
});
