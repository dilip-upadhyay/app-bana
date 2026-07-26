/**
 * LoginPage.tsx — Tenant-branded login screen for the standalone runtime.
 *
 * Stage 2 requirement: loads branding before rendering, applies logo +
 * primary colour. When embedded in the studio (postMessage token received),
 * this page is never shown — AppRuntimeShell skips it.
 *
 * Sprint 3 tasks 3.8 + 3.9 + 3.11(c):
 *   • Submit button migrated to the unified <Button variant="primary"> primitive.
 *   • Primary colour applied by writing --color-brand at :root, so hover /
 *     focus / active states inherit tenant tint (no inline style leakage).
 *   • Heading structure fixed: <h1> is the page label "Sign In", tenant
 *     display name demoted to a semantic <p> so screen readers announce
 *     the page purpose first.
 */
import { useEffect, useState } from 'react';
import type { TenantBranding } from '@appbana/shared';
import { fetchBranding } from '@appbana/shared';
import { Button } from '../runtime/Button';
import { applyBrandRamp } from '../runtime/applyBrandRamp';

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

  // Task 3.9 (post-review: extracted to applyBrandRamp helper) — mirror the
  // ramp AppRuntimeShell computes post-login so the login card, focus rings,
  // and submit button all share the tenant tint.
  useEffect(() => {
    applyBrandRamp(branding?.primaryColor);
  }, [branding?.primaryColor]);

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

  const displayName = branding?.displayName ?? tenantId;

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-sm bg-white rounded-2xl shadow-xl p-8 flex flex-col gap-6">
        {/* Branding — logo + tenant label (task 3.11c: demote to <p>) */}
        <div className="flex flex-col items-center gap-2">
          {branding?.logoUrl ? (
            <img
              src={branding.logoUrl}
              alt={`${displayName} logo`}
              className="h-16 object-contain"
            />
          ) : (
            <img
              src="/logo.svg"
              alt="AppBana"
              className="h-16 object-contain"
              draggable={false}
            />
          )}
          <p className="text-sm font-medium text-gray-500" aria-label="Signing in to">
            {displayName}
          </p>
          <h1 className="text-xl font-bold text-gray-800">Sign In</h1>
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

          <Button type="submit" variant="primary" size="lg" loading={loading} className="w-full">
            {loading ? 'Signing in…' : 'Sign In'}
          </Button>
        </form>
      </div>
    </div>
  );
}
