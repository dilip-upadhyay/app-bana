/**
 * Unit tests for the pure cell-formatter helpers used by StudioTableLive.
 * These functions have zero React / DOM dependencies, so vitest can run
 * them in Node with no jsdom setup.
 *
 * Coverage matches the Sprint 1 exit criteria in
 * docs/planning/RUNTIME_UX_OVERHAUL_PLAN.md.
 */
import { describe, it, expect } from 'vitest';
import {
  humanizeHeader,
  formatDate,
  pickReferenceLabel,
  classifyStatus,
} from './cell-formatters';

describe('humanizeHeader', () => {
  it('turns snake_case into sentence case', () => {
    expect(humanizeHeader('full_name')).toBe('Full name');
    expect(humanizeHeader('created_at')).toBe('Created at');
    expect(humanizeHeader('onboarding_status')).toBe('Onboarding status');
  });

  it('turns camelCase into sentence case', () => {
    expect(humanizeHeader('firstName')).toBe('First name');
    expect(humanizeHeader('customerID')).toBe('Customer id');
  });

  it('turns kebab-case into sentence case', () => {
    expect(humanizeHeader('user-email')).toBe('User email');
  });

  it('preserves common acronyms as ALL-CAPS', () => {
    expect(humanizeHeader('id')).toBe('ID');
    expect(humanizeHeader('url')).toBe('URL');
    expect(humanizeHeader('api')).toBe('API');
  });

  it('returns empty string for null-ish input', () => {
    expect(humanizeHeader(undefined)).toBe('');
    expect(humanizeHeader(null)).toBe('');
    expect(humanizeHeader('')).toBe('');
  });
});

describe('formatDate', () => {
  it('formats a full ISO datetime into a human label', () => {
    const out = formatDate('2026-07-25T18:26:36Z');
    expect(out.isDate).toBe(true);
    // Format uses Intl.DateTimeFormat with en-US — expect the year 2026 in it.
    expect(out.label).toMatch(/2026/);
    expect(out.label).toMatch(/Jul/);
    // The tooltip / title must retain the raw ISO for accessibility.
    expect(out.title).toBe('2026-07-25T18:26:36Z');
  });

  it('uses date-only format when the column type is "date"', () => {
    const out = formatDate('2026-07-25', 'date');
    expect(out.isDate).toBe(true);
    // No time-of-day in the label.
    expect(out.label).not.toMatch(/[AP]M/);
    expect(out.label).toMatch(/Jul 25, 2026/);
  });

  it('passes non-date strings through unchanged', () => {
    const out = formatDate('hello');
    expect(out.isDate).toBe(false);
    expect(out.label).toBe('hello');
  });

  it('returns empty result for null/empty input', () => {
    expect(formatDate(null).label).toBe('');
    expect(formatDate('').label).toBe('');
  });
});

describe('pickReferenceLabel', () => {
  it('prefers a `name` field over `id`', () => {
    expect(pickReferenceLabel({ id: 1, name: 'Alice Johnson' })).toBe('Alice Johnson');
  });

  it('falls back to `full_name`, `title`, `email` in priority order', () => {
    expect(pickReferenceLabel({ id: 2, full_name: 'Bob'   })).toBe('Bob');
    expect(pickReferenceLabel({ id: 3, title: 'Manager'   })).toBe('Manager');
    expect(pickReferenceLabel({ id: 4, email: 'x@y.com'   })).toBe('x@y.com');
  });

  it('is case-insensitive on the key name', () => {
    expect(pickReferenceLabel({ ID: 5, Name: 'Case Insensitive' })).toBe('Case Insensitive');
  });

  it('falls back to `#id` when no candidate label is present', () => {
    expect(pickReferenceLabel({ id: 99 })).toBe('#99');
  });

  it('returns empty string for a null row', () => {
    expect(pickReferenceLabel(null)).toBe('');
    expect(pickReferenceLabel(undefined)).toBe('');
  });
});

describe('classifyStatus', () => {
  it('maps completed-like states to success', () => {
    expect(classifyStatus('Completed')).toBe('success');
    expect(classifyStatus('Approved')).toBe('success');
    expect(classifyStatus('Paid')).toBe('success');
  });

  it('maps in-progress states to info', () => {
    expect(classifyStatus('In Progress')).toBe('info');
    expect(classifyStatus('New')).toBe('info');
    expect(classifyStatus('Draft')).toBe('info');
  });

  it('maps waiting/warning states to warning', () => {
    expect(classifyStatus('On Hold')).toBe('warning');
    expect(classifyStatus('Waiting')).toBe('warning');
  });

  it('maps failed states to danger', () => {
    expect(classifyStatus('Cancelled')).toBe('danger');
    expect(classifyStatus('Rejected')).toBe('danger');
  });

  it('returns neutral for unknown / empty values', () => {
    expect(classifyStatus('Unspecified')).toBe('neutral');
    expect(classifyStatus('')).toBe('neutral');
    expect(classifyStatus(null)).toBe('neutral');
  });
});
