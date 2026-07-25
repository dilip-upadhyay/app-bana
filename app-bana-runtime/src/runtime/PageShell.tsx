/**
 * PageShell.tsx — Mandatory frame around every rendered page.
 *
 * Adds the missing page header (title / subtitle / breadcrumb / actions slot)
 * that the runtime was silently omitting for every scaffolded app.
 *
 * Runtime UX Overhaul Plan §0.4 + §1.1.
 */
import type { ReactNode } from 'react';

export interface Crumb {
  readonly label: string;
  readonly onClick?: () => void;
}

interface PageShellProps {
  readonly title: string;
  readonly subtitle?: string;
  readonly breadcrumb?: readonly Crumb[];
  readonly actions?: ReactNode;
  readonly children: ReactNode;
}

export function PageShell({ title, subtitle, breadcrumb, actions, children }: PageShellProps) {
  return (
    <div className="appbana-page-shell">
      <header className="appbana-page-header">
        <div className="min-w-0">
          {breadcrumb && breadcrumb.length > 0 && (
            <nav className="appbana-page-crumbs" aria-label="Breadcrumb">
              {breadcrumb.map((crumb, i) => (
                <span key={`${crumb.label}-${i}`} className="flex items-center gap-1.5">
                  {crumb.onClick ? (
                    <button
                      type="button"
                      onClick={crumb.onClick}
                      className="hover:text-slate-700 hover:underline focus:outline-none focus:text-slate-900"
                    >
                      {crumb.label}
                    </button>
                  ) : (
                    <span>{crumb.label}</span>
                  )}
                  {i < breadcrumb.length - 1 && <span className="sep" aria-hidden="true">›</span>}
                </span>
              ))}
            </nav>
          )}
          <h1 className="appbana-page-title">{title}</h1>
          {subtitle && <p className="appbana-page-subtitle">{subtitle}</p>}
        </div>
        {actions && <div className="appbana-page-actions">{actions}</div>}
      </header>
      <div className="appbana-page-body">{children}</div>
    </div>
  );
}
