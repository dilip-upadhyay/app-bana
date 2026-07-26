/**
 * Toaster.tsx — Tiny toast implementation with zero external deps.
 *
 * Runtime UX Overhaul Plan §1.8 + Sprint 3 task 3.10.
 *
 * Contract (locked in Sprint 3):
 *   • Four kinds:   success | info | warning | error
 *   • Auto-dismiss: success/info = 4 s, warning = 8 s, error = never
 *   • Every toast has a visible ✕ dismiss button (never dismiss-only-on-timer)
 *   • Optional `action: { label, onClick }` renders a text button beside ✕
 *     — used by Delete + Undo (task 3.5) and future recoverable flows
 *
 * Usage:
 *   ```tsx
 *   toast.success('Customer saved');
 *   toast.error('Save failed', { description: err.message });
 *   toast.success('Deleted', {
 *     action: { label: 'Undo', onClick: () => restore(row) },
 *   });
 *   ```
 */
import { useCallback, useEffect, useRef, useState } from 'react';

type ToastKind = 'success' | 'error' | 'info' | 'warning';

export interface ToastAction {
  readonly label: string;
  readonly onClick: () => void;
}

interface ToastItem {
  readonly id: number;
  readonly kind: ToastKind;
  readonly title: string;
  readonly description?: string;
  readonly action?: ToastAction;
}

interface ToastOptions {
  readonly description?: string;
  readonly action?: ToastAction;
}

// ─── Event-based bridge — no context needed ─────────────────────────────────
const TOAST_EVENT = 'appbana:toast';
type ToastEvent = CustomEvent<Omit<ToastItem, 'id'>>;
let nextId = 1;

function emit(item: Omit<ToastItem, 'id'>) {
  if (typeof window === 'undefined') return;
  window.dispatchEvent(new CustomEvent(TOAST_EVENT, { detail: item }));
}

export const toast = {
  success: (title: string, opts?: ToastOptions) =>
    emit({ kind: 'success', title, description: opts?.description, action: opts?.action }),
  error: (title: string, opts?: ToastOptions) =>
    emit({ kind: 'error', title, description: opts?.description, action: opts?.action }),
  info: (title: string, opts?: ToastOptions) =>
    emit({ kind: 'info', title, description: opts?.description, action: opts?.action }),
  warning: (title: string, opts?: ToastOptions) =>
    emit({ kind: 'warning', title, description: opts?.description, action: opts?.action }),
};

// ─── The mounted UI component ────────────────────────────────────────────────
/** Per-kind auto-dismiss window in milliseconds; `null` == sticky. */
const DISMISS_MS: Record<ToastKind, number | null> = {
  success: 4000,
  info:    4000,
  warning: 8000,
  error:   null,
};

export function Toaster() {
  const [items, setItems] = useState<ToastItem[]>([]);
  // Track pending timers so we can cancel when the user dismisses manually.
  const timers = useRef<Map<number, number>>(new Map());

  const dismiss = useCallback((id: number) => {
    const t = timers.current.get(id);
    if (t != null) {
      window.clearTimeout(t);
      timers.current.delete(id);
    }
    setItems((prev) => prev.filter((x) => x.id !== id));
  }, []);

  useEffect(() => {
    const handler = (raw: Event) => {
      const detail = (raw as ToastEvent).detail;
      if (!detail) return;
      const id = nextId++;
      const next: ToastItem = { id, ...detail };
      setItems((prev) => [...prev, next]);
      const ms = DISMISS_MS[next.kind];
      if (ms != null) {
        const t = window.setTimeout(() => dismiss(id), ms);
        timers.current.set(id, t);
      }
    };
    window.addEventListener(TOAST_EVENT, handler);
    return () => {
      window.removeEventListener(TOAST_EVENT, handler);
      const localTimers = timers.current;
      localTimers.forEach((t) => window.clearTimeout(t));
      localTimers.clear();
    };
  }, [dismiss]);

  if (items.length === 0) return null;

  return (
    <div
      className="appbana-toaster"
      aria-live="polite"
      aria-atomic="true"
    >
      {items.map((t) => (
        <output key={t.id} className={`appbana-toast ${t.kind}`}>
          <span className="dot" aria-hidden="true" />
          <div className="flex flex-col gap-0.5 min-w-0 flex-1">
            <span className="font-medium text-slate-900">{t.title}</span>
            {t.description && (
              <span className="text-xs text-slate-500 break-words">{t.description}</span>
            )}
          </div>
          {t.action && (
            <button
              type="button"
              className="appbana-toast-action"
              onClick={() => {
                try {
                  t.action!.onClick();
                } finally {
                  dismiss(t.id);
                }
              }}
            >
              {t.action.label}
            </button>
          )}
          <button
            type="button"
            className="appbana-toast-close"
            aria-label="Dismiss"
            onClick={() => dismiss(t.id)}
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
              className="w-3.5 h-3.5"
            >
              <line x1="6" y1="6" x2="18" y2="18" />
              <line x1="18" y1="6" x2="6" y2="18" />
            </svg>
          </button>
        </output>
      ))}
    </div>
  );
}

/** Exported for unit tests. */
export const __test__ = { DISMISS_MS };
