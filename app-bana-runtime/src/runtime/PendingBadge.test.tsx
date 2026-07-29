/**
 * PendingBadge.test.tsx — Task C3.7.
 *
 * Covers the badge formatting helpers and the sidebar rendering that consumes
 * them.
 */
import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { RuntimeSidebar } from './RuntimeSidebar';
import { formatBadgeCount, totalPending, PENDING_POLL_MS } from './usePendingCounts';

describe('formatBadgeCount', () => {
  it('renders nothing at zero — an empty queue needs no badge', () => {
    expect(formatBadgeCount(0)).toBe('');
  });

  it('renders nothing for a negative count', () => {
    expect(formatBadgeCount(-3)).toBe('');
  });

  it('renders the exact figure up to 99', () => {
    expect(formatBadgeCount(1)).toBe('1');
    expect(formatBadgeCount(99)).toBe('99');
  });

  /** A four-digit queue would stretch the nav; the exact figure is on the page. */
  it('caps beyond 99 so a long queue cannot stretch the nav', () => {
    expect(formatBadgeCount(100)).toBe('99+');
    expect(formatBadgeCount(4213)).toBe('99+');
  });
});

describe('totalPending', () => {
  it('sums across entities', () => {
    expect(totalPending({ Invoice: 2, Payment: 3 })).toBe(5);
  });

  it('is zero for no entities', () => {
    expect(totalPending({})).toBe(0);
  });

  it('ignores non-finite values rather than yielding NaN', () => {
    expect(totalPending({ a: 1, b: NaN as unknown as number })).toBe(1);
  });
});

describe('polling interval', () => {
  /** C3.7 exit criterion: the badge updates within 30s of a submit. */
  it('is no slower than the 30s the exit criterion allows', () => {
    expect(PENDING_POLL_MS).toBeLessThanOrEqual(30_000);
  });
});

describe('RuntimeSidebar pending badge', () => {
  const base = {
    pages: [] as never,
    currentPageId: null,
    onSelect: () => {},
    checkerEntities: ['Invoice'],
    onSelectQueue: () => {},
  };

  it('shows the count beside the queue link', () => {
    const html = renderToStaticMarkup(
      <RuntimeSidebar {...base} pendingCounts={{ Invoice: 4 }} />
    );
    expect(html).toContain('appbana-nav-badge');
    expect(html).toContain('>4<');
  });

  it('omits the badge entirely when nothing is pending', () => {
    const html = renderToStaticMarkup(
      <RuntimeSidebar {...base} pendingCounts={{ Invoice: 0 }} />
    );
    expect(html).not.toContain('appbana-nav-badge');
  });

  it('omits the badge when no counts have loaded yet', () => {
    const html = renderToStaticMarkup(<RuntimeSidebar {...base} />);
    expect(html).not.toContain('appbana-nav-badge');
    expect(html).toContain('data-approval-queue-link="Invoice"');
  });

  /**
   * The pill is aria-hidden, so the count has to reach a screen reader through
   * the link's accessible name instead.
   */
  it('puts the count in the accessible name, not only in the pill', () => {
    const html = renderToStaticMarkup(
      <RuntimeSidebar {...base} pendingCounts={{ Invoice: 4 }} />
    );
    expect(html).toContain('aria-label="Invoices to review, 4 pending"');
  });

  it('leaves the accessible name unadorned when nothing is pending', () => {
    const html = renderToStaticMarkup(
      <RuntimeSidebar {...base} pendingCounts={{ Invoice: 0 }} />
    );
    expect(html).toContain('aria-label="Invoices to review"');
  });

  it('exposes the raw count for e2e assertions', () => {
    const html = renderToStaticMarkup(
      <RuntimeSidebar {...base} pendingCounts={{ Invoice: 7 }} />
    );
    expect(html).toContain('data-pending-count="7"');
  });

  it('caps the rendered badge but not the accessible name', () => {
    const html = renderToStaticMarkup(
      <RuntimeSidebar {...base} pendingCounts={{ Invoice: 250 }} />
    );
    expect(html).toContain('>99+<');
    expect(html).toContain('250 pending');
  });
});
