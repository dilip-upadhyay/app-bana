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
import { RuntimeSidebar } from './RuntimeSidebar';
import { RuntimeNavigationProvider } from './runtime-navigation';
import { AppLoadingSkeleton } from './Skeleton';
import { LoginPage } from '../pages/LoginPage';
import { Toaster } from './Toaster';

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
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
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
    // React 18 StrictMode invokes effects twice with a cleanup between them,
    // which flips isMounted.current to false. Reset it on every (re)mount so
    // in-flight `loadApp` promises can still settle their state.
    isMounted.current = true;

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
            const pgs = (app.pages ?? []) as PageMeta[];
            const p = pgs.find((pg) => pg.id === msg.pageId);
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
      // Backend returns two shapes for pages:
      //   pages:     string[] of page IDs (legacy)
      //   pagesData: full PageMeta objects
      // Prefer pagesData when present so tabs/renderer receive real objects.
      const normalized: AppMeta = {
        ...appData,
        pages: (appData.pagesData ?? appData.pages ?? []) as PageMeta[],
      };
      setApp(normalized);
      const firstPage = ((normalized.pages ?? []) as PageMeta[])[0] ?? null;
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
    return <AppLoadingSkeleton />;
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
    <div className="min-h-screen bg-gray-50 flex flex-col">
      {/* Top app bar — sticky, spans full width */}
      <header className="appbana-appbar">
        <button
          type="button"
          className="appbana-appbar-menu md:hidden"
          onClick={() => setMobileNavOpen(true)}
          aria-label="Open navigation"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
               strokeLinecap="round" strokeLinejoin="round" className="w-5 h-5" aria-hidden="true">
            <line x1="4" y1="6"  x2="20" y2="6" />
            <line x1="4" y1="12" x2="20" y2="12" />
            <line x1="4" y1="18" x2="20" y2="18" />
          </svg>
        </button>
        <span className="appbana-appbar-brand">{app.name}</span>
        <div className="flex-1" />
        {/* Right slot — reserved for user menu / settings. Kept minimal for now. */}
      </header>

      {/* Body — sidebar + main */}
      <div className="flex flex-1 min-h-0">
        {/* Desktop / tablet sidebar (permanent, hidden on mobile) */}
        <aside className="hidden md:block appbana-sidebar-container">
          <RuntimeSidebar
            pages={(app.pages ?? []) as PageMeta[]}
            currentPageId={currentPage?.id ?? null}
            onSelect={setCurrentPage}
          />
        </aside>

        {/* Mobile drawer — rendered outside <aside> to overlay content */}
        {mobileNavOpen && (
          <button
            type="button"
            className="appbana-drawer-backdrop md:hidden"
            aria-label="Close navigation"
            onClick={() => setMobileNavOpen(false)}
          />
        )}
        <aside
          className={`appbana-drawer md:hidden ${mobileNavOpen ? 'appbana-drawer-open' : ''}`}
          aria-hidden={!mobileNavOpen}
        >
          <RuntimeSidebar
            pages={(app.pages ?? []) as PageMeta[]}
            currentPageId={currentPage?.id ?? null}
            onSelect={setCurrentPage}
            onClose={() => setMobileNavOpen(false)}
          />
        </aside>

        {/* Main content — scrollable */}
        <main className="flex-1 overflow-y-auto">
          <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 sm:py-8">
            {currentPage ? (
              <RuntimeNavigationProvider
                pages={(app.pages ?? []) as PageMeta[]}
                navigateToPage={setCurrentPage}
              >
                {renderPage(currentPage)}
              </RuntimeNavigationProvider>
            ) : (
              <p className="text-slate-400 text-sm p-8 text-center">No pages in this app.</p>
            )}
          </div>
        </main>
      </div>
      <Toaster />
    </div>
  );
}
