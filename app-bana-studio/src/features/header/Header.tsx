import { useEffect, useRef, useState } from 'react';
import { listApps, createApp, deployApp, fetchBranding, type DeployResult } from '@appbana/shared';
import { useSessionStore } from '../../stores/session';
import { useWorkspaceStore } from '../../stores/workspace';
import { useDrawerStore } from '../../stores/drawer';
import { SessionPicker } from '../sessions/SessionPicker';
import { DeployResultModal } from './DeployResultModal';

type Env = 'DEV' | 'SIT' | 'PROD';
const ENVIRONMENTS: Env[] = ['DEV', 'SIT', 'PROD'];
const ENV_BADGE: Record<Env, { text: string; className: string }> = {
  DEV:  { text: 'test', className: 'bg-emerald-900/60 text-emerald-300' },
  SIT:  { text: 'test', className: 'bg-amber-900/60 text-amber-300'   },
  PROD: { text: 'live', className: 'bg-rose-900/60 text-rose-300'     },
};

export function Header() {
  const { token, tenantId, name, clearSession } = useSessionStore();
  const { apps, currentApp, branding, setApps, setCurrentApp, setBranding } = useWorkspaceStore();
  const { toggleData, toggleSessions, sessionsOpen, closeAll } = useDrawerStore();
  const [menuOpen, setMenuOpen] = useState(false);
  const [appsOpen, setAppsOpen] = useState(false);
  const [deploying, setDeploying] = useState<Env | null>(null);
  const [deployOpen, setDeployOpen] = useState(false);
  const [deployResult, setDeployResult] = useState<{ result: DeployResult; url: string } | null>(null);
  const [deployError, setDeployError] = useState<string | null>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const sessionsBtnRef = useRef<HTMLDivElement>(null);
  const deployRef = useRef<HTMLDivElement>(null);

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
      if (deployRef.current && !deployRef.current.contains(e.target as Node)) {
        setDeployOpen(false);
      }
      if (sessionsBtnRef.current && !sessionsBtnRef.current.contains(e.target as Node) && sessionsOpen) {
        closeAll();
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [sessionsOpen, closeAll]);

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

  // Runtime origin — Stage 5 will replace with tenant subdomains.
  const RUNTIME_ORIGIN = 'http://localhost:5175';

  async function handleDeploy(env: Env) {
    if (!token || !tenantId || !currentApp || deploying) return;
    setDeployOpen(false);
    setDeployError(null);
    setDeploying(env);
    try {
      const result = await deployApp(tenantId, currentApp.id, token, env);
      // Deployed app URL for this env. Same path today; subdomains land in Stage 5.
      const url = `${RUNTIME_ORIGIN}/run/${encodeURIComponent(tenantId)}/${encodeURIComponent(currentApp.id)}?env=${env}`;
      setDeployResult({ result, url });
    } catch (err) {
      setDeployError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setDeploying(null);
    }
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

      {/* Data drawer toggle */}
      {currentApp && (
        <button
          onClick={toggleData}
          className="text-xs text-gray-300 hover:text-white px-2 py-1 rounded-md hover:bg-gray-800
                     transition-colors flex items-center gap-1"
          title="View data"
        >
          <span aria-hidden="true">▦</span>
          <span className="hidden md:inline">Data</span>
        </button>
      )}

      {/* Session picker */}
      <div className="relative" ref={sessionsBtnRef}>
        <button
          onClick={() => { toggleSessions(); setMenuOpen(false); setAppsOpen(false); }}
          className={`text-xs px-2 py-1 rounded-md transition-colors flex items-center gap-1
            ${sessionsOpen ? 'bg-gray-800 text-white' : 'text-gray-300 hover:text-white hover:bg-gray-800'}`}
          title="Chat sessions"
        >
          <span aria-hidden="true">🕒</span>
          <span className="hidden md:inline">Sessions</span>
        </button>
        <SessionPicker />
      </div>

      {/* Deploy — env picker dropdown */}
      {currentApp && (
        <div className="relative" ref={deployRef}>
          <button
            onClick={() => { setDeployOpen((v) => !v); setMenuOpen(false); setAppsOpen(false); }}
            disabled={deploying !== null}
            className="text-xs bg-indigo-600 hover:bg-indigo-500 disabled:opacity-60 disabled:cursor-not-allowed
                       text-white pl-3 pr-2 py-1.5 rounded-lg transition-colors font-medium flex items-center gap-1"
            title="Deploy to an environment"
          >
            {deploying ? `Deploying ${deploying}…` : 'Deploy'}
            <span className="text-indigo-200">▾</span>
          </button>
          {deployOpen && !deploying && (
            <div className="absolute top-full right-0 mt-1 w-40 bg-gray-800 border border-gray-700 rounded-lg
                            shadow-xl z-50 py-1 text-sm">
              <div className="px-3 py-1 text-[10px] uppercase tracking-wider text-gray-500">Environment</div>
              {ENVIRONMENTS.map((env) => {
                const badge = ENV_BADGE[env];
                return (
                  <button
                    key={env}
                    onClick={() => handleDeploy(env)}
                    className="w-full text-left px-3 py-2 text-gray-200 hover:bg-gray-700 transition-colors
                               flex items-center justify-between"
                  >
                    <span>{env}</span>
                    <span className={`text-[10px] px-1.5 py-0.5 rounded font-mono ${badge.className}`}>
                      {badge.text}
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* Deploy result modal (success) */}
      {deployResult && (
        <DeployResultModal
          appName={currentApp?.name ?? 'App'}
          url={deployResult.url}
          result={deployResult.result}
          onClose={() => setDeployResult(null)}
        />
      )}

      {/* Deploy error toast (failure) */}
      {deployError && (
        <div className="fixed top-16 right-4 z-50 max-w-md bg-red-900/95 border border-red-700 text-red-100
                        rounded-lg shadow-xl p-4 text-sm">
          <div className="flex items-start gap-3">
            <span aria-hidden="true" className="text-lg leading-none">⚠️</span>
            <div className="flex-1">
              <div className="font-semibold mb-1">Deploy failed</div>
              <div className="text-red-200 text-xs break-words">{deployError}</div>
            </div>
            <button
              onClick={() => setDeployError(null)}
              className="text-red-300 hover:text-white text-lg leading-none"
              aria-label="Dismiss"
            >×</button>
          </div>
        </div>
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
