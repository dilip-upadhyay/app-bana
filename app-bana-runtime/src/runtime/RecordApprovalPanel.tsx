/**
 * RecordApprovalPanel.tsx — Task C3.9.
 *
 * The maker's half of the maker-checker round trip.
 *
 * C3 shipped a submit affordance in exactly one place: the insert form, which
 * offers "Save as draft" and "Submit for approval" at creation time. Review
 * found that this left the cycle unbuildable rather than merely untested — a
 * maker could create a draft but could not submit an existing one, and could
 * not revise and resubmit a rejected one. Worse, the post-save toast told them
 * to "submit it from the list view", an affordance that did not exist. This
 * panel is that missing surface.
 *
 * What it shows, by state:
 *   DRAFT     — pill + "Submit for approval"
 *   REJECTED  — pill + the checker's reason + "Resubmit for approval"
 *   PENDING   — pill + who is waiting on it; no action (only a checker moves it)
 *   APPROVED  — pill only
 *
 * The rejection reason matters especially: `rejection_reason` is an approval
 * column and is therefore stripped from the table's display columns, so before
 * this panel a maker could see *that* they were rejected but never *why*.
 *
 * History is offered to the maker too. AuditDrawer used to be mounted only in
 * the checker queue, which put the audit trail in front of the one party who
 * did not need to act on it.
 */
import { useCallback, useMemo, useState } from 'react';
import type { ApprovalTarget } from '@appbana/shared';
import { ApprovalConflictError, submitForApproval } from '@appbana/shared';
import { ApprovalStatusPill, toApprovalState } from './ApprovalStatusPill';
import type { ApprovalState } from './ApprovalStatusPill';
import { readRowValue, APPROVAL_STATUS_COLUMN } from './approval-columns';
import { AuditDrawer } from './AuditDrawer';
import { Button } from './Button';
import { getRuntimeToken } from './qualifyEntityKey';
import { toast } from './Toaster';
import { formatDate } from './cell-formatters';

export interface RecordApprovalPanelProps {
  readonly record: Record<string, unknown> | null | undefined;
  readonly target: ApprovalTarget | null;
  readonly recordLabel?: string;
  /** Called after a successful submit so the host can re-hydrate the record. */
  readonly onChanged?: () => void;
}

/** The action a maker may take, or null when the state is not theirs to move. */
export function makerActionFor(state: ApprovalState | null): 'submit' | 'resubmit' | null {
  if (state === 'DRAFT') return 'submit';
  if (state === 'REJECTED') return 'resubmit';
  return null;
}

/** Explains, in the second person, what the current state means for the maker. */
export function makerHintFor(state: ApprovalState | null): string {
  switch (state) {
    case 'DRAFT':
      return 'This record is a draft. It is not visible as approved work until a checker approves it.';
    case 'PENDING':
      return 'Submitted. It is now with a checker — you cannot change it until they approve or return it.';
    case 'PENDING_L2':
      return 'Approved at level 1. It is now with a final (level-2) checker — you cannot change it until they approve or return it.';
    case 'REJECTED':
      return 'A checker returned this for rework. Edit it, then resubmit.';
    case 'APPROVED':
      return 'Approved.';
    default:
      return '';
  }
}

export function RecordApprovalPanel(props: Readonly<RecordApprovalPanelProps>) {
  const { record, target, recordLabel, onChanged } = props;
  const [submitting, setSubmitting] = useState(false);
  const [auditing, setAuditing] = useState(false);

  const rawStatus = readRowValue(record, APPROVAL_STATUS_COLUMN);
  const state = useMemo(() => toApprovalState(rawStatus), [rawStatus]);
  const rejectionReason = readRowValue(record, 'rejection_reason');
  const submittedAt = readRowValue(record, 'submitted_at');
  const action = makerActionFor(state);

  const handleSubmit = useCallback(async () => {
    if (!target) return;
    setSubmitting(true);
    try {
      await submitForApproval(target, getRuntimeToken());
      toast.success(action === 'resubmit' ? 'Resubmitted for approval' : 'Submitted for approval', {
        description: 'A checker will review it. You cannot edit it while it is pending.',
      });
      onChanged?.();
    } catch (e) {
      if (e instanceof ApprovalConflictError) {
        // 409: someone else moved it, or it was already submitted. Not a
        // failure the user can fix by retrying, so refresh rather than nag.
        toast.info('This record already moved on', {
          description: e.message || 'Its approval state changed while this page was open. Refreshed.',
        });
        onChanged?.();
      } else {
        toast.error('Submit failed', {
          description: e instanceof Error ? e.message : undefined,
        });
      }
    } finally {
      setSubmitting(false);
    }
  }, [target, action, onChanged]);

  // No approval column on this record ⇒ the entity does not use the workflow.
  if (rawStatus == null || rawStatus === '') return null;

  const submittedLabel = submittedAt ? formatDate(submittedAt, 'datetime').label : '';

  return (
    <section className="appbana-approval-panel" data-approval-state={state ?? 'UNKNOWN'}>
      <div className="appbana-approval-panel-head">
        <ApprovalStatusPill value={rawStatus} />
        {submittedLabel && (state === 'PENDING' || state === 'PENDING_L2') && (
          <span className="appbana-approval-panel-meta">{`Submitted ${submittedLabel}`}</span>
        )}
        <div className="appbana-approval-panel-actions">
          {target && (
            <Button variant="tertiary" onClick={() => setAuditing(true)}>
              History
            </Button>
          )}
          {action && (
            <Button variant="primary" loading={submitting} onClick={handleSubmit}>
              {action === 'resubmit' ? 'Resubmit for approval' : 'Submit for approval'}
            </Button>
          )}
        </div>
      </div>

      <p className="appbana-approval-panel-hint">{makerHintFor(state)}</p>

      {state === 'REJECTED' && (
        <div className="appbana-approval-rejection" role="note">
          <span className="appbana-approval-rejection-label">Reason for rework</span>
          <p className="appbana-approval-rejection-body">
            {rejectionReason ? String(rejectionReason) : 'No reason was recorded. Open History for the full trail.'}
          </p>
        </div>
      )}

      <AuditDrawer
        open={auditing}
        target={auditing ? target : null}
        recordLabel={recordLabel}
        onClose={() => setAuditing(false)}
      />
    </section>
  );
}
