/**
 * ConfirmDialog.tsx — Sprint 3 task 3.5.
 *
 * Zero-dep confirmation modal backed by the native <dialog> element. Chosen
 * over a custom overlay because <dialog> gives us focus-trap, backdrop
 * click-to-close, Esc-to-close, and correct z-stack behaviour for free —
 * every browser we support has shipped it since 2022.
 *
 * Consumers open the dialog imperatively via {@link useConfirm}, which
 * returns an `async confirm(opts) => boolean`. This keeps call sites clean:
 *
 *   const confirm = useConfirm();
 *   const ok = await confirm({
 *     title: 'Delete Customer?',
 *     message: 'This can be undone within 5 seconds.',
 *     danger: true,
 *   });
 *   if (ok) await deleteRow(...);
 *
 * The `ConfirmDialogHost` must be mounted once near the app root. Only one
 * dialog is visible at a time; simultaneous requests queue via the internal
 * event bus.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { Button } from './Button';

export interface ConfirmOptions {
  readonly title: string;
  readonly message?: string;
  readonly confirmLabel?: string;
  readonly cancelLabel?: string;
  /** Renders the confirm button in the danger variant. */
  readonly danger?: boolean;
}

interface ConfirmRequest extends ConfirmOptions {
  readonly resolve: (accepted: boolean) => void;
}

// ─── Event bridge — same pattern as the toast bus, no context needed ───────
const CONFIRM_EVENT = 'appbana:confirm';
type ConfirmEvent = CustomEvent<ConfirmRequest>;

/**
 * Hook that returns an `async confirm(opts)` function. Call it anywhere to
 * pop the modal and await the user's answer. Falls back to `window.confirm`
 * when the host isn't mounted (defence in depth, e.g. unit tests).
 */
export function useConfirm() {
  return useCallback(async (opts: ConfirmOptions): Promise<boolean> => {
    if (typeof window === 'undefined') return false;
    return new Promise<boolean>((resolve) => {
      const detail: ConfirmRequest = { ...opts, resolve };
      window.dispatchEvent(new CustomEvent(CONFIRM_EVENT, { detail }));
    });
  }, []);
}

export function ConfirmDialogHost() {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [current, setCurrent] = useState<ConfirmRequest | null>(null);
  const queueRef = useRef<ConfirmRequest[]>([]);

  const takeNext = useCallback(() => {
    const next = queueRef.current.shift() ?? null;
    setCurrent(next);
  }, []);

  useEffect(() => {
    const handler = (raw: Event) => {
      const detail = (raw as ConfirmEvent).detail;
      if (!detail) return;
      if (current) {
        queueRef.current.push(detail);
      } else {
        setCurrent(detail);
      }
    };
    window.addEventListener(CONFIRM_EVENT, handler);
    return () => window.removeEventListener(CONFIRM_EVENT, handler);
  }, [current]);

  // Open the <dialog> whenever we get a new request.
  useEffect(() => {
    const dlg = dialogRef.current;
    if (!dlg || !current) return;
    if (!dlg.open) dlg.showModal();
  }, [current]);

  const close = useCallback((accepted: boolean) => {
    current?.resolve(accepted);
    const dlg = dialogRef.current;
    if (dlg?.open) dlg.close();
    takeNext();
  }, [current, takeNext]);

  // Native `cancel` event (Esc + backdrop click) counts as decline.
  useEffect(() => {
    const dlg = dialogRef.current;
    if (!dlg) return;
    const onCancel = (e: Event) => {
      e.preventDefault(); // stop the default close so our close() runs consistently
      close(false);
    };
    dlg.addEventListener('cancel', onCancel);
    return () => dlg.removeEventListener('cancel', onCancel);
  }, [close]);

  if (!current) {
    // Still render the <dialog> so ref survives across renders; hidden by
    // absence of the `open` attribute.
    return <dialog ref={dialogRef} className="appbana-confirm" aria-hidden="true" />;
  }

  return (
    <dialog
      ref={dialogRef}
      className="appbana-confirm"
      aria-labelledby="appbana-confirm-title"
    >
      <div className="appbana-confirm-body">
        <h2 id="appbana-confirm-title" className="appbana-confirm-title">
          {current.title}
        </h2>
        {current.message && (
          <p className="appbana-confirm-message">{current.message}</p>
        )}
        <div className="appbana-confirm-actions">
          <Button variant="tertiary" onClick={() => close(false)}>
            {current.cancelLabel ?? 'Cancel'}
          </Button>
          <Button
            variant={current.danger ? 'danger' : 'primary'}
            onClick={() => close(true)}
          >
            {current.confirmLabel ?? (current.danger ? 'Delete' : 'Confirm')}
          </Button>
        </div>
      </div>
    </dialog>
  );
}
