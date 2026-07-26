/**
 * useWizardDraft.ts — Phase B1.3. Persists in-flight wizard values to
 * localStorage per (userId, appId, entityName, mode) so a refresh (or
 * accidental tab close) never loses a partially-filled multi-step form.
 *
 * Contract:
 *   - `load()` returns a snapshot map { fieldName -> string value } or
 *     `null` if no draft exists.
 *   - `save(values)` writes the snapshot. Debounced by the caller.
 *   - `clear()` removes the draft — call on successful submit or when the
 *     user explicitly cancels the wizard.
 *
 * The values map is intentionally string-keyed and string-valued — matches
 * what the DOM's uncontrolled inputs read/write via `defaultValue` +
 * FormData.
 */
import { useCallback, useMemo } from 'react';

export type WizardDraftValues = Record<string, string>;

export interface WizardDraftHandle {
  readonly load: () => WizardDraftValues | null;
  readonly save: (values: WizardDraftValues) => void;
  readonly clear: () => void;
}

export interface UseWizardDraftOptions {
  /** Stable identifier — see hook docs. */
  readonly key: string;
  /**
   * Optional TTL in ms. Older drafts are ignored on `load()`. Defaults to
   * 7 days — long enough to survive a weekend, short enough that a stale
   * spec change doesn't resurrect obviously-wrong data.
   */
  readonly maxAgeMs?: number;
}

const DEFAULT_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000;
const STORAGE_PREFIX = 'appbana:wizard-draft:';

interface Envelope {
  savedAt: number;
  values: WizardDraftValues;
}

function safeStorage(): Storage | null {
  try {
    if (typeof window === 'undefined') return null;
    return window.localStorage;
  } catch {
    return null;
  }
}

export function useWizardDraft(options: UseWizardDraftOptions): WizardDraftHandle {
  const { key, maxAgeMs = DEFAULT_MAX_AGE_MS } = options;
  const storageKey = STORAGE_PREFIX + key;

  const load = useCallback((): WizardDraftValues | null => {
    const s = safeStorage();
    if (!s) return null;
    try {
      const raw = s.getItem(storageKey);
      if (!raw) return null;
      const env = JSON.parse(raw) as Envelope;
      if (!env || typeof env !== 'object') return null;
      if (typeof env.savedAt !== 'number' || Date.now() - env.savedAt > maxAgeMs) {
        s.removeItem(storageKey);
        return null;
      }
      if (!env.values || typeof env.values !== 'object') return null;
      return env.values;
    } catch {
      return null;
    }
  }, [storageKey, maxAgeMs]);

  const save = useCallback((values: WizardDraftValues) => {
    const s = safeStorage();
    if (!s) return;
    try {
      const env: Envelope = { savedAt: Date.now(), values };
      s.setItem(storageKey, JSON.stringify(env));
    } catch {
      // Quota errors / private mode — ignore silently. The wizard still
      // works in-memory; the user just loses persistence.
    }
  }, [storageKey]);

  const clear = useCallback(() => {
    const s = safeStorage();
    if (!s) return;
    try {
      s.removeItem(storageKey);
    } catch {
      // no-op
    }
  }, [storageKey]);

  return useMemo(() => ({ load, save, clear }), [load, save, clear]);
}
