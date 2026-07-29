/**
 * ApprovalStatusPill.test.tsx — Phase C3.1.
 *
 * Rendered with react-dom/server; assertions are on the emitted class names
 * and label. No DOM shim required, matching the rest of this suite.
 */
import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ApprovalStatusPill, toApprovalState } from './ApprovalStatusPill';
import {
  isApprovalColumn,
  isApprovalStatusColumn,
  rowsHaveApprovalColumns,
  readRowValue,
} from './approval-columns';

describe('ApprovalStatusPill', () => {
  it('renders DRAFT as slate/neutral, not the generic blue "info" tone', () => {
    const html = renderToStaticMarkup(<ApprovalStatusPill value="DRAFT" />);
    expect(html).toContain('appbana-status-pill');
    expect(html).toContain('status-neutral');
    expect(html).not.toContain('status-info');
    expect(html).toContain('Draft');
  });

  it('renders PENDING as amber with an actionable label', () => {
    const html = renderToStaticMarkup(<ApprovalStatusPill value="PENDING" />);
    expect(html).toContain('status-warning');
    expect(html).toContain('Pending approval');
  });

  it('renders APPROVED as green', () => {
    const html = renderToStaticMarkup(<ApprovalStatusPill value="APPROVED" />);
    expect(html).toContain('status-success');
    expect(html).toContain('Approved');
  });

  it('renders REJECTED as red', () => {
    const html = renderToStaticMarkup(<ApprovalStatusPill value="REJECTED" />);
    expect(html).toContain('status-danger');
    expect(html).toContain('Rejected');
  });

  it('never renders the shouty backend casing as the visible label', () => {
    for (const state of ['DRAFT', 'PENDING', 'APPROVED', 'REJECTED']) {
      const html = renderToStaticMarkup(<ApprovalStatusPill value={state} />);
      // Strip the data attribute, which deliberately carries the raw machine value.
      const visible = html.replace(/ data-approval-state="[^"]*"/, '');
      expect(visible).not.toContain(state);
    }
  });

  it('tolerates lower-case and padded values', () => {
    const html = renderToStaticMarkup(<ApprovalStatusPill value="  pending " />);
    expect(html).toContain('status-warning');
    expect(html).toContain('Pending approval');
  });

  it('renders an em-dash for empty values so the column never collapses', () => {
    expect(renderToStaticMarkup(<ApprovalStatusPill value={null} />)).toContain('—');
    expect(renderToStaticMarkup(<ApprovalStatusPill value="" />)).toContain('—');
  });

  it('renders nothing for empty values in hide mode', () => {
    expect(renderToStaticMarkup(<ApprovalStatusPill value={null} emptyMode="hide" />)).toBe('');
  });

  it('keeps an unrecognised state visible rather than swallowing it', () => {
    const html = renderToStaticMarkup(<ApprovalStatusPill value="ESCALATED" />);
    expect(html).toContain('ESCALATED');
    expect(html).toContain('status-neutral');
    expect(html).toContain('data-approval-state="UNKNOWN"');
  });

  it('exposes the machine state as a data attribute for e2e selectors', () => {
    const html = renderToStaticMarkup(<ApprovalStatusPill value="approved" />);
    expect(html).toContain('data-approval-state="APPROVED"');
  });
});

describe('toApprovalState', () => {
  it('normalises casing and whitespace', () => {
    expect(toApprovalState('pending')).toBe('PENDING');
    expect(toApprovalState(' Approved ')).toBe('APPROVED');
  });

  it('returns null for empty and unknown values', () => {
    expect(toApprovalState(null)).toBeNull();
    expect(toApprovalState(undefined)).toBeNull();
    expect(toApprovalState('   ')).toBeNull();
    expect(toApprovalState('nope')).toBeNull();
  });
});

describe('approval-columns', () => {
  it('recognises every column the backend injects', () => {
    for (const c of [
      'approval_status', 'approval_revision', 'approval_parent_id',
      'submitted_by', 'submitted_at', 'approved_by', 'approved_at', 'rejection_reason',
    ]) {
      expect(isApprovalColumn(c)).toBe(true);
      expect(isApprovalColumn(c.toUpperCase())).toBe(true);
    }
  });

  it('does not claim ordinary user fields', () => {
    for (const c of ['status', 'name', 'approval_notes', 'approved']) {
      expect(isApprovalColumn(c)).toBe(false);
    }
  });

  it('identifies the status column specifically, in any casing', () => {
    expect(isApprovalStatusColumn('APPROVAL_STATUS')).toBe(true);
    expect(isApprovalStatusColumn('approval_status')).toBe(true);
    expect(isApprovalStatusColumn('approval_revision')).toBe(false);
    expect(isApprovalStatusColumn('status')).toBe(false);
  });

  it('detects approval-enabled entities from the returned rows', () => {
    expect(rowsHaveApprovalColumns([{ id: 1, name: 'a', approval_status: 'DRAFT' }])).toBe(true);
    expect(rowsHaveApprovalColumns([{ ID: 1, APPROVAL_STATUS: 'DRAFT' }])).toBe(true);
    expect(rowsHaveApprovalColumns([{ id: 1, name: 'a' }])).toBe(false);
    expect(rowsHaveApprovalColumns([])).toBe(false);
  });

  it('reads row values regardless of the casing the API used', () => {
    expect(readRowValue({ approval_status: 'PENDING' }, 'approval_status')).toBe('PENDING');
    expect(readRowValue({ APPROVAL_STATUS: 'PENDING' }, 'approval_status')).toBe('PENDING');
    expect(readRowValue({ name: 'x' }, 'approval_status')).toBeUndefined();
    expect(readRowValue(null, 'approval_status')).toBeUndefined();
  });
});
