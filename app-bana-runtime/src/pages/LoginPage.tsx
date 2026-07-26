/**
 * LoginPage.tsx — Tenant-branded login screen for the standalone runtime.
 *
 * Stage 2 requirement: loads branding before rendering, applies logo +
 * primary colour. When embedded in the studio (postMessage token received),
 * this page is never shown — AppRuntimeShell skips it.
 */
import { useEffect, useState } from 'react';
import type { TenantBranding } from '@appbana/shared';
import { fetchBranding } from '@appbana/shared';

interface Props {
  tenantId: string;
  onLogin: (email: string, password: string) => Promise<string>;
}

export function LoginPage({ tenantId, onLogin }: Props) {
  const [branding, setBranding] = useState<TenantBranding | null>(null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchBranding(tenantId).then(setBranding).catch(() => {});
  }, [tenantId]);

  const primaryColor = branding?.primaryColor ?? '#6366f1';

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await onLogin(email, password);
      // Parent (AppRuntimeShell) handles the state update on success
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div
      className="min-h-screen flex flex-col items-center justify-center bg-gray-50 px-4"
      style={{ '--color-brand': primaryColor } as React.CSSProperties}
    >
      <div className="w-full max-w-sm bg-white rounded-2xl shadow-xl p-8 flex flex-col gap-6">
        {/* Branding */}
        <div className="flex flex-col items-center gap-2">
          {branding?.logoUrl ? (
            <img
              src={branding.logoUrl}
              alt={`${branding.displayName ?? tenantId} logo`}
              className="h-10 object-contain"
            />
          ) : (
            <span className="text-3xl" aria-hidden="true">🍌</span>
          )}
          <h1 className="text-xl font-bold text-gray-800">
            {branding?.displayName ?? 'AppBana'}
          </h1>
          <p className="text-sm text-gray-400">Sign in to continue</p>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" aria-label="Sign in">
          <div className="flex flex-col gap-1">
            <label htmlFor="appbana-login-email" className="text-xs font-medium text-gray-600">
              Email
            </label>
            <input
              id="appbana-login-email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              required
              aria-required="true"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none
                         focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="appbana-login-password" className="text-xs font-medium text-gray-600">
              Password
            </label>
            <input
              id="appbana-login-password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              required
              aria-required="true"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none
                         focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          {error && (
            <p className="text-red-500 text-xs" role="alert">{error}</p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2 rounded-lg text-sm font-semibold text-white
                       disabled:opacity-60 disabled:cursor-not-allowed transition-opacity
                       focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"
            style={{ backgroundColor: primaryColor }}
          >
            {loading ? 'Signing in…' : 'Sign In'}
          </button>
        </form>
      </div>
    </div>
  );
}
