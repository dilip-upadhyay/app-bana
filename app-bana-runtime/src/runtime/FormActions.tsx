/**
 * FormActions.tsx — Sticky action bar for entity forms.
 *
 * Runtime UX Overhaul Plan §1.7. Replaces the orphaned undersized Save button
 * that used to sit flush-left at the bottom of every form.
 *
 * Layout: Cancel (tertiary) · Save & New (secondary, optional) · Save (primary)
 * right-aligned inside `.appbana-form-save-cell`, which spans the full grid row.
 */
import type { ReactNode } from 'react';

interface FormActionsProps {
  readonly saving: boolean;
  readonly onCancel?: () => void;
  readonly onSaveAndNew?: () => void;
  readonly saveLabel?: string;
  readonly cancelLabel?: string;
  readonly saveAndNewLabel?: string;
  readonly children?: ReactNode;
}

export function FormActions({
  saving,
  onCancel,
  onSaveAndNew,
  saveLabel = 'Save',
  cancelLabel = 'Cancel',
  saveAndNewLabel = 'Save & Add another',
  children,
}: FormActionsProps) {
  return (
    <div className="appbana-form-actions" aria-label="Form actions">
      {children}
      {onCancel && (
        <button type="button" className="tertiary" onClick={onCancel} disabled={saving}>
          {cancelLabel}
        </button>
      )}
      {onSaveAndNew && (
        <button type="button" className="secondary" onClick={onSaveAndNew} disabled={saving}>
          {saveAndNewLabel}
        </button>
      )}
      <button type="submit" className="primary" disabled={saving} aria-busy={saving}>
        {saving && (
          <svg
            className="animate-spin -ml-0.5 mr-1 h-4 w-4"
            viewBox="0 0 24 24"
            fill="none"
            aria-hidden="true"
          >
            <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" opacity="0.25" />
            <path d="M4 12a8 8 0 018-8" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
          </svg>
        )}
        {saving ? 'Saving…' : saveLabel}
      </button>
    </div>
  );
}
