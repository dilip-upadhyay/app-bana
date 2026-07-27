/**
 * RejectDialog.tsx — Task C3.4.
 *
 * Prompts a checker for a rejection reason. Built on the native <dialog>
 * element for the same reasons as {@link ConfirmDialog}: focus trap, Esc to
 * close and backdrop behaviour come for free.
 *
 * It cannot reuse ConfirmDialog because the backend requires a non-empty
 * `reason` on reject — that reason is what the maker sees when the record comes
 * back, and it is written to the immutable audit trail. A yes/no confirm has
 * nowhere to put it, and defaulting it to something like "Rejected" would put
 * a meaningless string into a permanent record.
 */
import { useEffect, useRef, useState } from 'react';
import { Button } from './Button';

export interface RejectDialogProps {
  readonly open: boolean;
  /** Shown in the heading, e.g. "Invoice #204". */
  readonly recordLabel?: string;
  readonly submitting?: boolean;
  readonly onCancel: () => void;
  readonly onConfirm: (reason: string) => void;
}

export function RejectDialog({
  open,
  recordLabel,
  submitting = false,
  onCancel,
  onConfirm,
}: Readonly<RejectDialogProps>) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    const dlg = dialogRef.current;
    if (!dlg) return;
    if (open && !dlg.open) {
      setReason('');
      setTouched(false);
      dlg.showModal();
      // Focus the reason box rather than the first button: the reason is the
      // whole point of this dialog, and it is mandatory.
      requestAnimationFrame(() => textareaRef.current?.focus());
    } else if (!open && dlg.open) {
      dlg.close();
    }
  }, [open]);

  useEffect(() => {
    const dlg = dialogRef.current;
    if (!dlg) return;
    const onDialogCancel = (e: Event) => {
      e.preventDefault();
      if (!submitting) onCancel();
    };
    dlg.addEventListener('cancel', onDialogCancel);
    return () => dlg.removeEventListener('cancel', onDialogCancel);
  }, [onCancel, submitting]);

  const trimmed = reason.trim();
  const invalid = trimmed.length === 0;

  function handleConfirm() {
    setTouched(true);
    if (invalid) {
      textareaRef.current?.focus();
      return;
    }
    onConfirm(trimmed);
  }

  return (
    <dialog ref={dialogRef} className="appbana-confirm" aria-labelledby="appbana-reject-title">
      <div className="appbana-confirm-body">
        <h2 id="appbana-reject-title" className="appbana-confirm-title">
          {recordLabel ? `Reject ${recordLabel}?` : 'Reject this record?'}
        </h2>
        <p className="appbana-confirm-message">
          The maker sees this reason and can revise and resubmit. It is recorded
          permanently in the approval history.
        </p>

        <label htmlFor="appbana-reject-reason" className="appbana-field-label">
          Reason for rejection
        </label>
        <textarea
          id="appbana-reject-reason"
          ref={textareaRef}
          className="appbana-textarea"
          rows={3}
          value={reason}
          disabled={submitting}
          onChange={(e) => setReason(e.target.value)}
          onBlur={() => setTouched(true)}
          aria-invalid={touched && invalid ? true : undefined}
          aria-describedby={touched && invalid ? 'appbana-reject-error' : undefined}
          placeholder="What needs to change before this can be approved?"
        />
        {touched && invalid && (
          <p id="appbana-reject-error" className="appbana-field-error" role="alert">
            A reason is required.
          </p>
        )}

        <div className="appbana-confirm-actions">
          <Button variant="tertiary" onClick={onCancel} disabled={submitting}>
            Cancel
          </Button>
          <Button variant="danger" onClick={handleConfirm} loading={submitting} disabled={submitting}>
            {submitting ? 'Rejecting…' : 'Reject'}
          </Button>
        </div>
      </div>
    </dialog>
  );
}
