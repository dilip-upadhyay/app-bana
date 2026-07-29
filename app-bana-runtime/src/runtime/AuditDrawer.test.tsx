/**
 * AuditDrawer.test.tsx — Task C3.5.
 *
 * Covers the audit entry mapping, the drawer's four states, and the envelope
 * unwrapping in the shared client that this feature depends on.
 */
import { describe, it, expect, afterEach, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { AuditDrawer, describeAuditEntry } from './AuditDrawer';
import { fetchApprovalAudit, fetchPendingApprovals } from '@appbana/shared';

describe('describeAuditEntry', () => {
  it('names the action and the actor', () => {
    const v = describeAuditEntry({ action: 'APPROVE', actor_user_id: 'alice' });
    expect(v.summary).toBe('Approved by alice');
    expect(v.tone).toBe('neutral'); // no status supplied
  });

  it('takes its tone from the resulting status', () => {
    expect(describeAuditEntry({ action: 'REJECT', status: 'REJECTED' }).tone).toBe('danger');
    expect(describeAuditEntry({ action: 'APPROVE', status: 'APPROVED' }).tone).toBe('success');
    expect(describeAuditEntry({ action: 'SUBMIT', status: 'PENDING' }).tone).toBe('warning');
    expect(describeAuditEntry({ action: 'DRAFT', status: 'DRAFT' }).tone).toBe('neutral');
  });

  it('is tolerant of casing, since the backend persists uppercase', () => {
    expect(describeAuditEntry({ action: 'approve' }).summary).toBe('Approved');
    expect(describeAuditEntry({ status: ' rejected ' }).summary).toBe('Rejected');
  });

  it('falls back to the status when no action was recorded', () => {
    expect(describeAuditEntry({ status: 'APPROVED' }).summary).toBe('Approved');
  });

  /**
   * An unrecognised action must stay visible. A blank row in an audit trail is
   * worse than an ugly one: it suggests nothing happened.
   */
  it('keeps unknown actions visible rather than blanking the row', () => {
    const v = describeAuditEntry({ action: 'ESCALATE', actor_user_id: 'bob' });
    expect(v.summary).toBe('ESCALATE by bob');
  });

  it('describes a wholly empty entry as an update rather than an empty string', () => {
    expect(describeAuditEntry({}).summary).toBe('Updated');
  });

  it('omits the actor clause when no actor was recorded', () => {
    expect(describeAuditEntry({ action: 'APPROVE' }).summary).toBe('Approved');
  });

  it('treats blank and whitespace-only comments as absent', () => {
    expect(describeAuditEntry({ comments: '   ' }).comment).toBeNull();
    expect(describeAuditEntry({ comments: null }).comment).toBeNull();
    expect(describeAuditEntry({ comments: 'Wrong amount' }).comment).toBe('Wrong amount');
  });

  it('carries the revision through only when it is numeric', () => {
    expect(describeAuditEntry({ revision: 2 }).revision).toBe(2);
    expect(describeAuditEntry({}).revision).toBeNull();
  });

  it('formats the timestamp and tolerates a missing one', () => {
    expect(describeAuditEntry({ created_at: '2026-07-28T10:15:00Z' }).when).not.toBe('');
    expect(describeAuditEntry({ created_at: null }).when).toBe('');
  });
});

describe('AuditDrawer rendering', () => {
  const target = { tenantId: 't', appId: 'a', entityName: 'Invoice', rowId: '7' };

  it('renders the record label in the heading', () => {
    const html = renderToStaticMarkup(
      <AuditDrawer open target={target} recordLabel="Invoice #7" onClose={() => {}} />
    );
    expect(html).toContain('Approval history — Invoice #7');
  });

  it('falls back to a generic heading without a label', () => {
    const html = renderToStaticMarkup(
      <AuditDrawer open target={target} onClose={() => {}} />
    );
    expect(html).toContain('Approval history');
    expect(html).not.toContain('—');
  });

  /**
   * On the server render no fetch has resolved, so the drawer shows its empty
   * state. It must say the record has no history yet, not show a bare panel.
   */
  it('explains an empty trail instead of rendering nothing', () => {
    const html = renderToStaticMarkup(
      <AuditDrawer open target={target} onClose={() => {}} />
    );
    expect(html).toContain('has not been through the approval workflow yet');
  });

  it('tags the dialog with the row id so e2e can target it', () => {
    const html = renderToStaticMarkup(
      <AuditDrawer open target={target} onClose={() => {}} />
    );
    expect(html).toContain('data-audit-drawer="7"');
  });

  it('always offers a way out', () => {
    const html = renderToStaticMarkup(
      <AuditDrawer open target={target} onClose={() => {}} />
    );
    expect(html).toContain('Close');
  });
});

/**
 * ApprovalRoutes wraps both list responses in an envelope — `{count, records}`
 * and `{count, history}` — but the client originally returned `res.json()`
 * straight through while promising an array. The queue silently rendered as
 * permanently empty and the audit trail would have thrown on `.map`.
 */
describe('approval list envelope unwrapping', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  function stubJson(payload: unknown) {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => payload,
    }) as unknown as typeof fetch;
  }

  it('unwraps the pending queue envelope', async () => {
    stubJson({ count: 2, records: [{ id: 1 }, { id: 2 }] });
    const rows = await fetchPendingApprovals(
      { tenantId: 't', appId: 'a', entityName: 'Invoice' },
      'token'
    );
    expect(rows).toHaveLength(2);
  });

  it('unwraps the audit envelope', async () => {
    stubJson({ count: 1, history: [{ action: 'APPROVE' }] });
    const trail = await fetchApprovalAudit(
      { tenantId: 't', appId: 'a', entityName: 'Invoice', rowId: '1' },
      'token'
    );
    expect(trail).toHaveLength(1);
    expect(trail[0].action).toBe('APPROVE');
  });

  it('still accepts a bare array, so unwrapping the endpoint later is safe', async () => {
    stubJson([{ id: 1 }]);
    const rows = await fetchPendingApprovals(
      { tenantId: 't', appId: 'a', entityName: 'Invoice' },
      'token'
    );
    expect(rows).toHaveLength(1);
  });

  it('yields an empty list rather than throwing on an unexpected shape', async () => {
    stubJson({ unexpected: true });
    const rows = await fetchPendingApprovals(
      { tenantId: 't', appId: 'a', entityName: 'Invoice' },
      'token'
    );
    expect(rows).toEqual([]);
  });
});
