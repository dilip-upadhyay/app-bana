/**
 * form-values-context.tsx — Phase B2 support. Publishes the current form
 * values as a React context so field renderers can re-evaluate declarative
 * `showWhen` / `requiredWhen` / `disabledWhen` expressions on every input
 * change.
 *
 * Design:
 *   - <FormValuesProvider formRef={...}> subscribes to the underlying
 *     `<form>` element's `input` and `change` events, then broadcasts a
 *     fresh values snapshot via context on each mutation.
 *   - Field renderers call `useFormValues()` to read the latest snapshot
 *     and evaluate their conditions.
 *   - Uncontrolled inputs (defaultValue-based) keep their state in the DOM;
 *     the context is purely a mirror for condition evaluation. Save-time
 *     value collection still goes through FormData.
 */
import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
  type RefObject,
} from 'react';
import { readFormValues, type FormValues } from './conditions';

interface FormValuesContextValue {
  readonly values: FormValues;
}

const EMPTY_CTX: FormValuesContextValue = { values: {} };
const FormValuesContext = createContext<FormValuesContextValue>(EMPTY_CTX);

export interface FormValuesProviderProps {
  readonly formRef: RefObject<HTMLFormElement | null>;
  readonly children: ReactNode;
  /** Fallback initial values (e.g. edit mode) so first-render conditions
   *  see something reasonable before the DOM subscription primes. */
  readonly initialValues?: FormValues;
}

export function FormValuesProvider(props: Readonly<FormValuesProviderProps>) {
  const { formRef, children, initialValues } = props;
  const [values, setValues] = useState<FormValues>(initialValues ?? {});

  useEffect(() => {
    const form = formRef.current;
    if (!form) return;
    // Prime with whatever is currently in the DOM (defaultValues).
    setValues(readFormValues(form));

    const update = () => setValues(readFormValues(form));
    form.addEventListener('input', update);
    form.addEventListener('change', update);
    return () => {
      form.removeEventListener('input', update);
      form.removeEventListener('change', update);
    };
  }, [formRef]);

  const value = useMemo(() => ({ values }), [values]);
  return <FormValuesContext.Provider value={value}>{children}</FormValuesContext.Provider>;
}

/** Get the latest form values snapshot. Returns `{}` outside a provider. */
export function useFormValues(): FormValues {
  return useContext(FormValuesContext).values;
}
