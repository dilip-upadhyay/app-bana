/**
 * FieldError.tsx — Inline validation-error message rendered under a form field.
 * Reads the current error for `fieldName` from `EntityFormErrorContext` and
 * renders nothing when there is no error. Uses `role="alert"` so assistive
 * tech announces new errors when they appear.
 */
import type { ReactElement } from 'react';
import { useFieldError } from './entity-form-context';

export function FieldError({
  fieldName,
}: Readonly<{ fieldName: string | undefined }>): ReactElement | null {
  const error = useFieldError(fieldName);
  if (!error) return null;
  return (
    <p className="appbana-field-error" role="alert">
      {error}
    </p>
  );
}
