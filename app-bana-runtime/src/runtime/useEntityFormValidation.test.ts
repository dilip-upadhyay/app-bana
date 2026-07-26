/**
 * useEntityFormValidation.test.ts — Sprint 2 Task 2.5.
 *
 * Direct coverage for the pure `ruleForControl` helper used by the entity-form
 * validator. `ruleForControl` reads a control's DOM/data attributes via
 * duck-typed property access (no `instanceof` check), so we can hand it
 * plain objects here and stay in Node without pulling in jsdom.
 */
import { describe, it, expect } from 'vitest';
import { ruleForControl, isEffectivelyVisible } from './useEntityFormValidation';

/** Minimal object shape mirroring the fields ruleForControl reads. */
function makeInput(overrides: {
  type?: string;
  required?: boolean;
  min?: string;
  max?: string;
  minLength?: number;
  maxLength?: number;
  pattern?: string;
  format?: string;
} = {}): HTMLElement {
  const el = {
    type: overrides.type ?? 'text',
    required: overrides.required ?? false,
    min: overrides.min ?? '',
    max: overrides.max ?? '',
    minLength: overrides.minLength ?? 0,
    maxLength: overrides.maxLength ?? 0,
    pattern: overrides.pattern ?? '',
    dataset: overrides.format ? { appbanaFormat: overrides.format } : {},
  };
  return el as unknown as HTMLElement;
}

describe('ruleForControl', () => {
  it('required text field rejects empty string', () => {
    const rule = ruleForControl(makeInput({ required: true }));
    const result = rule.safeParse('');
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].message).toBe('This field is required');
    }
  });

  it('optional text field accepts empty string', () => {
    const rule = ruleForControl(makeInput({ required: false }));
    expect(rule.safeParse('').success).toBe(true);
    expect(rule.safeParse('hello').success).toBe(true);
  });

  it('email input rejects garbage and accepts a valid address', () => {
    const rule = ruleForControl(makeInput({ type: 'email', required: true }));
    expect(rule.safeParse('not-an-email').success).toBe(false);
    expect(rule.safeParse('alice@example.com').success).toBe(true);
  });

  it('email input via data-appbana-format hint works for type=text', () => {
    const rule = ruleForControl(makeInput({ type: 'text', required: true, format: 'email' }));
    expect(rule.safeParse('foo').success).toBe(false);
    expect(rule.safeParse('foo@bar.co').success).toBe(true);
  });

  it('phone format hint enforces the 7-character digit-ish pattern', () => {
    const rule = ruleForControl(makeInput({ type: 'text', required: true, format: 'phone' }));
    expect(rule.safeParse('abc').success).toBe(false);
    expect(rule.safeParse('+1 (555) 123-4567').success).toBe(true);
  });

  it('numeric input honours min and max', () => {
    const rule = ruleForControl(makeInput({ type: 'number', required: true, min: '5', max: '10' }));
    expect(rule.safeParse('4').success).toBe(false);
    expect(rule.safeParse('11').success).toBe(false);
    expect(rule.safeParse('7').success).toBe(true);
  });

  it('minLength / maxLength constraints are enforced for text', () => {
    const rule = ruleForControl(makeInput({ type: 'text', required: true, minLength: 3, maxLength: 5 }));
    expect(rule.safeParse('ab').success).toBe(false);
    expect(rule.safeParse('abcdef').success).toBe(false);
    expect(rule.safeParse('abcd').success).toBe(true);
  });

  it('pattern (regex) constraint is enforced', () => {
    const rule = ruleForControl(makeInput({ type: 'text', required: true, pattern: '[A-Z]{3}' }));
    expect(rule.safeParse('abc').success).toBe(false);
    expect(rule.safeParse('ABC').success).toBe(true);
  });

  it('required checkbox demands "on" state', () => {
    const rule = ruleForControl(makeInput({ type: 'checkbox', required: true }));
    // FormData gives 'on' when checked, absence (empty string) when not.
    expect(rule.safeParse('on').success).toBe(true);
    expect(rule.safeParse('').success).toBe(false);
  });
});

