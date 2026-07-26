/**
 * Skeleton.test.ts — Sanity coverage for the loading-skeleton primitives
 * added in Sprint 2 Task 2.4. We render each variant with
 * react-dom/server (no jsdom needed) and assert on the emitted markup.
 */
import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import {
  Skeleton,
  TableSkeleton,
  FormSkeleton,
  AppLoadingSkeleton,
} from './Skeleton';

describe('Skeleton primitive', () => {
  it('renders a shimmer element with the appbana-skeleton class', () => {
    const html = renderToStaticMarkup(<Skeleton className="h-4 w-24" />);
    expect(html).toContain('appbana-skeleton');
    expect(html).toContain('h-4 w-24');
  });

  it('exposes a screen-reader label for assistive tech', () => {
    const html = renderToStaticMarkup(<Skeleton ariaLabel="Loading name" />);
    expect(html).toContain('aria-label="Loading name"');
    expect(html).toContain('role="status"');
  });

  it('defaults ariaLabel to "Loading"', () => {
    const html = renderToStaticMarkup(<Skeleton />);
    expect(html).toContain('aria-label="Loading"');
  });
});

describe('TableSkeleton', () => {
  it('renders one header row + five body rows by default', () => {
    const html = renderToStaticMarkup(<TableSkeleton columns={4} />);
    const rowMatches = html.match(/appbana-table-skeleton-row/g) ?? [];
    // 1 head + 5 body = 6 total rows
    expect(rowMatches).toHaveLength(6);
  });

  it('honours the rows prop', () => {
    const html = renderToStaticMarkup(<TableSkeleton columns={3} rows={2} />);
    const rowMatches = html.match(/appbana-table-skeleton-row/g) ?? [];
    // 1 head + 2 body = 3 total rows
    expect(rowMatches).toHaveLength(3);
  });

  it('renders a minimum of 3 columns even when fewer are requested', () => {
    const html = renderToStaticMarkup(<TableSkeleton columns={1} rows={1} />);
    // One row has 3 skeletons; header row has 3 skeletons — 6 total
    const cellMatches = html.match(/appbana-skeleton/g) ?? [];
    expect(cellMatches).toHaveLength(6);
  });

  it('marks itself aria-hidden so screen readers rely on the outer status region', () => {
    const html = renderToStaticMarkup(<TableSkeleton columns={4} />);
    expect(html).toContain('aria-hidden="true"');
  });
});

describe('FormSkeleton', () => {
  it('renders four label + input skeleton pairs by default', () => {
    const html = renderToStaticMarkup(<FormSkeleton />);
    const fieldMatches = html.match(/appbana-form-skeleton-field/g) ?? [];
    expect(fieldMatches).toHaveLength(4);
  });

  it('honours the fields prop', () => {
    const html = renderToStaticMarkup(<FormSkeleton fields={2} />);
    const fieldMatches = html.match(/appbana-form-skeleton-field/g) ?? [];
    expect(fieldMatches).toHaveLength(2);
    // Each field should have exactly two skeletons (label + input).
    const skels = html.match(/appbana-skeleton/g) ?? [];
    expect(skels).toHaveLength(4);
  });
});

describe('AppLoadingSkeleton', () => {
  it('renders the shell (appbar + sidebar + main)', () => {
    const html = renderToStaticMarkup(<AppLoadingSkeleton />);
    expect(html).toContain('appbana-app-skeleton-bar');
    expect(html).toContain('appbana-app-skeleton-side');
    expect(html).toContain('appbana-app-skeleton-main');
  });

  it('announces itself as a loading region', () => {
    const html = renderToStaticMarkup(<AppLoadingSkeleton />);
    expect(html).toContain('role="status"');
    expect(html).toContain('aria-label="Loading app"');
  });
});
