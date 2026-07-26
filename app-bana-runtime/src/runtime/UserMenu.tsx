/**
 * UserMenu.tsx — Sprint 2 Task 2.7.
 *
 * Top-right dropdown for the runtime shell. Shows an avatar with the
 * user's initials; clicking it opens a panel with the user's name /
 * email, the tenant display name, and a "Sign out" button.
 *
 * Zero-dep dropdown (outside-click + Escape close). The plan calls for
 * shadcn's dropdown-menu, but the rest of the runtime is deliberately
 * zero-dep with plain Tailwind classes so we don't ship the whole
 * shadcn primitive set for one menu. Behaviour matches shadcn's spec
 * (role="menu" on the panel, role="menuitem" on actionable rows,
 * aria-expanded / aria-haspopup on the trigger).
 *
 * Sign-out is client-side: we clear `appbana_token` + `appbana_user`
 * from localStorage and hard-reload so the shell drops back to the
 * LoginPage. The backend does not (yet) expose `/api/auth/logout`;
 * when it does, we can call it from `handleSignOut` before the
 * reload.
 */
import * as React from 'react';
import { useCallback, useEffect, useRef, useState } from 'react';

const TOKEN_KEY = 'appbana_token';
const USER_KEY = 'appbana_user';

export interface UserMenuUser {
  readonly id?: string;
  readonly email?: string;
  readonly name?: string;
  readonly tenantId?: string;
}

export interface UserMenuProps {
  /** Tenant id (fallback label when tenantDisplayName isn't provided). */
  readonly tenantId: string;
  /** Human-readable tenant name (e.g., "Acme Spices"). Falls back to tenantId. */
  readonly tenantDisplayName?: string;
  /**
   * User info. When omitted, the component reads `appbana_user` from
   * localStorage; useful for tests.
   */
  readonly user?: UserMenuUser | null;
  /**
   * Sign-out override — useful for tests. Defaults to clearing storage
   * and reloading the page.
   */
  readonly onSignOut?: () => void;
}

/** Two-letter initials from a full name, falling back to the email local-part. */
export function initialsFor(name?: string | null, email?: string | null): string {
  const source = (name ?? '').trim() || (email ?? '').split('@')[0] || '';
  if (!source) return '?';
  const parts = source.split(/[\s._-]+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  const last = parts.at(-1) ?? '';
  return (parts[0][0] + last[0]).toUpperCase();
}

function readStoredUser(): UserMenuUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as UserMenuUser;
    return parsed && typeof parsed === 'object' ? parsed : null;
  } catch {
    return null;
  }
}

function defaultSignOut() {
  try {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  } catch {
    // ignore — private mode / disabled storage
  }
  window.location.reload();
}

export function UserMenu(props: Readonly<UserMenuProps>) {
  const { tenantId, tenantDisplayName, user: userProp, onSignOut } = props;
  const [open, setOpen] = useState(false);
  const [user, setUser] = useState<UserMenuUser | null>(userProp ?? null);
  const rootRef = useRef<HTMLDivElement | null>(null);
  const triggerRef = useRef<HTMLButtonElement | null>(null);

  // Hydrate from localStorage on mount when caller didn't pass one.
  useEffect(() => {
    if (userProp === undefined) {
      setUser(readStoredUser());
    } else {
      setUser(userProp);
    }
  }, [userProp]);

  // Outside-click + Escape to close.
  useEffect(() => {
    if (!open) return;
    function onDocClick(ev: MouseEvent) {
      if (!rootRef.current) return;
      if (rootRef.current.contains(ev.target as Node)) return;
      setOpen(false);
    }
    function onKey(ev: KeyboardEvent) {
      if (ev.key === 'Escape') {
        setOpen(false);
        triggerRef.current?.focus();
      }
    }
    document.addEventListener('mousedown', onDocClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDocClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const handleSignOut = useCallback(() => {
    setOpen(false);
    (onSignOut ?? defaultSignOut)();
  }, [onSignOut]);

  const initials = initialsFor(user?.name, user?.email);
  const primary = (user?.name?.trim() || user?.email || 'Signed in').trim();
  const secondary = user?.name?.trim() && user?.email ? user.email : '';
  const tenantLabel = (tenantDisplayName?.trim() || tenantId || 'default').trim();

  return (
    <div ref={rootRef} className="appbana-user-menu relative">
      <button
        ref={triggerRef}
        type="button"
        className="appbana-user-menu-trigger"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Account menu for ${primary}`}
        onClick={() => setOpen((v) => !v)}
      >
        <span className="appbana-user-avatar" aria-hidden="true">{initials}</span>
      </button>

      {open && (
        <div
          className="appbana-user-menu-panel"
          role="menu"
          aria-orientation="vertical"
          aria-label="Account menu"
        >
          <div className="appbana-user-menu-header">
            <p className="appbana-user-menu-name">{primary}</p>
            {secondary && <p className="appbana-user-menu-email">{secondary}</p>}
            <p className="appbana-user-menu-tenant">
              <span className="appbana-user-menu-tenant-label">Tenant</span>
              <span className="appbana-user-menu-tenant-value">{tenantLabel}</span>
            </p>
          </div>
          <hr className="appbana-user-menu-divider" />
          <button
            type="button"
            className="appbana-user-menu-item"
            role="menuitem"
            onClick={handleSignOut}
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.75"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="w-4 h-4"
              aria-hidden="true"
            >
              <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" />
              <polyline points="10 17 15 12 10 7" />
              <line x1="15" y1="12" x2="3" y2="12" />
            </svg>
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}
