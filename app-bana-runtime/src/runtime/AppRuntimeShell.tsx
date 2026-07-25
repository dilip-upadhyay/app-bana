/**
 * AppRuntimeShell.tsx — React port of app-bana-ui's LitElement runtime shell.
 *
 * Stage 2 responsibilities:
 * - Resolve tenantId + appId from path via resolveAppContext()
 * - Optionally accept JWT via postMessage (skip login when embedded in studio)
 * - Fetch app metadata from backend
 * - Render pages via Renderer.tsx
 * - Emit ready/selection/error to parent via postMessage
 */
import { useEffect, useRef, useState, useCallback } from 'react';
import type { AppMeta, PageMeta, AppBanaPostMessage, RuntimeMode } from '@appbana/shared';
import { resolveAppContext, getApp, login as apiLogin } from '@appbana/shared';
import { renderPage } from './Renderer';
import { LoginPage } from '../pages/LoginPage';

const TOKEN_KEY   = 'appbana_token';
const STUDIO_ORIGIN = 'http://localhost:5174';

/** True when this runtime is embedded in the studio iframe (has a real cross-frame parent). */
function isEmbedded(): boolean {
  return typeof window !== 'undefined' && window.parent && window.parent !== window;
}

/** Post a message to the studio parent iff we are actually embedded. */
function postToStudio(msg: AppBanaPostMessage) {
  if (!isEmbedded()) return;
  try {
    window.parent.postMessage(msg, STUDIO_ORIGIN);
  } catch {
    // Cross-origin errors are non-fatal — runtime works standalone too.
  }
}

function storedToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function AppRuntimeShell() {
  const [token, setTokenState] = useState<string | null>(storedToken);
  const [app, setApp] = useState<AppMeta | null>(null);
  const [currentPage, setCurrentPage] = useState<PageMeta | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  // Stage 2 contract fields — full Stage 6 wiring lands with select-and-instruct.
  // Stored in refs (not state) because the current render doesn't consume them yet;
  // Stage 6 will migrate these to state and drive the inspection overlay.
  const modeRef = useRef<RuntimeMode>('browse');
  const highlightedNodeRef = useRef<string | null>(null);
  const isMounted = useRef(true);

  // --- Resolve context from URL path ---
  const ctx = resolveAppContext(window.location);

  // --- postMessage bridge ---
  useEffect(() => {
    // Signal to studio parent that we are ready.
    postToStudio({ type: 'ready' });

    const handler = (ev: MessageEvent) => {
      if (ev.origin !== STUDIO_ORIGIN) return;
      const msg = ev.data as AppBanaPostMessage;
      switch (msg.type) {
        case 'token':
          localStorage.setItem(TOKEN_KEY, msg.jwt);
          if (msg.userId !== undefined || msg.email !== undefined) {
            localStorage.setItem('appbana_user', JSON.stringify({
              id: msg.userId ?? '',
              email: msg.email ?? '',
              name: msg.name ?? '',
              tenantId: msg.tenantId ?? 'default',
            }));
          }
          setTokenState(msg.jwt);
          break;
        case 'setMode':
          // Stage 2 contract: acknowledge mode changes (browse vs inspect).
          // Stage 6 will use `inspect` to emit selection events on click.
          modeRef.current = msg.mode;
          break;
        case 'setPage':
          if (app) {
            const p = app.pages?.find((pg) => pg.id === msg.pageId);
            if (p) setCurrentPage(p);
          }
          break;
        case 'highlight':
          // Stage 2 contract: studio asked us to highlight a node.
          // Stage 6 will render an outline overlay for the target element.
          highlightedNodeRef.current = msg.nodeId;
          break;
        case 'reload':
          window.location.reload();
          break;
      }
    };

    window.addEventListener('message', handler);
    return () => {
      isMounted.current = false;
      window.removeEventListener('message', handler);
    };
  }, [app]);

  // --- Fetch app metadata once token is available ---
  const loadApp = useCallback(async (tk: string) => {
    if (!ctx) {
      setError('Cannot resolve app context from URL path.');
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const appData = await getApp(ctx.tenantId, ctx.appId, tk);
      if (!isMounted.current) return;
      setApp(appData);
      const firstPage = appData.pages?.[0] ?? null;
      setCurrentPage(firstPage);
    } catch (e) {
      if (!isMounted.current) return;
      setError(e instanceof Error ? e.message : 'Failed to load app');
      postToStudio({ type: 'error', message: String(e) });
    } finally {
      if (isMounted.current) setLoading(false);
    }
  }, [ctx?.tenantId, ctx?.appId]);

  useEffect(() => {
    if (token) loadApp(token);
    else setLoading(false);
  }, [token, loadApp]);

  // --- Login handler (when not embedded in studio) ---
  async function handleLogin(email: string, password: string): Promise<string> {
    const result = await apiLogin(email, password);
    localStorage.setItem(TOKEN_KEY, result.token);
    setTokenState(result.token);
    return result.token;
  }

  // --- Show login if no token ---
  if (!token) {
    return (
      <LoginPage
        tenantId={ctx?.tenantId ?? 'default'}
        onLogin={handleLogin}
      />
    );
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gray-50 text-gray-400 text-sm">
        Loading app…
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-gray-50 gap-4 p-8">
        <p className="text-red-600 font-semibold">Error loading app</p>
        <p className="text-gray-500 text-sm">{error}</p>
      </div>
    );
  }

  if (!app) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gray-50 text-gray-400 text-sm">
        App not found.
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-white">
      {/* App header */}
      <header className="h-12 flex items-center gap-3 px-4 border-b border-gray-200 bg-white shadow-sm">
        <span className="font-bold text-gray-800">{app.name}</span>
        {/* Page tabs */}
        <div className="flex gap-1 ml-4">
          {(app.pages ?? []).map((p) => (
            <button
              key={p.id}
              onClick={() => setCurrentPage(p)}
              className={`text-xs px-3 py-1 rounded transition-colors
                ${currentPage?.id === p.id
                  ? 'bg-indigo-600 text-white'
                  : 'text-gray-500 hover:text-gray-800 hover:bg-gray-100'}`}
            >
              {p.name}
            </button>
          ))}
        </div>
      </header>

      {/* Page content */}
      <main className="p-4">
        {currentPage
          ? renderPage(currentPage)
          : <p className="text-gray-400 text-sm p-8 text-center">No pages in this app.</p>
        }
      </main>
    </div>
  );
}
