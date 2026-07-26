/**
 * useEntityFormValidation.test.ts — Sprint 2 Task 2.5.
 *
 * Direct coverage for the pure `ruleForControl` helper used by the entity-form
 * validator. `ruleForControl` reads a control's DOM/data attributes via
 * duck-typed property access (no `instanceof` check), so we can hand it
 * plain objects here and stay in Node without pulling in jsdom.
 */
import { describe, it, expect } from 'vitest';
import { ruleForControl } from './useEntityFormValidation';

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
