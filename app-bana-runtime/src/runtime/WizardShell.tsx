/**
 * WizardShell.tsx — Phase B1. Renders a page as a step-by-step wizard
 * when `PageMeta.layout === 'wizard'`.
 *
 * Structure:
 *   ┌─ progress bar (Step 2 of 4 · titles) ─┐
 *   │  current step subtitle                │
 *   │  ┌─ step fields (rendered by parent)─┐│
 *   │  └───────────────────────────────────┘│
 *   │  [Prev]   [Next] / [Submit]           │
 *   └───────────────────────────────────────┘
 *
 * All fields live in a single `<form>` element so uncontrolled inputs
 * keep their `defaultValue` across step navigation. Non-current-step
 * fields are hidden via CSS (not removed), so FormData at submit time
 * contains every field.
 *
 * Auto-injects a final "Review & Submit" step that summarises entered
 * values grouped by step.
 */
import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import type { WizardStep } from '@appbana/shared';
import { insertEntityRow, ApiFieldError } from '@appbana/shared';
import { buildSchemaFor, type FieldErrors } from './useEntityFormValidation';
import { EntityFormErrorProvider } from './entity-form-context';
import { qualifyEntityKey, getRuntimeToken } from './qualifyEntityKey';
import { toast } from './Toaster';
import { humanizeHeader } from './cell-formatters';
import { Button } from './Button';
import { useWizardDraft, type WizardDraftValues } from './useWizardDraft';
import { FormValuesProvider } from './form-values-context';

export interface WizardShellProps {
  /** Qualified entity key `{tenantId}_{appId}_{Entity}` for the row insert. */
  readonly entity: string;
  /** Author-declared steps. A synthetic Review step is appended automatically. */
  readonly steps: WizardStep[];
  /**
   * Render a single field by its `name`. The parent (Renderer.tsx) supplies
   * this so WizardShell never touches ComponentNode details.
   */
  readonly renderField: (fieldName: string) => ReactElement | null;
  /**
   * Optional label for the parent-form entity (used in toasts / summary).
   * Defaults to the bare entity name from the qualified key.
   */
  readonly entityLabel?: string;
  /** Optional stable id — combined with entity + userId to key the draft. */
  readonly draftId?: string;
}

const REVIEW_STEP_ID = '__appbana_review__';

function toReviewLabel(name: string): string {
  return humanizeHeader(name);
}

/**
 * Read the current form values as a plain map. Used for both the draft
 * snapshot and the Review-step summary.
 */
function snapshotFormValues(form: HTMLFormElement): WizardDraftValues {
  const fd = new FormData(form);
  const out: WizardDraftValues = {};
  for (const [k, v] of fd.entries()) {
    if (typeof v === 'string') out[k] = v;
  }
  return out;
}

/**
 * Validate a subset of the form's controls (those whose `name` is in
 * `fieldNames`). Returns { ok, errors } shaped like useEntityFormValidation.
 */
function validateSubset(
  form: HTMLFormElement,
  fieldNames: string[]
): { ok: true } | { ok: false; errors: FieldErrors; firstBad: string } {
  const nameSet = new Set(fieldNames);
  const { schema, controls } = buildSchemaFor(form);
  const fd = new FormData(form);
  const raw: Record<string, unknown> = {};
  const inScopeControls = controls.filter((c) => nameSet.has((c as HTMLInputElement).name));
  for (const c of inScopeControls) {
    const name = (c as HTMLInputElement).name;
    raw[name] = fd.get(name);
  }
  // Build a sub-schema by picking just the in-scope field rules.
  const subShape: Record<string, unknown> = {};
  for (const name of Object.keys(raw)) {
    subShape[name] = (schema.shape as Record<string, unknown>)[name];
  }
  // Zod object: safeParse against just those keys.
  const result = (schema.pick as (m: Record<string, true>) => typeof schema)(
    Object.fromEntries(Object.keys(raw).map((k) => [k, true as const]))
  ).safeParse(raw);
  if (result.success) return { ok: true };
  const errors: FieldErrors = {};
  let firstBad = '';
  for (const issue of result.error.issues) {
    const field = String(issue.path[0] ?? '');
    if (!field || errors[field]) continue;
    if (!firstBad) firstBad = field;
    errors[field] = issue.message === 'Required' ? `${field} is required` : issue.message;
  }
  return { ok: false, errors, firstBad };
}

