/**
 * page-classifier.test.ts — Unit coverage for the pure helpers shared between
 * the sidebar (Task 2.2) and the empty-state CTA lookup (Task 2.3). The
 * classification / entity-extraction tests live alongside the sidebar suite
 * to avoid duplication; this file focuses on the two Task-2.3 additions:
 * `entityNameFromKey` and `findAddPageForEntity`.
 */
import { describe, it, expect } from 'vitest';
import type { PageMeta } from '@appbana/shared';
import { entityNameFromKey, findAddPageForEntity } from './page-classifier';

const page = (id: string, name: string): PageMeta => ({
  id,
  name,
  path: `/${id}`,
  rootId: 'root',
  nodes: [],
}) as unknown as PageMeta;

describe('entityNameFromKey', () => {
  it('extracts the trailing PascalCase entity token from a qualified key', () => {
    expect(entityNameFromKey('default_abc123_Customer')).toBe('Customer');
    expect(entityNameFromKey('default_7495460a-bc30-40e9-8235-9ddb08720b2a_Order')).toBe('Order');
  });

  it('returns the input for a bare entity name', () => {
    expect(entityNameFromKey('Customer')).toBe('Customer');
  });

  it('returns empty string for empty input', () => {
    expect(entityNameFromKey('')).toBe('');
  });

  it('falls back to the last segment when no PascalCase segment is present', () => {
    expect(entityNameFromKey('a_b_c')).toBe('c');
  });
});

describe('findAddPageForEntity', () => {
  const pages: PageMeta[] = [
    page('p1', 'Customer List'),
    page('p2', 'Add Customer'),
    page('p3', 'Customer Detail'),
    page('p4', 'Order List'),
    page('p5', 'New Order'),
    page('p6', 'Dashboard'),
  ];

  it('finds the "Add {Entity}" page for a matching singular entity', () => {
    expect(findAddPageForEntity('Customer', pages)?.id).toBe('p2');
  });

  it('finds an "Add" page using an alternate verb (New / Create / Register)', () => {
    expect(findAddPageForEntity('Order', pages)?.id).toBe('p5');
  });

  it('matches case-insensitively', () => {
    expect(findAddPageForEntity('customer', pages)?.id).toBe('p2');
    expect(findAddPageForEntity('CUSTOMER', pages)?.id).toBe('p2');
  });

  it('matches when the entity token is passed in plural form', () => {
    expect(findAddPageForEntity('Customers', pages)?.id).toBe('p2');
  });

  it('returns null when no add-page exists for the entity', () => {
    expect(findAddPageForEntity('Widget', pages)).toBeNull();
  });

  it('returns null for empty inputs', () => {
    expect(findAddPageForEntity('', pages)).toBeNull();
    expect(findAddPageForEntity('Customer', [])).toBeNull();
  });
});
