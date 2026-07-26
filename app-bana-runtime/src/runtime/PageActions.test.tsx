/**
 * PageActions.test.tsx — Sprint 2 Task 2.8.
 *
 * We exercise the pure `classifyPageActions` helper and the rendered markup
 * for each of the three branches (list / detail / other) via
 * react-dom/server. Interactive callbacks (onClick → navigateToPage,
 * onClick → dispatchEvent + toast) are verified manually and by e2e; unit
 * tests stay pure to honour the no-jsdom rule.
 */
import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import type { PageMeta } from '@appbana/shared';
import { PageActions, classifyPageActions } from './PageActions';
import { RuntimeNavigationProvider } from './runtime-navigation';

function page(id: string, name: string): PageMeta {
  return { id, name, rootId: 'root', nodes: [{ id: 'root', type: 'div', props: {} }] } as PageMeta;
}

describe('classifyPageActions', () => {
  it('detects list pages and extracts the entity', () => {
    expect(classifyPageActions(page('p1', 'Customer List'))).toEqual({
      kind: 'list', entity: 'Customer',
    });
    expect(classifyPageActions(page('p2', 'Orders List'))).toEqual({
      kind: 'list', entity: 'Orders',
    });
  });

  it('detects detail pages and extracts the entity', () => {
    expect(classifyPageActions(page('p1', 'Customer Detail'))).toEqual({
      kind: 'detail', entity: 'Customer',
    });
    expect(classifyPageActions(page('p2', 'User Profile'))).toEqual({
      kind: 'detail', entity: 'User',
    });
  });

  it('reports other for pages that have no action affordance', () => {
    expect(classifyPageActions(page('p1', 'Dashboard'))).toEqual({
      kind: 'dashboard', entity: null,
    });
    expect(classifyPageActions(page('p2', 'Settings'))).toEqual({
      kind: 'settings', entity: null,
    });
  });
});

describe('PageActions (list branch)', () => {
  it('renders a "New {Entity}" button that references the Add page', () => {
    const listPage = page('p1', 'Customer List');
    const addPage = page('p2', 'Add Customer');
    const html = renderToStaticMarkup(
      <RuntimeNavigationProvider pages={[listPage, addPage]} navigateToPage={() => {}}>
        <PageActions page={listPage} />
      </RuntimeNavigationProvider>,
    );
    expect(html).toContain('appbana-button');
    expect(html).toContain('New Customer');
    expect(html).not.toContain('appbana-button danger');
  });

  it('renders nothing when the app has no matching Add page', () => {
    const listPage = page('p1', 'Customer List');
    const html = renderToStaticMarkup(
      <RuntimeNavigationProvider pages={[listPage]} navigateToPage={() => {}}>
        <PageActions page={listPage} />
      </RuntimeNavigationProvider>,
    );
    expect(html).toBe('');
  });

  it('renders nothing when no navigation context is provided', () => {
    const listPage = page('p1', 'Customer List');
    const html = renderToStaticMarkup(<PageActions page={listPage} />);
    expect(html).toBe('');
  });
});

describe('PageActions (detail branch)', () => {
  it('renders an Edit + Delete pair for detail pages', () => {
    const detailPage = page('p1', 'Customer Detail');
    const html = renderToStaticMarkup(<PageActions page={detailPage} />);
    expect(html).toContain('appbana-button secondary');
    expect(html).toContain('appbana-button danger');
    expect(html).toContain('>Edit<');
    expect(html).toContain('>Delete<');
  });

  it('renders the pair even without a navigation context', () => {
    const detailPage = page('p1', 'Order Edit');
    const html = renderToStaticMarkup(<PageActions page={detailPage} />);
    expect(html).toContain('>Edit<');
    expect(html).toContain('>Delete<');
  });
});

describe('PageActions (other branch)', () => {
  it('renders nothing for dashboard pages', () => {
    const html = renderToStaticMarkup(<PageActions page={page('p1', 'Dashboard')} />);
    expect(html).toBe('');
  });

  it('renders nothing for settings pages', () => {
    const html = renderToStaticMarkup(<PageActions page={page('p1', 'Settings')} />);
    expect(html).toBe('');
  });

  it('renders nothing for add pages (the actions live on the list page)', () => {
    const html = renderToStaticMarkup(<PageActions page={page('p1', 'Add Customer')} />);
    expect(html).toBe('');
  });
});
