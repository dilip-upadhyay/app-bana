/**
 * Button.tsx — Sprint 3 task 3.8.
 *
 * Unified button primitive for the runtime. Replaces the four competing
 * implementations the architect review surfaced:
 *
 *   1. `.appbana-button` (+ `.secondary` / `.danger` / `.ghost`) — legacy
 *      MD3 filled tonal, still consumed by scaffolder-generated buttons.
 *   2. `.appbana-form-actions .primary` / `.secondary` / `.tertiary` —
 *      duplicated rules that produced subtly different Save chrome.
 *   3. `LoginPage` inline styles.
 *   4. `.appbana-empty-state-cta` — bespoke indigo button per CTA.
 *
 * All new call sites use this component. `variant` picks the visual
 * treatment, `size` picks the padding scale, `loading` swaps the label for
 * a spinner + aria-busy. The rendered element is a native <button>, so
 * type=submit / type=button semantics work as expected and the disabled
 * attribute cannot be overridden by className leaks.
 *
 * Related tokens: `--color-brand`, `--color-brand-hover`, `--color-brand-soft`
 * — task 3.9 wires these through `TenantBranding.primaryColor` so every
 * primary/secondary variant re-tints when the tenant colour changes.
 */
import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';

export type ButtonVariant = 'primary' | 'secondary' | 'tertiary' | 'danger' | 'ghost';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'type'> {
  readonly variant?: ButtonVariant;
  readonly size?: ButtonSize;
  /** When true, disables the button and swaps the label for a spinner. */
  readonly loading?: boolean;
  /** Native button type — defaults to `button` so uncaptured clicks don't submit forms. */
  readonly type?: 'button' | 'submit' | 'reset';
  /** Icon rendered before the label. Ignored while loading. */
  readonly icon?: ReactNode;
  readonly children?: ReactNode;
}

const CLASS_BY_VARIANT: Record<ButtonVariant, string> = {
  primary:   'appbana-btn appbana-btn-primary',
  secondary: 'appbana-btn appbana-btn-secondary',
  tertiary:  'appbana-btn appbana-btn-tertiary',
  danger:    'appbana-btn appbana-btn-danger',
  ghost:     'appbana-btn appbana-btn-ghost',
};

const CLASS_BY_SIZE: Record<ButtonSize, string> = {
  sm: 'appbana-btn-sm',
  md: 'appbana-btn-md',
  lg: 'appbana-btn-lg',
};

function Spinner() {
  return (
    <svg
      className="animate-spin h-4 w-4"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" opacity="0.25" />
      <path d="M4 12a8 8 0 018-8" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  );
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = 'primary',
    size = 'md',
    loading = false,
    type = 'button',
    icon,
    disabled,
    className,
    children,
    ...rest
  },
  ref,
) {
  const cls = [
    CLASS_BY_VARIANT[variant],
    CLASS_BY_SIZE[size],
    className ?? '',
  ]
    .filter(Boolean)
    .join(' ');
  return (
    <button
      ref={ref}
      type={type}
      className={cls}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? <Spinner /> : icon}
      {children != null && <span>{children}</span>}
    </button>
  );
});
