/**
 * FormActions.test.tsx — Phase C3.2.
 *
 * Rendered with react-dom/server; assertions are on the emitted markup.
 * The point of most of these is regression safety: the approval layout is
 * opt-in and must not change what existing (non-approval) forms render.
 */
import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { FormActions } from './FormActions';

const noop = () => {};

describe('FormActions — default (non-approval) bar', () => {
  it('renders a single primary Save', () => {
    const html = renderToStaticMarkup(<FormActions saving={false} />);
    expect(html).toContain('appbana-btn-primary');
    expect(html).toContain('Save');
    expect(html).not.toContain('Submit for approval');
    expect(html).not.toContain('Save as draft');
  });

  it('still offers Save & Add another when asked', () => {
    const html = renderToStaticMarkup(<FormActions saving={false} onSaveAndNew={noop} />);
    expect(html).toContain('Save &amp; Add another');
  });

  it('shows a saving label while in flight', () => {
    const html = renderToStaticMarkup(<FormActions saving />);
    expect(html).toContain('Saving…');
  });

  it('is unaffected by approval-only props when approvalMode is off', () => {
    const html = renderToStaticMarkup(
      <FormActions saving={false} onSaveDraft={noop} pendingAction="draft" />
    );
    expect(html).not.toContain('Save as draft');
    expect(html).toContain('Save');
  });
});

describe('FormActions — approval bar', () => {
  it('makes Submit for approval the primary action', () => {
    const html = renderToStaticMarkup(
      <FormActions saving={false} approvalMode onSaveDraft={noop} />
    );
    expect(html).toContain('Submit for approval');
    expect(html).toContain('appbana-btn-primary');
  });

  it('renders Save as draft as a secondary action', () => {
    const html = renderToStaticMarkup(
      <FormActions saving={false} approvalMode onSaveDraft={noop} />
    );
    expect(html).toContain('Save as draft');
    expect(html).toContain('appbana-btn-secondary');
  });

  it('keeps the primary button a native submit so Enter matches the label', () => {
    const html = renderToStaticMarkup(
      <FormActions saving={false} approvalMode onSaveDraft={noop} />
    );
    expect(html).toContain('type="submit"');
  });

  it('drops Save & Add another — a third save verb makes the primary ambiguous', () => {
    const html = renderToStaticMarkup(
      <FormActions saving={false} approvalMode onSaveDraft={noop} onSaveAndNew={noop} />
    );
    expect(html).not.toContain('Add another');
  });

  it('spins only the draft button when a draft save is in flight', () => {
    const html = renderToStaticMarkup(
      <FormActions saving approvalMode onSaveDraft={noop} pendingAction="draft" />
    );
    expect(html).toContain('Saving…');
    expect(html).not.toContain('Submitting…');
  });

  it('spins only the submit button when a submission is in flight', () => {
    const html = renderToStaticMarkup(
      <FormActions saving approvalMode onSaveDraft={noop} pendingAction="submit" />
    );
    expect(html).toContain('Submitting…');
    expect(html).not.toContain('Saving…');
  });

  it('disables every action while saving so a double-submit cannot race', () => {
    const html = renderToStaticMarkup(
      <FormActions saving approvalMode onSaveDraft={noop} onCancel={noop} pendingAction="submit" />
    );
    const disabledCount = (html.match(/disabled/g) ?? []).length;
    expect(disabledCount).toBeGreaterThanOrEqual(3);
  });

  it('still renders Cancel when provided', () => {
    const html = renderToStaticMarkup(
      <FormActions saving={false} approvalMode onSaveDraft={noop} onCancel={noop} />
    );
    expect(html).toContain('Cancel');
    expect(html).toContain('appbana-btn-tertiary');
  });

  it('honours custom labels', () => {
    const html = renderToStaticMarkup(
      <FormActions
        saving={false}
        approvalMode
        onSaveDraft={noop}
        submitForApprovalLabel="Send to reviewer"
        saveDraftLabel="Keep working"
      />
    );
    expect(html).toContain('Send to reviewer');
    expect(html).toContain('Keep working');
  });
});
