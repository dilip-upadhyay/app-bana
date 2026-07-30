/**
 * useDebouncedValue.ts — column-filter/scale hardening pass.
 *
 * Generic debounce hook. Used so per-column filter inputs (text/number/date)
 * feel instant to type into while the actual network request — which must
 * hit the server, not filter a client-side array, since tables can hold
 * millions of rows — only fires after the caller stops changing the value
 * for `delayMs`.
 */
import { useEffect, useState } from 'react';

export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delayMs);
    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(value), delayMs]);

  return debounced;
}
