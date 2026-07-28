/**
 * SavedViewsBar.tsx — Phase B5 saved-view chips for list pages.
 *
 * Fetches saved views for the current tenant/app/entity and renders
 * a horizontal pill row. Clicking a pill invokes `onSelect` with the
 * view payload. The optional "Save current" affordance POSTs whatever
 * the parent hands over.
 */
import { useCallback, useEffect, useState } from 'react';
import {
  listSavedViews,
  saveView,
  deleteSavedView,
  type SavedViewRecord,
} from '@appbana/shared';
import { getRuntimeToken } from './qualifyEntityKey';

interface SavedViewsBarProps {
  readonly tenantId: string;
  readonly appId: string;
  readonly entityKey: string;
  readonly currentView?: SavedViewRecord['view'];
  readonly onSelect: (view: SavedViewRecord) => void;
  /**
   * Task C3.6 — synthesised views rendered ahead of the user's own. They carry
   * no delete affordance: they are not the user's to remove, and a "delete"
   * that silently reappeared on reload would be worse than none.
   */
  readonly systemViews?: readonly SavedViewRecord[];
  /** viewId of the currently applied view, for an active state. */
  readonly activeViewId?: string | null;
}

export function SavedViewsBar({
  tenantId,
  appId,
  entityKey,
  currentView,
  onSelect,
  systemViews = [],
  activeViewId = null,
}: Readonly<SavedViewsBarProps>) {
  const [views, setViews] = useState<SavedViewRecord[]>([]);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => {
    const token = getRuntimeToken();
    if (!token) return;
    try {
      const next = await listSavedViews(tenantId, appId, entityKey, token);
      setViews(next);
    } catch {
      /* silent — bar is non-critical */
    }
  }, [tenantId, appId, entityKey]);

  useEffect(() => { refresh().catch(() => {}); }, [refresh]);

  async function handleSave() {
    const token = getRuntimeToken();
    if (!token || !currentView) return;
    const name = window.prompt('Name this view');
    if (!name) return;
    setBusy(true);
    try {
      await saveView({ tenantId, appId, entityKey, name, view: currentView }, token);
      await refresh();
    } finally {
      setBusy(false);
    }
  }

  async function handleDelete(viewId: string) {
    const token = getRuntimeToken();
    if (!token) return;
    setBusy(true);
    try {
      await deleteSavedView(viewId, token);
      await refresh();
    } finally {
      setBusy(false);
    }
  }

  return (
    <div
      className="flex flex-wrap items-center gap-2 px-5 py-2 border-b border-slate-200 bg-white"
      data-appbana-saved-views
    >
      {systemViews.map((v) => {
        const active = activeViewId === v.viewId;
        return (
          <button
            key={v.viewId}
            type="button"
            className={`px-3 py-1 rounded-full text-xs ${
              active
                ? 'bg-indigo-600 text-white'
                : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
            }`}
            aria-pressed={active}
            data-system-view={v.viewId}
            onClick={() => onSelect(v)}
          >
            {v.name}
          </button>
        );
      })}
      {views.map((v) => (
        <span key={v.viewId} className="inline-flex items-center gap-1 rounded-full bg-slate-100 hover:bg-slate-200 text-xs">
          <button
            type="button"
            className="pl-3 py-1 text-slate-700"
            onClick={() => onSelect(v)}
          >
            {v.name}
          </button>
          <button
            type="button"
            className="pr-2 pl-1 py-1 text-slate-400 hover:text-rose-600"
            aria-label={`Delete view ${v.name}`}
            onClick={() => { handleDelete(v.viewId).catch(() => {}); }}
          >
            ×
          </button>
        </span>
      ))}
      {currentView && (
        <button
          type="button"
          disabled={busy}
          className="text-xs text-indigo-600 hover:text-indigo-800 disabled:opacity-50 underline"
          onClick={() => { handleSave().catch(() => {}); }}
        >
          + Save current
        </button>
      )}
    </div>
  );
}
