/**
 * approval-toasts.ts — Task C3.8.
 *
 * Turns an approval failure into a sentence a non-technical user can act on.
 *
 * The interesting case is a failure *after* the row was already inserted. The
 * insert and the workflow transition are two separate calls (C3.2), so the
 * record exists as a draft even when the submit fails. Reporting that as "Save
 * failed" would be wrong in the way that matters: the user would believe their
 * typing was lost and retype it, creating a duplicate.
 */
import { ApprovalConflictError } from '@appbana/shared';

/**
 * Explain why a submit-for-approval did not go through. Always ends in a full
 * stop so it can be concatenated with a reassurance about the saved draft.
 */
export function describeSubmitFailure(err: unknown): string {
  if (err instanceof ApprovalConflictError) {
    return 'It looks like it was already submitted.';
  }

  const status = (err as { status?: number })?.status;
  if (status === 403) {
    return 'You do not have permission to submit this for approval.';
  }
  if (status === 401) {
    return 'Your session expired before it could be submitted.';
  }

  const message = err instanceof Error ? err.message.trim() : '';
  if (!message) return 'It could not be submitted for approval.';
  return /[.!?]$/.test(message) ? message : `${message}.`;
}
