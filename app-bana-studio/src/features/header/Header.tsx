import { useEffect, useRef, useState } from 'react';
import { listApps, createApp, fetchBranding } from '@appbana/shared';
import { useSessionStore } from '../../stores/session';
import { useWorkspaceStore } from '../../stores/workspace';

export function Header() {
  const { token, tenantId, name, clearSession } = useSessionStore();
  const { apps, currentApp, branding, setApps, setCurrentApp, setBranding } = useWorkspaceStore();
  const [menuOpen, setMenuOpen] = useState(false);
  const [appsOpen, setAppsOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!token || !tenantId) return;
    fetchBranding(tenantId).then(setBranding).catch(() => {});
    listApps(tenantId, token).then(setApps).catch(() => {});
  }, [token, tenantId]);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
        setAppsOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  async function handleNewApp() {
    if (!token || !tenantId) return;
    const appName = prompt('App name:');
    if (!appName?.trim()) return;
    try {
      const app = await createApp(tenantId, appName.trim(), token);
      setApps([...apps, app]);
      setCurrentApp(app);
    } catch {
      alert('Failed to create app');
    }
    setAppsOpen(false);
  }

  const brandColor = branding?.primaryColor ?? '#6366f1';

  return (
    <header
      className="h-12 flex items-center gap-3 px-4 border-b border-gray-800 bg-gray-900 shrink-0"
      style={{ '--color-brand': brandColor } as React.CSSProperties}
    >
      {/* Logo */}
      <span className="text-xl select-none">🍌</span>
      <span className="font-bold text-white text-sm hidden sm:block">AppBana</span>

      <div className="w-px h-5 bg-gray-700 mx-1" />

      {/* App switcher */}
      <div className="relative" ref={menuRef}>
        <button
          onClick={() => { setAppsOpen((v) => !v); setMenuOpen(false); }}
          className="flex items-center gap-1.5 text-sm text-gray-300 hover:text-white px-2 py-1 rounded-md
                     hover:bg-gray-800 transition-colors max-w-[180px]"
        >
          <span className="truncate">{currentApp?.name ?? 'Select app'}</span>
          <span className="text-gray-500">▾</span>
        </button>

        {appsOpen && (
          <div className="absolute top-full mt-1 left-0 w-56 bg-gray-800 border border-gray-700 rounded-lg
                          shadow-xl z-50 py-1 text-sm">
            {apps.map((app) => (
              <button
                key={app.id}
                onClick={() => { setCurrentApp(app); setAppsOpen(false); }}
                className={`w-full text-left px-3 py-2 hover:bg-gray-700 transition-colors truncate
                  ${currentApp?.id === app.id ? 'text-indigo-400' : 'text-gray-200'}`}
              >
                {app.name}
              </button>
            ))}
            <div className="border-t border-gray-700 my-1" />
            <button
              onClick={handleNewApp}
              className="w-full text-left px-3 py-2 text-indigo-400 hover:bg-gray-700 transition-colors"
            >
              + New app…
            </button>
          </div>
        )}
      </div>

      {/* Spacer */}
      <div className="flex-1" />

      {/* Deploy */}
      {currentApp && (
        <button className="text-xs bg-indigo-600 hover:bg-indigo-500 text-white px-3 py-1.5 rounded-lg
                           transition-colors font-medium">
          Deploy
        </button>
      )}

      {/* User menu */}
      <div className="relative">
        <button
          onClick={() => { setMenuOpen((v) => !v); setAppsOpen(false); }}
          className="w-7 h-7 rounded-full bg-indigo-700 flex items-center justify-center text-xs font-bold
                     text-white hover:bg-indigo-600 transition-colors"
        >
          {name?.[0]?.toUpperCase() ?? '?'}
        </button>

        {menuOpen && (
          <div className="absolute top-full mt-1 right-0 w-44 bg-gray-800 border border-gray-700 rounded-lg
                          shadow-xl z-50 py-1 text-sm">
            <div className="px-3 py-2 text-gray-400 truncate">{name}</div>
            <div className="border-t border-gray-700 my-1" />
            <button
              onClick={() => { clearSession(); setMenuOpen(false); }}
              className="w-full text-left px-3 py-2 text-red-400 hover:bg-gray-700 transition-colors"
            >
              Sign out
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
