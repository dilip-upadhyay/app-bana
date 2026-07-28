/**
 * AuditDrawer.tsx — Task C3.5.
 *
 * The approval history of a single record: who submitted it, who approved or
 * rejected it, when, and why.
 *
 * Two decisions worth recording:
 *
 *   A 403 is reported plainly rather than rendered as an empty timeline. In the
 *   checker queue a 403 is answered with an empty list, because there the user
 *   never asked a question — the queue is offered to them and "you are not a
 *   checker here" is best expressed by there being nothing to see. Here the user
 *   explicitly clicked History, so silence would read as "this record has no
 *   history", which is a different and false statement.
 *
 *   The trail is rendered newest-first because that is the order the backend
 *   returns (C2.6) and the order that answers the question people actually open
 *   this for: what happened to it most recently.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import type { ApprovalAuditEntry, ApprovalTarget } from '@appbana/shared';
import { fetchApprovalAudit } from '@appbana/shared';
import { getRuntimeToken } from './qualifyEntityKey';
import { formatDate } from './cell-formatters';
import { toApprovalState } from './ApprovalStatusPill';
import { Button } from './Button';

export interface AuditEntryView {
  /** One line describing what happened, e.g. "Approved by alice". */
  readonly summary: string;
  /** Free-text the actor supplied — a rejection reason, usually. */
  readonly comment: string | null;
  /** Formatted timestamp, or an empty string when the backend sent none. */
  readonly when: string;
  /** Full timestamp for the `title` attribute. */
  readonly whenTitle: string;
  /** Tone driving the dot colour, aligned with ApprovalStatusPill. */
  readonly tone: 'neutral' | 'warning' | 'success' | 'danger';
  readonly revision: number | null;
}

const ACTION_VERBS: Record<string, string> = {
  SUBMIT: 'Submitted for approval',
  SUBMITTED: 'Submitted for approval',
  APPROVE: 'Approved',
  APPROVED: 'Approved',
  REJECT: 'Rejected',
  REJECTED: 'Rejected',
  DRAFT: 'Saved as draft',
  REVISE: 'Revised after rejection',
  REVISED: 'Revised after rejection',
};

const TONES: Record<string, AuditEntryView['tone']> = {
  DRAFT: 'neutral',
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
};

/**
 * Turn one raw audit row into something displayable.
 *
 * Exported for testing, and because the mapping is the part most likely to be
 * wrong: the backend writes `action` and `status` independently, and neither is
 * guaranteed to be present on historical rows.
 */
export function describeAuditEntry(entry: ApprovalAuditEntry): AuditEntryView {
  const rawAction = String(entry.action ?? '').trim().toUpperCase();
  const rawStatus = String(entry.status ?? '').trim().toUpperCase();

  // Prefer the action ("what was done"); fall back to the resulting status, and
  // finally to the raw value so an unrecognised entry stays visible rather than
  // silently rendering as a blank row.
  const verb =
    ACTION_VERBS[rawAction] ??
    ACTION_VERBS[rawStatus] ??
    (rawAction || rawStatus || 'Updated');

  const actor = String(entry.actor_user_id ?? '').trim();
  const summary = actor ? `${verb} by ${actor}` : verb;

  const stamp = formatDate(entry.created_at ?? null, 'datetime');
  const comment = typeof entry.comments === 'string' && entry.comments.trim()
    ? entry.comments.trim()
    : null;

  const state = toApprovalState(rawStatus || rawAction);
  const tone = (state && TONES[state]) ?? 'neutral';

  const revision = typeof entry.revision === 'number' ? entry.revision : null;

  return { summary, comment, when: stamp.label, whenTitle: stamp.title, tone, revision };
}

const DOT_CLASSES: Record<AuditEntryView['tone'], string> = {
  neutral: 'bg-slate-400',
  warning: 'bg-amber-500',
  success: 'bg-emerald-500',
  danger: 'bg-red-500',
};