export function WizardShell(props: Readonly<WizardShellProps>): ReactElement {
  const { entity, steps, renderField, entityLabel, draftId } = props;
  const totalAuthorSteps = steps.length;
  const totalSteps = totalAuthorSteps + 1; // +1 for review

  const formRef = useRef<HTMLFormElement | null>(null);
  const [stepIdx, setStepIdx] = useState(0);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [saving, setSaving] = useState(false);
  const [reviewValues, setReviewValues] = useState<WizardDraftValues>({});

  const draftKey = useMemo(() => `${entity}:${draftId ?? 'default'}`, [entity, draftId]);
  const draft = useWizardDraft({ key: draftKey });

  // Restore draft into uncontrolled inputs on first mount, once the form
  // element is available in the DOM.
  useEffect(() => {
    const saved = draft.load();
    if (!saved) return;
    const form = formRef.current;
    if (!form) return;
    for (const [name, value] of Object.entries(saved)) {
      const el = form.querySelector<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>(
        `[name="${CSS.escape(name)}"]`
      );
      if (el && 'value' in el) el.value = value;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Debounced draft save on any input change within the form.
  useEffect(() => {
    const form = formRef.current;
    if (!form) return;
    let timer: ReturnType<typeof setTimeout> | null = null;
    const onInput = () => {
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => {
        draft.save(snapshotFormValues(form));
      }, 300);
    };
    form.addEventListener('input', onInput);
    form.addEventListener('change', onInput);
    return () => {
      form.removeEventListener('input', onInput);
      form.removeEventListener('change', onInput);
      if (timer) clearTimeout(timer);
    };
  }, [draft]);

  const isReviewStep = stepIdx === totalAuthorSteps;
  const currentStep: WizardStep | null = isReviewStep ? null : steps[stepIdx];

  const gotoNext = useCallback(() => {
    const form = formRef.current;
    if (!form) return;
    if (currentStep) {
      const result = validateSubset(form, currentStep.fields);
      if (!result.ok) {
        setErrors(result.errors);
        const el = form.querySelector<HTMLElement>(`[name="${CSS.escape(result.firstBad)}"]`);
        el?.focus();
        toast.error('Please fix the highlighted fields on this step');
        return;
      }
    }
    setErrors({});
    setStepIdx((i) => {
      const nextIdx = Math.min(i + 1, totalSteps - 1);
      if (nextIdx === totalAuthorSteps) {
        // Entering the review step — snapshot values for read-only render.
        setReviewValues(snapshotFormValues(form));
      }
      return nextIdx;
    });
  }, [currentStep, totalAuthorSteps, totalSteps]);

  const gotoPrev = useCallback(() => {
    setErrors({});
    setStepIdx((i) => Math.max(0, i - 1));
  }, []);

  const handleSubmitError = useCallback((err: unknown) => {
    if (err instanceof ApiFieldError && Object.keys(err.fieldErrors).length > 0) {
      setErrors(err.fieldErrors);
      const badStepIdx = steps.findIndex((s) => s.fields.some((f) => f in err.fieldErrors));
      if (badStepIdx >= 0) setStepIdx(badStepIdx);
      toast.error('Please fix the highlighted fields', {
        description: err.fieldErrors._form ?? undefined,
      });
      return;
    }
    const msg = err instanceof Error ? err.message : 'Save failed';
    toast.error('Save failed', { description: msg });
  }, [steps]);

  const submit = useCallback(async () => {
    const form = formRef.current;
    if (!form) return;
    // Full-form validation on submit — walk every author step's fields.
    const allFieldNames = steps.flatMap((s) => s.fields);
    const result = validateSubset(form, allFieldNames);
    if (!result.ok) {
      setErrors(result.errors);
      // Jump back to the first step containing an invalid field.
      const badStepIdx = steps.findIndex((s) => s.fields.some((f) => f in result.errors));
      if (badStepIdx >= 0) setStepIdx(badStepIdx);
      toast.error('Please fix the highlighted fields');
      return;
    }
    const payload: Record<string, unknown> = {};
    const fd = new FormData(form);
    for (const name of allFieldNames) {
      const v = fd.get(name);
      payload[name] = v === '' || v == null ? null : v;
    }
    setSaving(true);
    try {
      const qualified = qualifyEntityKey(entity);
      await insertEntityRow(qualified, payload, getRuntimeToken());
      window.dispatchEvent(
        new CustomEvent('appbana:row-inserted', { detail: { entity: qualified } })
      );
      draft.clear();
      toast.success('Saved', {
        description: `New ${entityLabel ?? humanizeHeader(entity.split('_').pop() ?? entity)} added.`,
      });
      // Reset the form + step to allow another entry.
      form.reset();
      setStepIdx(0);
      setReviewValues({});
    } catch (err) {
      handleSubmitError(err);
    } finally {
      setSaving(false);
    }
  }, [entity, entityLabel, steps, draft, handleSubmitError]);

  const percent = Math.round(((stepIdx + 1) / totalSteps) * 100);
  const currentTitle = isReviewStep ? 'Review & Submit' : currentStep!.title;
  const currentSubtitle = isReviewStep
    ? 'Confirm your entries below, then submit.'
    : currentStep?.subtitle;

  const clearFieldError = useCallback((name: string) => {
    setErrors((prev) => {
      if (!(name in prev)) return prev;
      const { [name]: _drop, ...rest } = prev;
      return rest;
    });
  }, []);

  return (
    <EntityFormErrorProvider errors={errors} clearError={clearFieldError}>
      <form
        ref={formRef}
        className="appbana-wizard"
        onSubmit={(e) => e.preventDefault()}
        noValidate
        data-appbana-wizard={entity}
      >
        <FormValuesProvider formRef={formRef}>
        {/* Progress bar + step chips */}
        <div className="mb-6">
          <div className="flex items-center justify-between text-xs text-slate-600 mb-2">
            <span>Step {stepIdx + 1} of {totalSteps}</span>
            <span>{percent}% complete</span>
          </div>
          <div className="h-2 bg-slate-100 rounded-full overflow-hidden">
            <div
              className="h-full bg-indigo-600 transition-all duration-300"
              style={{ width: `${percent}%` }}
              aria-hidden="true"
            />
          </div>
          <ol className="mt-3 flex flex-wrap gap-2" aria-label="Wizard steps">
            {steps.map((s, i) => {
              let chipClass = 'bg-white text-slate-500 border-slate-200';
              if (i === stepIdx) chipClass = 'bg-indigo-600 text-white border-indigo-600';
              else if (i < stepIdx) chipClass = 'bg-indigo-50 text-indigo-700 border-indigo-200 hover:bg-indigo-100';
              return (
                <li key={s.id}>
                  <button
                    type="button"
                    onClick={() => setStepIdx(i)}
                    className={'px-2.5 py-1 text-xs rounded-full border transition ' + chipClass}
                    aria-current={i === stepIdx ? 'step' : undefined}
                  >
                    <span className="inline-block w-4 text-center">{i + 1}</span>
                    <span className="ml-1">{s.title}</span>
                  </button>
                </li>
              );
            })}
            <li>
              <button
                type="button"
                onClick={() => isReviewStep ? undefined : gotoNext()}
                disabled={isReviewStep}
                className={
                  'px-2.5 py-1 text-xs rounded-full border transition ' +
                  (isReviewStep
                    ? 'bg-emerald-600 text-white border-emerald-600'
                    : 'bg-white text-slate-500 border-slate-200')
                }
                aria-current={isReviewStep ? 'step' : undefined}
              >
                <span className="inline-block w-4 text-center">{totalSteps}</span>
                <span className="ml-1">Review</span>
              </button>
            </li>
          </ol>
        </div>

        {/* Current step heading */}
        <div className="mb-4">
          <h2 className="text-lg font-semibold text-slate-900">{currentTitle}</h2>
          {currentSubtitle && (
            <p className="text-sm text-slate-500 mt-0.5">{currentSubtitle}</p>
          )}
        </div>

        {/* Step body: render every field, hide the non-current ones */}
        {steps.map((s, i) => (
          <div
            key={s.id}
            style={{ display: !isReviewStep && i === stepIdx ? 'block' : 'none' }}
            data-appbana-wizard-step={s.id}
          >
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {s.fields.map((fieldName) => (
                <div key={fieldName}>{renderField(fieldName)}</div>
              ))}
            </div>
          </div>
        ))}

        {/* Review step */}
        {isReviewStep && (
          <div className="space-y-4" data-appbana-wizard-step={REVIEW_STEP_ID}>
            {steps.map((s) => (
              <section
                key={s.id}
                className="border border-slate-200 rounded-lg overflow-hidden"
              >
                <header className="bg-slate-50 px-4 py-2 border-b border-slate-200 flex items-center justify-between">
                  <h3 className="text-sm font-semibold text-slate-700">{s.title}</h3>
                  <button
                    type="button"
                    onClick={() => setStepIdx(steps.findIndex((x) => x.id === s.id))}
                    className="text-xs text-indigo-600 hover:text-indigo-800 underline"
                  >
                    Edit
                  </button>
                </header>
                <dl className="divide-y divide-slate-100">
                  {s.fields.map((fname) => {
                    const value = reviewValues[fname];
                    return (
                      <div key={fname} className="px-4 py-2 grid grid-cols-3 gap-2">
                        <dt className="text-xs text-slate-500">{toReviewLabel(fname)}</dt>
                        <dd className="col-span-2 text-sm text-slate-900 break-words">
                          {value && value.trim() !== '' ? value : <span className="text-slate-400">—</span>}
                        </dd>
                      </div>
                    );
                  })}
                </dl>
              </section>
            ))}
          </div>
        )}

        {/* Nav buttons */}
        <div className="mt-6 flex items-center justify-between border-t border-slate-200 pt-4">
          <div>
            <Button
              type="button"
              variant="ghost"
              onClick={gotoPrev}
              disabled={stepIdx === 0 || saving}
            >
              ← Previous
            </Button>
          </div>
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                draft.clear();
                const form = formRef.current;
                if (form) form.reset();
                setStepIdx(0);
                setReviewValues({});
                setErrors({});
                toast.info('Wizard cleared');
              }}
              disabled={saving}
            >
              Cancel
            </Button>
            {!isReviewStep ? (
              <Button type="button" variant="primary" onClick={gotoNext} disabled={saving}>
                Next →
              </Button>
            ) : (
              <Button type="button" variant="primary" onClick={submit} loading={saving}>
                Submit
              </Button>
            )}
          </div>
        </div>
        </FormValuesProvider>
      </form>
    </EntityFormErrorProvider>
  );
}
