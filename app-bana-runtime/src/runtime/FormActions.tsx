/**
 * FormActions.tsx — Sticky action bar for entity forms.
 *
 * Runtime UX Overhaul Plan §1.7. Replaces the orphaned undersized Save button
 * that used to sit flush-left at the bottom of every form.
 *
 * Layout: Cancel (tertiary) · Save & New (secondary, optional) · Save (primary)
 * right-aligned inside `.appbana-form-save-cell`, which spans the full grid row.
 *
 * Sprint 3 task 3.8 — every button here is now the unified <Button> primitive
 * so branding + focus rings + disabled state stay uniform across the runtime.
 */
import type { ReactNode } from 'react';
import { Button } from './Button';

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
}: Readonly<FormActionsProps>) {
  return (
    <div className="appbana-form-actions" aria-label="Form actions">
      {children}
      {onCancel && (
        <Button variant="tertiary" onClick={onCancel} disabled={saving}>
          {cancelLabel}
        </Button>
      )}
      {onSaveAndNew && (
        <Button variant="secondary" onClick={onSaveAndNew} disabled={saving}>
          {saveAndNewLabel}
        </Button>
      )}
      <Button type="submit" variant="primary" loading={saving}>
        {saving ? 'Saving…' : saveLabel}
      </Button>
    </div>
  );
}
