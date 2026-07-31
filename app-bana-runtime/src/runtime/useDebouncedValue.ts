/**
 * Generic debounce hook. Lets per-column filter inputs feel instant to type
 * into while the actual request — which must hit the server, since tables can
 * hold millions of rows — only fires once the value stops changing.
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
