/**
 * Toaster.tsx — Tiny toast implementation with zero external deps.
 *
 * Runtime UX Overhaul Plan §1.8. We opted out of `sonner` to keep the runtime
 * bundle lean and to avoid a Radix pull-in for one feature. The public API is
 * intentionally the same shape (`toast.success` / `toast.error`), so we can
 * swap in `sonner` later with a one-file change if we need richer features.
 *
 * Usage:
 *   ```tsx
 *   // mount once, near the app root
 *   <Toaster />
 *
 *   // fire from anywhere — no React hooks required
 *   toast.success('Customer saved');
 *   toast.error('Save failed', { description: err.message });
 *   ```
 */
import { useEffect, useState } from 'react';

type ToastKind = 'success' | 'error' | 'info';

interface ToastItem {
  readonly id: number;
  readonly kind: ToastKind;
  readonly title: string;
  readonly description?: string;
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
  success: (title: string, opts?: { description?: string }) =>
    emit({ kind: 'success', title, description: opts?.description }),
  error: (title: string, opts?: { description?: string }) =>
    emit({ kind: 'error', title, description: opts?.description }),
  info: (title: string, opts?: { description?: string }) =>
    emit({ kind: 'info', title, description: opts?.description }),
};

// ─── The mounted UI component ────────────────────────────────────────────────
const AUTO_DISMISS_MS = 4000;

export function Toaster() {
  const [items, setItems] = useState<ToastItem[]>([]);

  useEffect(() => {
    const handler = (raw: Event) => {
      const detail = (raw as ToastEvent).detail;
      if (!detail) return;
      const id = nextId++;
      const next: ToastItem = { id, ...detail };
      setItems((prev) => [...prev, next]);
      window.setTimeout(() => {
        setItems((prev) => prev.filter((t) => t.id !== id));
      }, AUTO_DISMISS_MS);
    };
    window.addEventListener(TOAST_EVENT, handler);
    return () => window.removeEventListener(TOAST_EVENT, handler);
  }, []);

  if (items.length === 0) return null;

  return (
    <div className="appbana-toaster" aria-live="polite" aria-atomic="true">
      {items.map((t) => (
        <output key={t.id} className={`appbana-toast ${t.kind}`}>
          <span className="dot" aria-hidden="true" />
          <div className="flex flex-col gap-0.5 min-w-0">
            <span className="font-medium text-slate-900">{t.title}</span>
            {t.description && (
              <span className="text-xs text-slate-500 break-words">{t.description}</span>
            )}
          </div>
        </output>
      ))}
    </div>
  );
}
