import { useEffect, useRef, useState } from 'react';
import type { AppBanaPostMessage } from '@appbana/shared';
import { useSessionStore } from '../../stores/session';
import { useWorkspaceStore } from '../../stores/workspace';

const RUNTIME_ORIGIN = 'http://localhost:5175';

const RUNTIME_BASE = RUNTIME_ORIGIN; // new React runtime on port 5175 (Stage 2)

export function PreviewPane() {
  const { token, tenantId, userId, email, name } = useSessionStore();
  const { currentApp, previewRefreshToken } = useWorkspaceStore();
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const [activePage, setActivePage] = useState<string | null>(null);
  const [deviceWidth, setDeviceWidth] = useState<'full' | 'tablet' | 'mobile'>('full');

  const pages = currentApp?.pages ?? [];

  // Build the iframe URL
  const runtimeUrl = currentApp
    ? `${RUNTIME_BASE}/run/${tenantId ?? 'default'}/${currentApp.id}`
    : null;

  // Reload iframe when previewRefreshToken changes (after tool completes)
  useEffect(() => {
    if (iframeRef.current && runtimeUrl) {
      iframeRef.current.src = runtimeUrl + `?t=${previewRefreshToken}`;
    }
  }, [previewRefreshToken, runtimeUrl]);

  // postMessage bridge: token handshake
  useEffect(() => {
    const handler = (ev: MessageEvent) => {
      if (ev.origin !== RUNTIME_ORIGIN) return;
      const msg = ev.data as AppBanaPostMessage;
      if (msg.type === 'ready' && token) {
        iframeRef.current?.contentWindow?.postMessage(
          { type: 'token', jwt: token, userId: userId ?? undefined, email: email ?? undefined, name: name ?? undefined, tenantId: tenantId ?? 'default' } satisfies AppBanaPostMessage,
          RUNTIME_ORIGIN
        );
        iframeRef.current?.contentWindow?.postMessage(
          { type: 'setMode', mode: 'browse' } satisfies AppBanaPostMessage,
          RUNTIME_ORIGIN
        );
      }
    };
    window.addEventListener('message', handler);
    return () => window.removeEventListener('message', handler);
  }, [token, userId, email, name, tenantId]);

  const widthClass = deviceWidth === 'mobile'
    ? 'w-[375px]' : deviceWidth === 'tablet' ? 'w-[768px]' : 'w-full';

  return (
    <div className="flex flex-col h-full bg-gray-900">
      {/* Preview toolbar */}
      <div className="h-10 flex items-center gap-2 px-3 border-b border-gray-800 shrink-0">
        {/* Page tabs */}
        <div className="flex items-center gap-1 overflow-x-auto flex-1 min-w-0">
          {pages.map((p) => (
            <button
              key={p.id}
              onClick={() => {
                setActivePage(p.id);
                iframeRef.current?.contentWindow?.postMessage(
                  { type: 'setPage', pageId: p.id } as AppBanaPostMessage,
                  RUNTIME_ORIGIN
                );
              }}
              className={`text-xs px-3 py-1 rounded-md whitespace-nowrap transition-colors
                ${activePage === p.id
                  ? 'bg-indigo-600 text-white' : 'text-gray-400 hover:text-white hover:bg-gray-800'}`}
            >
              {p.name}
            </button>
          ))}
        </div>

        {/* Device toggle */}
        <div className="flex gap-1">
          {(['full', 'tablet', 'mobile'] as const).map((d) => (
            <button
              key={d}
              onClick={() => setDeviceWidth(d)}
              title={d}
              className={`text-xs px-2 py-1 rounded transition-colors
                ${deviceWidth === d ? 'text-indigo-400 bg-gray-800' : 'text-gray-500 hover:text-gray-300'}`}
            >
              {d === 'full' ? '🖥' : d === 'tablet' ? '📱' : '📲'}
            </button>
          ))}
        </div>

        {/* Open in new tab */}
        {runtimeUrl && (
          <a
            href={runtimeUrl}
            target="_blank"
            rel="noreferrer"
            className="text-xs text-gray-500 hover:text-gray-300 transition-colors ml-1"
            title="Open in new tab"
          >
            ↗
          </a>
        )}
      </div>

      {/* Preview area */}
      <div className="flex-1 overflow-hidden flex justify-center bg-gray-950">
        {currentApp && runtimeUrl ? (
          <div className={`h-full transition-all duration-300 ${widthClass}`}>
            <iframe
              ref={iframeRef}
              src={runtimeUrl}
              title="App preview"
              className="w-full h-full border-0"
              allow="same-origin"
            />
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center gap-4 text-center p-8">
            <span className="text-4xl opacity-40">📱</span>
            <p className="text-gray-500 text-sm max-w-xs">
              Describe your app in the chat and it'll appear here live as the AI builds it.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
