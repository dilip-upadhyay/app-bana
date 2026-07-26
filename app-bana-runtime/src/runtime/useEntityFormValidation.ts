/**
 * useEntityFormValidation.ts — Sprint 2 Task 2.5.
 *
 * Client-side inline validation for the deployed runtime's entity forms.
 * We derive a Zod schema on the fly from the form's native `HTMLFormElement`
 * — every registered input contributes a Zod rule based on its `type`,
 * `required`, `min`, `max`, `pattern`, `minLength`, `maxLength`, and any
 * `data-appbana-format` hint (`email`, `phone`, `url`).
 *
 * Why Zod on the DOM and not react-hook-form:
 *   Our field renderers in Renderer.tsx are uncontrolled — the tree is
 *   assembled from scaffold JSON and inputs use `defaultValue`. Value
 *   readback happens once, at submit time, via FormData. RHF's `register`
 *   pattern would require wiring every field renderer to a form-context
 *   ref-callback and is a bigger refactor than the value it adds today.
 *   Zod alone gives us the plan-mandated "schema derived from entity
 *   metadata" plus per-field errors with `aria-invalid`, wrapped in a
 *   ~150 LOC hook. RHF-backed controlled forms remain a future upgrade
 *   path when cross-field validation or live formatting is needed.
 */
import { useCallback, useMemo, useState } from 'react';
import { z, type ZodType } from 'zod';

export type FieldErrors = Record<string, string>;

export interface UseEntityFormValidation {
  readonly errors: FieldErrors;
  readonly validate: (form: HTMLFormElement) => { ok: true; data: Record<string, unknown> } | { ok: false; errors: FieldErrors };
  readonly clearError: (name: string) => void;
  readonly resetErrors: () => void;
  /**
   * Sprint 3 task 3.1 — Inject server-side field errors (from a 400
   * response) into the same error store that client-side Zod errors flow
   * through. Merges with the existing errors map so a form that already
   * has one bad field can accept another from the server. Fields not in
   * the payload are left untouched.
   */
  readonly setExternalErrors: (fieldErrors: FieldErrors) => void;
}

/** Read all named form controls in submission order (excludes buttons). */
function collectControls(form: HTMLFormElement): HTMLElement[] {
  const controls: HTMLElement[] = [];
  const seen = new Set<string>();
  for (const el of Array.from(form.elements)) {
    if (!(el instanceof HTMLInputElement || el instanceof HTMLSelectElement || el instanceof HTMLTextAreaElement)) continue;
    if (!el.name) continue;
    if (el.disabled) continue;
    // Skip submit/reset/button-style inputs.
    if (el instanceof HTMLInputElement && (el.type === 'submit' || el.type === 'reset' || el.type === 'button' || el.type === 'hidden' && el.dataset.appbanaValidate === 'skip')) continue;
    if (seen.has(el.name)) continue;
    seen.add(el.name);
    controls.push(el);
  }
  return controls;
}

