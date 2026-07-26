/**
 * applyBrandRamp — Sprint 3 post-review fix.
 *
 * <p>Two components (AppRuntimeShell, LoginPage) were hand-writing the same
 * five `document.documentElement.style.setProperty(...)` calls with the same
 * `color-mix(in srgb, ${brand} X%, black|white)` recipes to derive
 * hover/active/soft/on-soft shades from a single tenant primary colour.
 * That duplication meant every tweak (blend %, base direction, adding a new
 * ramp step) had to be applied in two places — and one of them silently
 * drifted during the Sprint 3 flurry.
 *
 * <p>This helper centralises the ramp so the surface components only decide
 * <em>when</em> to apply/reset it; the recipe itself lives once.
 *
 * <p><b>Browser support:</b> `color-mix()` ships in Chrome 111+, Firefox
 * 113+, Safari 16.4+. Older browsers ignore the invalid values and fall
 * back to whatever `:root` in globals.css already declares for
 * `--color-brand-*` — visually the whole ramp collapses to the base
 * `--color-brand`, which is acceptable degradation.
 */

const RAMP_VARS = [
  '--color-brand',
  '--color-brand-hover',
  '--color-brand-active',
  '--color-brand-soft',
  '--color-brand-on-soft',
] as const;

/**
 * Apply the tint ramp to `document.documentElement`.
 * Pass `null` / empty to reset to the defaults declared in globals.css.
 * Safe to call in SSR — no-ops if `document` is undefined.
 */
export function applyBrandRamp(brand: string | null | undefined): void {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  const value = brand?.trim();
  if (!value) {
    RAMP_VARS.forEach((v) => root.style.removeProperty(v));
    return;
  }
  root.style.setProperty('--color-brand', value);
  // Darker shades for hover/active — 12% and 24% blended toward black.
  root.style.setProperty('--color-brand-hover',   `color-mix(in srgb, ${value} 88%, black)`);
  root.style.setProperty('--color-brand-active',  `color-mix(in srgb, ${value} 76%, black)`);
  // Softer tinted background for filled-tonal / soft variants.
  root.style.setProperty('--color-brand-soft',    `color-mix(in srgb, ${value} 12%, white)`);
  root.style.setProperty('--color-brand-on-soft', `color-mix(in srgb, ${value} 76%, black)`);
}