/**
 * H5 hardening — hidden fields (via `type="hidden"`, inline `display:none`
 * on an ancestor, `hidden` attribute, `aria-hidden="true"`, or the runtime's
 * own `data-appbana-hidden`) must not participate in validation, so a
 * required-but-hidden field never blocks form submit.
 */
type FakeEl = {
  type?: string;
  hidden?: boolean;
  style?: Partial<CSSStyleDeclaration>;
  dataset?: Record<string, string>;
  parentElement?: FakeEl | null;
  attrs?: Record<string, string>;
  getAttribute?: (n: string) => string | null;
};

function fakeEl(overrides: Partial<FakeEl> = {}): FakeEl {
  const attrs = overrides.attrs ?? {};
  return {
    type: overrides.type ?? 'text',
    hidden: overrides.hidden ?? false,
    style: overrides.style ?? {},
    dataset: overrides.dataset ?? {},
    parentElement: overrides.parentElement ?? null,
    attrs,
    getAttribute: (n: string) => attrs[n] ?? null,
  };
}

describe('isEffectivelyVisible', () => {
  it('plain text input inside a form is visible', () => {
    const form = fakeEl({});
    const input = fakeEl({ parentElement: form });
    expect(isEffectivelyVisible(input as unknown as HTMLElement, form as unknown as HTMLFormElement)).toBe(true);
  });

  it('type="hidden" is always invisible', () => {
    const form = fakeEl({});
    const input = fakeEl({ type: 'hidden', parentElement: form });
    expect(isEffectivelyVisible(input as unknown as HTMLElement, form as unknown as HTMLFormElement)).toBe(false);
  });

  it('input with hidden attribute is invisible', () => {
    const form = fakeEl({});
    const input = fakeEl({ hidden: true, parentElement: form });
    expect(isEffectivelyVisible(input as unknown as HTMLElement, form as unknown as HTMLFormElement)).toBe(false);
  });

  it('ancestor with display:none hides the input (WizardShell inactive step)', () => {
    const form = fakeEl({});
    const step = fakeEl({ style: { display: 'none' }, parentElement: form });
    const input = fakeEl({ parentElement: step });
    expect(isEffectivelyVisible(input as unknown as HTMLElement, form as unknown as HTMLFormElement)).toBe(false);
  });

  it('ancestor with data-appbana-hidden="true" hides the input', () => {
    const form = fakeEl({});
    const wrapper = fakeEl({ dataset: { appbanaHidden: 'true' }, parentElement: form });
    const input = fakeEl({ parentElement: wrapper });
    expect(isEffectivelyVisible(input as unknown as HTMLElement, form as unknown as HTMLFormElement)).toBe(false);
  });

  it('ancestor with aria-hidden="true" hides the input', () => {
    const form = fakeEl({});
    const wrapper = fakeEl({ attrs: { 'aria-hidden': 'true' }, parentElement: form });
    const input = fakeEl({ parentElement: wrapper });
    expect(isEffectivelyVisible(input as unknown as HTMLElement, form as unknown as HTMLFormElement)).toBe(false);
  });

  it('stops walking at the form element (siblings outside form do not affect us)', () => {
    // form has display:none, but we stop AT the form — however the form itself
    // is one of the checked nodes, so an entirely-hidden form is still hidden.
    const form = fakeEl({ style: { display: 'none' } });
    const input = fakeEl({ parentElement: form });
    expect(isEffectivelyVisible(input as unknown as HTMLElement, form as unknown as HTMLFormElement)).toBe(false);
  });

  it('deeply nested visible input with all-visible ancestors is visible', () => {
    const form = fakeEl({});
    const grid = fakeEl({ parentElement: form });
    const cell = fakeEl({ parentElement: grid });
    const wrapper = fakeEl({ parentElement: cell });
    const input = fakeEl({ parentElement: wrapper });
    expect(isEffectivelyVisible(input as unknown as HTMLElement, form as unknown as HTMLFormElement)).toBe(true);
  });
});
