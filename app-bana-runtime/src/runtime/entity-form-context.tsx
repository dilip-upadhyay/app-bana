/**
 * entity-form-context.tsx — React context for surfacing per-field validation
 * errors from `useEntityFormValidation` into individual field renderers.
 *
 * Field renderers in Renderer.tsx read the error map by name and render an
 * inline `<p role="alert">` under the input plus `aria-invalid="true"`. When
 * the form is not wrapped in an <EntityFormErrorProvider> (e.g. fields
 * rendered outside a form), `useEntityFormErrors()` returns an empty map so
 * nothing changes.
 */
import { createContext, useContext, useMemo, type ReactNode } from 'react';
import type { FieldErrors } from './useEntityFormValidation';

interface EntityFormErrorValue {
  readonly errors: FieldErrors;
  readonly clearError: (name: string) => void;
}

const EMPTY: EntityFormErrorValue = { errors: {}, clearError: () => {} };

const EntityFormErrorContext = createContext<EntityFormErrorValue>(EMPTY);

export function EntityFormErrorProvider({
  errors,
  clearError,
  children,
}: Readonly<{
  errors: FieldErrors;
  clearError: (name: string) => void;
  children: ReactNode;
}>) {
  const value = useMemo(() => ({ errors, clearError }), [errors, clearError]);
  return (
    <EntityFormErrorContext.Provider value={value}>
      {children}
    </EntityFormErrorContext.Provider>
  );
}

export function useEntityFormErrors(): EntityFormErrorValue {
  return useContext(EntityFormErrorContext);
}

/** Convenience: read the error string for a single field name. */
export function useFieldError(name: string | undefined): string | undefined {
  const { errors } = useEntityFormErrors();
  if (!name) return undefined;
  return errors[name];
}
