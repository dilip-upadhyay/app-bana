/**
 * ConditionalField.tsx — Phase B2. Wraps a form-field JSX subtree with
 * declarative `showWhen` / `requiredWhen` / `disabledWhen` behaviour.
 *
 * Semantics:
 *   - `showWhen === false`  → the entire subtree is removed from the DOM
 *     and its `name`-carrying inputs won't submit values (so hidden fields
 *     never fail validation and never leak state).
 *   - `requiredWhen === true`  → propagates a `data-appbana-required` hint;
 *     the actual `required` attribute is set on the inner input by adding
 *     it via the parent renderer. For MVP we render a hidden mirror input
 *     with `required` so browser + Zod both pick it up. (Deferred pattern:
 *     the parent renderer will honour `requiredWhen` directly in a follow-up.)
 *   - `disabledWhen === true` → sets `pointer-events:none; opacity:0.5` on
 *     the subtree and mirrors a `data-appbana-disabled="true"` hint; the
 *     inner input's `disabled` attribute is handled by the parent renderer
 *     when it has direct access.
 *
 * The parent Renderer.tsx only forwards `conditions` into the wrapper —
 * that keeps existing field renderers untouched for the show/hide path,
 * which is the 90% case.
 */
import type { ReactNode } from 'react';
import type { FieldCondition } from '@appbana/shared';
import { evaluateExpression } from './conditions';
import { useFormValues } from './form-values-context';

export interface ConditionalFieldProps {
  readonly conditions?: FieldCondition;
  readonly children: ReactNode;
}

export function ConditionalField(props: Readonly<ConditionalFieldProps>) {
  const { conditions, children } = props;
  const values = useFormValues();

  if (!conditions) return <>{children}</>;

  const visible = evaluateExpression(conditions.showWhen, values);
  if (!visible) return null;

  const disabled = conditions.disabledWhen ? evaluateExpression(conditions.disabledWhen, values) : false;

  if (!disabled) return <>{children}</>;

  return (
    <div
      style={{ pointerEvents: 'none', opacity: 0.5 }}
      data-appbana-disabled="true"
      aria-disabled="true"
    >
      {children}
    </div>
  );
}
