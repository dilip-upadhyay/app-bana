/**
 * Unit tests for RuntimeSidebar's pure helpers.
 * Runs in Node via vitest — no jsdom needed since we only test
 * the classification / grouping functions, not the JSX output.
 *
 * Coverage matches Sprint 2 Task 2.2 in
 * docs/planning/RUNTIME_UX_OVERHAUL_PLAN.md.
 */
import { describe, it, expect } from 'vitest';
import { __test__ } from './RuntimeSidebar';
import type { PageMeta } from '@appbana/shared';

const { classifyKind, extractEntity, pluralize, singularize, groupPages } = __test__;

function page(id: string, name: string): PageMeta {
  return { id, name, nodes: [] } as unknown as PageMeta;
}

describe('classifyKind', () => {
  it('detects "Add" and its synonyms', () => {
    expect(classifyKind('Add Customer')).toBe('add');
    expect(classifyKind('New Order')).toBe('add');
    expect(classifyKind('Create User')).toBe('add');
    expect(classifyKind('Register Vendor')).toBe('add');
    expect(classifyKind('Onboard Customer')).toBe('add');
  });

  it('detects "List" and its synonyms', () => {
    expect(classifyKind('Customer List')).toBe('list');
    expect(classifyKind('Order Table')).toBe('list');
    expect(classifyKind('Browse Users')).toBe('list');
    expect(classifyKind('All Invoices')).toBe('list');
  });

  it('detects "Detail" and its synonyms', () => {
    expect(classifyKind('Customer Detail')).toBe('detail');
    expect(classifyKind('User Profile')).toBe('detail');
    expect(classifyKind('Order Edit')).toBe('detail');
  });

  it('detects Dashboard / Chart / Settings', () => {
    expect(classifyKind('Dashboard')).toBe('dashboard');
    expect(classifyKind('Overview')).toBe('dashboard');
    expect(classifyKind('Sales Report')).toBe('chart');
    expect(classifyKind('Settings')).toBe('settings');
  });

  it('falls back to other for unrecognised names', () => {
    expect(classifyKind('Foo Bar')).toBe('other');
    expect(classifyKind(undefined)).toBe('other');
  });
});

describe('extractEntity', () => {
  it('strips leading Add / New / Create verbs', () => {
    expect(extractEntity('Add Customer', 'add')).toBe('Customer');
    expect(extractEntity('New Onboarding Process', 'add')).toBe('Onboarding Process');
    expect(extractEntity('Create User', 'add')).toBe('User');
  });

  it('strips trailing List / Table nouns', () => {
    expect(extractEntity('Customer List', 'list')).toBe('Customer');
    expect(extractEntity('Order Table', 'list')).toBe('Order');
    expect(extractEntity('Onboarding Process List', 'list')).toBe('Onboarding Process');
  });

  it('strips trailing Detail / Profile / Edit nouns', () => {
    expect(extractEntity('Customer Detail', 'detail')).toBe('Customer');
    expect(extractEntity('User Profile', 'detail')).toBe('User');
    expect(extractEntity('Order Edit', 'detail')).toBe('Order');
  });

  it('accepts bare entity names as entity tokens', () => {
    expect(extractEntity('Customers', 'other')).toBe('Customers');
  });

  it('returns null for dashboard / chart / settings pages', () => {
    expect(extractEntity('Dashboard', 'dashboard')).toBeNull();
    expect(extractEntity('Sales Report', 'chart')).toBeNull();
    expect(extractEntity('Settings', 'settings')).toBeNull();
  });
});

describe('pluralize / singularize', () => {
  it('pluralizes regular nouns', () => {
    expect(pluralize('Customer')).toBe('Customers');
    expect(pluralize('Order')).toBe('Orders');
  });

  it('pluralizes -y stems to -ies', () => {
    expect(pluralize('Company')).toBe('Companies');
    expect(pluralize('Category')).toBe('Categories');
  });

  it('pluralizes -s/-x/-ch/-sh with -es', () => {
    expect(pluralize('Bus')).toBe('Buses');
    expect(pluralize('Box')).toBe('Boxes');
    expect(pluralize('Batch')).toBe('Batches');
    expect(pluralize('Wish')).toBe('Wishes');
  });

  it('singularizes plurals', () => {
    expect(singularize('Customers')).toBe('Customer');
    expect(singularize('Companies')).toBe('Company');
    expect(singularize('Buses')).toBe('Bus');
  });
});

describe('groupPages', () => {
  it('clusters Add + List + Detail under one entity group', () => {
    const pages = [
      page('p1', 'Customer List'),
      page('p2', 'Add Customer'),
      page('p3', 'Customer Detail'),
    ];
    const groups = groupPages(pages);
    expect(groups).toHaveLength(1);
    expect(groups[0].label).toBe('Customers');
    // List first, Add second, Detail last.
    expect(groups[0].pages.map((p) => p.id)).toEqual(['p1', 'p2', 'p3']);
  });

  it('reorders within a group to List → Add → Detail regardless of input order', () => {
    const pages = [
      page('p1', 'Add Customer'),
      page('p2', 'Customer Detail'),
      page('p3', 'Customer List'),
    ];
    const groups = groupPages(pages);
    expect(groups[0].pages.map((p) => p.id)).toEqual(['p3', 'p1', 'p2']);
  });

  it('preserves group order by first appearance in input', () => {
    const pages = [
      page('p1', 'Add Order'),
      page('p2', 'Add Customer'),
    ];
    const groups = groupPages(pages);
    expect(groups.map((g) => g.label)).toEqual(['Orders', 'Customers']);
  });

  it('puts dashboard / settings pages under an "Other" group', () => {
    const pages = [
      page('p1', 'Customer List'),
      page('p2', 'Dashboard'),
      page('p3', 'Settings'),
    ];
    const groups = groupPages(pages);
    expect(groups.map((g) => g.label)).toEqual(['Customers', 'Other']);
    expect(groups[1].pages.map((p) => p.id)).toEqual(['p2', 'p3']);
  });

  it('handles empty input', () => {
    expect(groupPages([])).toEqual([]);
  });

  it('clusters bare "Customers" plural with "Add Customer"', () => {
    const pages = [
      page('p1', 'Customers'),
      page('p2', 'Add Customer'),
    ];
    const groups = groupPages(pages);
    expect(groups).toHaveLength(1);
    expect(groups[0].label).toBe('Customers');
    expect(groups[0].pages).toHaveLength(2);
  });
});