/** Turn a single control's HTML/data metadata into a Zod rule. */
export function ruleForControl(el: HTMLElement): ZodType {
  const type = ((el as HTMLInputElement).type ?? '').toLowerCase();
  const required = (el as HTMLInputElement).required === true;
  const format = (el as HTMLElement).dataset.appbanaFormat?.toLowerCase();

  // Numeric-ish inputs.
  if (type === 'number' || type === 'range') {
    const input = el as HTMLInputElement;
    let rule: z.ZodTypeAny = z.coerce.number({ error: 'Enter a number' });
    if (input.min !== '') rule = (rule as z.ZodNumber).min(Number(input.min), { message: `Must be at least ${input.min}` });
    if (input.max !== '') rule = (rule as z.ZodNumber).max(Number(input.max), { message: `Must be at most ${input.max}` });
    return (required ? rule : rule.optional().or(z.literal('').transform(() => undefined))) as ZodType;
  }

  // Checkbox = boolean.
  if (type === 'checkbox') {
    if (required) return z.literal('on', { error: 'This box must be checked' }) as unknown as ZodType;
    return z.string().optional() as ZodType;
  }

  // Everything else = string with length + pattern + format rules.
  let rule: z.ZodString = z.string();
  const input = el as HTMLInputElement;

  if (input.minLength > 0) {
    rule = rule.min(input.minLength, { message: `Must be at least ${input.minLength} character${input.minLength === 1 ? '' : 's'}` });
  }
  if (input.maxLength > 0) {
    rule = rule.max(input.maxLength, { message: `Must be at most ${input.maxLength} character${input.maxLength === 1 ? '' : 's'}` });
  }
  if (input.pattern) {
    try {
      rule = rule.regex(new RegExp(`^(?:${input.pattern})$`), { message: 'Doesn\u2019t match the required format' });
    } catch {
      // Invalid regex on the input — ignore rather than blowing up the form.
    }
  }
  if (type === 'email' || format === 'email') {
    rule = rule.email({ message: 'Enter a valid email address' });
  }
  if (type === 'url' || format === 'url') {
    rule = rule.url({ message: 'Enter a valid URL' });
  }
  if (format === 'phone') {
    rule = rule.regex(/^[+()\-\s\d]{7,}$/, { message: 'Enter a valid phone number' });
  }

  if (required) {
    return rule.min(1, { message: 'This field is required' }) as ZodType;
  }
  // Optional: allow empty string.
  return rule.optional().or(z.literal('')) as unknown as ZodType;
}

/** Build a `z.object({...})` schema for every named control in the form. */
export function buildSchemaFor(form: HTMLFormElement): {
  schema: z.ZodObject<Record<string, ZodType>>;
  controls: HTMLElement[];
} {
  const controls = collectControls(form);
  const shape: Record<string, ZodType> = {};
  for (const c of controls) {
    const name = (c as HTMLInputElement).name;
    if (!name) continue;
    shape[name] = ruleForControl(c);
  }
  return { schema: z.object(shape), controls };
}

/** Get a friendly human label for an input — used as the fallback error prefix. */
function labelFor(el: HTMLElement, form: HTMLFormElement): string {
  const id = (el as HTMLInputElement).id;
  if (id) {
    const label = form.querySelector<HTMLLabelElement>(`label[for="${CSS.escape(id)}"]`);
    if (label?.textContent) return label.textContent.replace(/\*\s*$/, '').trim();
  }
  return (el as HTMLInputElement).name;
}

export function useEntityFormValidation(): UseEntityFormValidation {
  const [errors, setErrors] = useState<FieldErrors>({});

  const validate = useCallback<UseEntityFormValidation['validate']>((form) => {
    const { schema, controls } = buildSchemaFor(form);
    const fd = new FormData(form);
    const raw: Record<string, unknown> = {};
    for (const c of controls) {
      const name = (c as HTMLInputElement).name;
      raw[name] = fd.get(name);
    }
    const result = schema.safeParse(raw);
    if (result.success) {
      setErrors({});
      return { ok: true, data: result.data as Record<string, unknown> };
    }
    const next: FieldErrors = {};
    for (const issue of result.error.issues) {
      const field = String(issue.path[0] ?? '');
      if (!field || next[field]) continue;
      // Prefix generic messages with the field label for context.
      const control = controls.find((c) => (c as HTMLInputElement).name === field);
      const label = control ? labelFor(control, form) : field;
      next[field] = issue.message === 'Required'
        ? `${label} is required`
        : issue.message;
    }
    setErrors(next);
    return { ok: false, errors: next };
  }, []);

  const clearError = useCallback((name: string) => {
    setErrors((prev) => {
      if (!(name in prev)) return prev;
      const { [name]: _drop, ...rest } = prev;
      return rest;
    });
  }, []);

  const resetErrors = useCallback(() => setErrors({}), []);

  const setExternalErrors = useCallback((fieldErrors: FieldErrors) => {
    setErrors((prev) => ({ ...prev, ...fieldErrors }));
  }, []);

  return useMemo(
    () => ({ errors, validate, clearError, resetErrors, setExternalErrors }),
    [errors, validate, clearError, resetErrors, setExternalErrors],
  );
}
