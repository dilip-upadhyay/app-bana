/**
 * RecordApprovalPanel.test.tsx — Task C3.9.
 *
 * The panel that closes the maker-checker round trip.
 *
 * Review found that C3 had built a submit affordance only inside the insert
 * form, so a maker could create a draft but could never submit an existing one
 * or resubmit a rejected one. The Playwright round-trip was not missing
 * coverage; it was blocked on a missing button. These tests pin the button into
 * existence, per state, and pin the rejection reason the maker previously had
 * no way to read.
 */
import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import {
  RecordApprovalPanel,
  makerActionFor,
  makerHintFor,
} from './RecordApprovalPanel';

const TARGET = { tenantId: 't1', appId: 'a1', entityName: 'Invoice', rowId: '7' };

function render(record: Record<string, unknown> | null) {
  return renderToStaticMarkup(
    <RecordApprovalPanel record={record} target={TARGET} recordLabel="Invoice #7" />,
  );
}

describe('makerActionFor', () => {
  it('offers submit on a draft and resubmit on a rejection', () => {
    expect(makerActionFor('DRAFT')).toBe('submit');
    expect(makerActionFor('REJECTED')).toBe('resubmit');
  });

  it('offers nothing while pending or approved — those states are not the maker\'s to move', () => {
    expect(makerActionFor('PENDING')).toBeNull();
    expect(makerActionFor('APPROVED')).toBeNull();
    expect(makerActionFor(null)).toBeNull();
  });
});

describe('makerHintFor', () => {
  it('speaks to the maker about what happens next, not about the state machine', () => {
    expect(makerHintFor('REJECTED')).toContain('resubmit');
    expect(makerHintFor('PENDING')).toContain('checker');
    expect(makerHintFor(null)).toBe('');
  });
});

describe('RecordApprovalPanel', () => {
  it('renders nothing for an entity with no approval workflow', () => {
    expect(render({ id: 1, name: 'Acme' })).toBe('');
    expect(render(null)).toBe('');
  });

  it('gives a draft a submit button — the affordance C3 never built', () => {
    const html = render({ id: 7, approval_status: 'DRAFT' });
    expect(html).toContain('Submit for approval');
    expect(html).toContain('data-approval-state="DRAFT"');
  });

  it('gives a rejected record a resubmit button and shows the reason', () => {
    const html = render({
      id: 7,
      approval_status: 'REJECTED',
      rejection_reason: 'Total does not match the attached PO.',
    });
    expect(html).toContain('Resubmit for approval');
    // rejection_reason is an approval column and so is stripped from the
    // table's display columns — before this panel the maker could see that
    // they were rejected but never why.
    expect(html).toContain('Total does not match the attached PO.');
    expect(html).toContain('Reason for rework');
  });

  it('says so when a rejection carries no recorded reason', () => {
    const html = render({ id: 7, approval_status: 'REJECTED' });
    expect(html).toContain('No reason was recorded');
  });

  it('offers no submit action while pending, since only a checker can move it', () => {
    const html = render({ id: 7, approval_status: 'PENDING' });
    expect(html).not.toContain('Submit for approval');
    expect(html).not.toContain('Resubmit for approval');
    expect(html).toContain('data-approval-state="PENDING"');
  });

  it('offers no submit action once approved', () => {
    const html = render({ id: 7, approval_status: 'APPROVED' });
    expect(html).not.toContain('Submit for approval');
  });

  it('offers History to the maker, not just the checker queue', () => {
    expect(render({ id: 7, approval_status: 'REJECTED' })).toContain('History');
  });

  it('tolerates UPPER-case columns, which a plain SELECT * returns', () => {
    const html = render({ ID: 7, APPROVAL_STATUS: 'DRAFT' });
    expect(html).toContain('Submit for approval');
  });
});
