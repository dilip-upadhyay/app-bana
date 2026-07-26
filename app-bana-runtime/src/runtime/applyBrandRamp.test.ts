/**
 * applyBrandRamp.test.ts — Sprint 3 post-review coverage.
 *
 * Verifies the extracted helper writes the five `--color-brand*` CSS custom
 * properties and clears them on reset. We fake a tiny DOM stub so the test
 * runs in the same jsdom-free environment as the rest of the runtime suite.
 */
import { afterEach, describe, expect, it } from 'vitest';
import { applyBrandRamp } from './applyBrandRamp';

interface StubStyle {
  setProperty(name: string, value: string): void;
  removeProperty(name: string): void;
  __map: Map<string, string>;
}

function makeStubStyle(): StubStyle {
  const map = new Map<string, string>();
  return {
    __map: map,
    setProperty(name, value) { map.set(name, value); },
    removeProperty(name) { map.delete(name); },
  };
}

function installFakeDocument(): StubStyle {
  const style = makeStubStyle();
  (globalThis as { document?: unknown }).document = {
    documentElement: { style },
  };
  return style;
}

afterEach(() => {
  delete (globalThis as { document?: unknown }).document;
});

describe('applyBrandRamp', () => {
  it('sets all five brand tokens for a valid hex colour', () => {
    const style = installFakeDocument();
    applyBrandRamp('#3366ff');

    expect(style.__map.get('--color-brand')).toBe('#3366ff');
    expect(style.__map.get('--color-brand-hover'))
      .toBe('color-mix(in srgb, #3366ff 88%, black)');
    expect(style.__map.get('--color-brand-active'))
      .toBe('color-mix(in srgb, #3366ff 76%, black)');
    expect(style.__map.get('--color-brand-soft'))
      .toBe('color-mix(in srgb, #3366ff 12%, white)');
    expect(style.__map.get('--color-brand-on-soft'))
      .toBe('color-mix(in srgb, #3366ff 76%, black)');
  });

  it('trims whitespace from the input before writing', () => {
    const style = installFakeDocument();
    applyBrandRamp('  #ff00aa  ');
    expect(style.__map.get('--color-brand')).toBe('#ff00aa');
  });

  it.each([null, undefined, '', '   '])('removes all tokens when brand is %p', (brand) => {
    const style = installFakeDocument();
    style.__map.set('--color-brand', '#aaaaaa');
    style.__map.set('--color-brand-hover', 'x');
    style.__map.set('--color-brand-active', 'x');
    style.__map.set('--color-brand-soft', 'x');
    style.__map.set('--color-brand-on-soft', 'x');

    applyBrandRamp(brand);

    expect(style.__map.get('--color-brand')).toBeUndefined();
    expect(style.__map.get('--color-brand-hover')).toBeUndefined();
    expect(style.__map.get('--color-brand-active')).toBeUndefined();
    expect(style.__map.get('--color-brand-soft')).toBeUndefined();
    expect(style.__map.get('--color-brand-on-soft')).toBeUndefined();
  });

  it('is a no-op when document is undefined (SSR safety)', () => {
    // No fake document installed — should not throw.
    expect(() => applyBrandRamp('#123456')).not.toThrow();
  });
});
