/**
 * FormField.tsx — wrapper that pairs a label, a control, and (Task 2.5)
 * inline validation error rendering.
 *
 * Reads the current error for `name` from EntityFormErrorContext. When an
 * error is present it appends `appbana-field-invalid` to the wrapper so the
 * CSS can outline the inner control in red, and renders a `role="alert"`
 * message below. The visual required-`*` is always rose-500.
 */
import type { CSSProperties, ReactNode } from 'react';
import * as React from 'react';
import { useFieldError } from './entity-form-context';
import { FieldError } from './FieldError';

export interface FormFieldProps {
  readonly name: string | undefined;
  readonly label?: string;
  readonly htmlFor?: string;
  readonly required?: boolean;
  readonly helpText?: string;
  readonly full?: boolean;
  readonly className?: string;
  readonly style?: CSSProperties;
  readonly dataAttrs?: Record<string, string>;
  readonly children: ReactNode;
}

export function FormField({
  name,
  label,
  htmlFor,
  required = false,
  helpText,
  full = false,
  className,
  style,
  dataAttrs,
  children,
}: Readonly<FormFieldProps>) {
  const error = useFieldError(name);
  const rootClass = [
    'appbana-field',
    full ? 'appbana-field-full' : '',
    error ? 'appbana-field-invalid' : '',
    className ?? '',
  ]
    .filter(Boolean)
    .join(' ');
  return (
    <div className={rootClass} style={style} {...dataAttrs}>
      {label && (
        <label htmlFor={htmlFor} className="appbana-field-label">
          {label}
          {required && (
            <span className="appbana-field-required" aria-hidden="true"> *</span>
          )}
        </label>
      )}
      {children}
      {helpText && !error && <p className="appbana-field-help">{helpText}</p>}
      <FieldError fieldName={name} />
    </div>
  );
}

/** Compute `aria-invalid` prop for the child control. */
export function useAriaInvalid(name: string | undefined): { 'aria-invalid': true } | Record<string, never> {
  const error = useFieldError(name);
  return error ? { 'aria-invalid': true } : {};
}

/**
 * Small controlled-attribute wrappers so switch-case renderers can drop in a
 * validated <input> / <select> / <textarea> without having to call the
 * `useAriaInvalid` hook inline in every branch.
 */
export function ValidatedInput(
  props: Readonly<React.InputHTMLAttributes<HTMLInputElement>>,
) {
  const aria = useAriaInvalid(props.name);
  return <input {...props} {...aria} />;
}

export function ValidatedSelect(
  props: Readonly<React.SelectHTMLAttributes<HTMLSelectElement> & { children?: ReactNode }>,
) {
  const aria = useAriaInvalid(props.name);
  return <select {...props} {...aria} />;
}

export function ValidatedTextarea(
  props: Readonly<React.TextareaHTMLAttributes<HTMLTextAreaElement>>,
) {
  const aria = useAriaInvalid(props.name);
  return <textarea {...props} {...aria} />;
}
