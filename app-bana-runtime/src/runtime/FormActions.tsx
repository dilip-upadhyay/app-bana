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
 *
 * Phase C3.2 — entities with the maker-checker workflow enabled get a second
 * layout: Cancel · Save as draft · Submit for approval. It is opt-in via
 * `approvalMode`, so every existing caller keeps the original bar.
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
  /**
   * C3.2 — switches the bar into approval mode: the primary (and form-default)
   * action becomes "Submit for approval", and saving without submitting moves
   * to a secondary "Save as draft" button.
   *
   * Submit is primary because it is the maker's actual goal — a draft nobody
   * submits is invisible to the workflow. Draft stays one click away for the
   * half-finished case.
   *
   * There is no `onSubmitForApproval` callback: the primary button is a native
   * `type="submit"`, so the owning form's `onSubmit` already handles it and
   * Enter does the same thing the button says.
   */
  readonly approvalMode?: boolean;
  /** Save without entering the workflow. Only rendered in approval mode. */
  readonly onSaveDraft?: () => void;
  readonly submitForApprovalLabel?: string;
  readonly saveDraftLabel?: string;
  /**
   * Narrows the disabled/loading treatment so only the button the user pressed
   * shows a spinner. Managed by the caller.
   */
  readonly pendingAction?: 'draft' | 'submit' | null;
}

export function FormActions({
  saving,
  onCancel,
  onSaveAndNew,
  saveLabel = 'Save',
  cancelLabel = 'Cancel',
  saveAndNewLabel = 'Save & Add another',
  children,
  approvalMode = false,
  onSaveDraft,
  submitForApprovalLabel = 'Submit for approval',
  saveDraftLabel = 'Save as draft',
  pendingAction = null,
}: Readonly<FormActionsProps>) {
  if (approvalMode) {
    // "Save & Add another" is deliberately dropped here: with two save
    // semantics already on the bar a third would make the primary action
    // ambiguous, which is the one thing an approval form cannot afford.
    return (
      <div className="appbana-form-actions" aria-label="Form actions">
        {children}
        {onCancel && (
          <Button variant="tertiary" onClick={onCancel} disabled={saving}>
            {cancelLabel}
          </Button>
        )}
        {onSaveDraft && (
          <Button
            variant="secondary"
            onClick={onSaveDraft}
            disabled={saving}
            loading={saving && pendingAction === 'draft'}
          >
            {saving && pendingAction === 'draft' ? 'Saving…' : saveDraftLabel}
          </Button>
        )}
        <Button
          type="submit"
          variant="primary"
          disabled={saving}
          loading={saving && pendingAction !== 'draft'}
        >
          {saving && pendingAction !== 'draft' ? 'Submitting…' : submitForApprovalLabel}
        </Button>
      </div>
    );
  }

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