export interface AuditDrawerProps {
  readonly open: boolean;
  /** Null while closed; the drawer only fetches once it has a target. */
  readonly target: ApprovalTarget | null;
  /** Shown in the heading, e.g. "Invoice #204". */
  readonly recordLabel?: string;
  readonly onClose: () => void;
}

export function AuditDrawer({ open, target, recordLabel, onClose }: Readonly<AuditDrawerProps>) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [entries, setEntries] = useState<ApprovalAuditEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);

  const key = target
    ? `${target.tenantId}/${target.appId}/${target.entityName}/${target.rowId}`
    : null;

  const load = useCallback(async () => {
    if (!target) return;
    setLoading(true);
    setError(null);
    setForbidden(false);
    try {
      setEntries(await fetchApprovalAudit(target, getRuntimeToken()));
    } catch (e) {
      if ((e as { status?: number })?.status === 403) {
        setForbidden(true);
        setEntries([]);
      } else {
        setError(e instanceof Error ? e.message : 'Failed to load the approval history');
      }
    } finally {
      setLoading(false);
    }
    // `key` stands in for the target's identity so that re-rendering with an
    // equal-but-new target object does not refetch.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  useEffect(() => {
    const dlg = dialogRef.current;
    if (!dlg) return;
    if (open && !dlg.open) {
      dlg.showModal();
      void load();
    } else if (!open && dlg.open) {
      dlg.close();
    }
  }, [open, load]);

  useEffect(() => {
    const dlg = dialogRef.current;
    if (!dlg) return;
    const onCancel = (e: Event) => {
      e.preventDefault();
      onClose();
    };
    dlg.addEventListener('cancel', onCancel);
    return () => dlg.removeEventListener('cancel', onCancel);
  }, [onClose]);

  const views = entries.map(describeAuditEntry);

  return (
    <dialog
      ref={dialogRef}
      className="appbana-confirm appbana-audit-drawer"
      aria-labelledby="appbana-audit-title"
      data-audit-drawer={target?.rowId ?? ''}
    >
      <div className="appbana-confirm-body">
        <h2 id="appbana-audit-title" className="appbana-confirm-title">
          {recordLabel ? `Approval history — ${recordLabel}` : 'Approval history'}
        </h2>

        {loading && <p className="appbana-confirm-message">Loading history…</p>}

        {!loading && forbidden && (
          <p className="appbana-confirm-message" role="status">
            You do not have permission to view the approval history for this record.
          </p>
        )}

        {!loading && error && (
          <p className="appbana-field-error" role="alert">
            {error}{' '}
            <button type="button" className="underline" onClick={() => void load()}>
              Retry
            </button>
          </p>
        )}

        {!loading && !error && !forbidden && views.length === 0 && (
          <p className="appbana-confirm-message" role="status">
            This record has not been through the approval workflow yet.
          </p>
        )}

        {!loading && !error && views.length > 0 && (
          <ol className="appbana-audit-list">
            {views.map((v, i) => (
              <li key={`${v.summary}-${v.whenTitle}-${i}`} className="appbana-audit-item">
                <span className={`appbana-audit-dot ${DOT_CLASSES[v.tone]}`} aria-hidden="true" />
                <div className="appbana-audit-content">
                  <p className="appbana-audit-summary">
                    {v.summary}
                    {v.revision != null && (
                      <span className="appbana-audit-revision"> · revision {v.revision}</span>
                    )}
                  </p>
                  {v.when && (
                    <p className="appbana-audit-when" title={v.whenTitle}>{v.when}</p>
                  )}
                  {v.comment && <p className="appbana-audit-comment">“{v.comment}”</p>}
                </div>
              </li>
            ))}
          </ol>
        )}

        <div className="appbana-confirm-actions">
          <Button variant="secondary" onClick={onClose}>Close</Button>
        </div>
      </div>
    </dialog>
  );
}
