import { useEffect, useState } from 'react';
import { login, register, fetchBranding } from '@appbana/shared';
import { useSessionStore } from '../../stores/session';
import { useWorkspaceStore } from '../../stores/workspace';
import { resetSessionScopedState } from '../../stores/sessionBoundary';

export function AuthGate({ children }: { children: React.ReactNode }) {
  const { token, setSession, clearSession } = useSessionStore();
  const { setBranding } = useWorkspaceStore();
  const [tab, setTab] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [sessionExpired, setSessionExpired] = useState(false);

  // Global 401 recovery: api-client's authedFetch broadcasts this event whenever
  // the backend returns 401 (typical after the backend or ai-builder restarts —
  // the persisted token becomes invalid). Instead of silently returning empty
  // data in every consumer, we clear the session and show the login form with
  // a friendly banner so the user knows to sign back in.
  useEffect(() => {
    const handler = () => {
      if (useSessionStore.getState().token) {
        setSessionExpired(true);
        clearSession();
        // S2.8/S2.8-followup: also clear every session-scoped store (app
        // switcher's apps/currentApp/branding, chat history/attachments).
        // Without this, whoever re-authenticates next in this tab -- the same
        // user after an idle timeout, or a different user on a shared machine --
        // would briefly see the previous session's data rendered as if it were
        // already confirmed for them, before the fresh tenant-scoped fetches
        // resolve. Nothing here may assume carried-over client state from a
        // prior session is still valid.
        resetSessionScopedState();
      }
    };
    window.addEventListener('appbana:auth:expired', handler);
    return () => window.removeEventListener('appbana:auth:expired', handler);
  }, [clearSession]);

  if (token) return <>{children}</>;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const result = tab === 'login'
        ? await login(email, password)
        : await register(name, email, password);
      const branding = await fetchBranding(result.tenantId);
      setBranding(branding);
      setSession({
        token: result.token,
        userId: result.userId,
        email: result.email,
        name: result.name,
        tenantId: result.tenantId,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Authentication failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-950">
      <div className="w-full max-w-sm bg-gray-900 rounded-2xl shadow-2xl p-8 border border-gray-800">
        <div className="flex items-center gap-3 mb-8">
          <img src="/logo.svg" alt="AppBana" className="h-9 w-9 object-contain" draggable={false} />
          <span className="text-xl font-bold text-white">AppBana Studio</span>
        </div>

        {sessionExpired && (
          <div className="mb-4 rounded-lg border border-amber-700/60 bg-amber-950/40 px-3 py-2
                          text-xs text-amber-200">
            Your session expired. Please sign in again to continue.
          </div>
        )}

        {/* Tabs */}
        <div className="flex gap-1 mb-6 bg-gray-800 rounded-lg p-1">
          {(['login', 'register'] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={`flex-1 py-1.5 rounded-md text-sm font-medium transition-colors
                ${tab === t ? 'bg-indigo-600 text-white' : 'text-gray-400 hover:text-white'}`}
            >
              {t === 'login' ? 'Sign In' : 'Create Account'}
            </button>
          ))}
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {tab === 'register' && (
            <input
              className="bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 text-sm text-white
                         placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              placeholder="Your name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          )}
          <input
            type="email"
            className="bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 text-sm text-white
                       placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          <input
            type="password"
            className="bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 text-sm text-white
                       placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            placeholder="••••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          {error && <p className="text-red-400 text-sm">{error}</p>}
          <button
            type="submit"
            disabled={loading}
            className="bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-semibold
                       py-2 rounded-lg transition-colors"
          >
            {loading ? 'Please wait…' : tab === 'login' ? 'Sign In' : 'Create Account'}
          </button>
        </form>
      </div>
    </div>
  );
}
