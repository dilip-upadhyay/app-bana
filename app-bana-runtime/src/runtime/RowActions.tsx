/**
 * RowActions.tsx — Trailing "⋯" column for list tables.
 *
 * Runtime UX Overhaul Plan §1.9. Reveals on row hover, gives users a place
 * to invoke non-primary actions (Copy ID today; Edit / Delete once the
 * corresponding routes exist).
 */
import { useEffect, useRef, useState } from 'react';

/**
 * A menu item contributed by the host table beyond the built-in Edit / Copy /
 * Delete trio — used for maker-checker actions (Submit for approval, Approve,
 * Reject, History) so an approval-required entity's row menu offers them by
 * default instead of forcing the user to open the record or find a separate
 * checker queue page.
 */
export interface RowActionItem {
  readonly label: string;
  readonly onClick: () => void;
  readonly disabled?: boolean;
  /** Tooltip — used to explain a disabled item, e.g. separation-of-duties. */
  readonly title?: string;
  readonly tone?: 'default' | 'danger';
}

export interface RowActionsProps {
  readonly rowId: string;
  readonly onCopy?: () => void;
  readonly onDelete?: () => void;
  readonly onEdit?: () => void;
  /** Extra menu items rendered between Edit and Copy ID, in array order. */
  readonly extraActions?: readonly RowActionItem[];
}

export function RowActions({ rowId, onCopy, onDelete, onEdit, extraActions }: RowActionsProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLTableCellElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
        triggerRef.current?.focus();
      }
    };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', close);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <td className="appbana-table-row-actions" ref={ref}>
      <button
        ref={triggerRef}
        type="button"
        aria-label={`Actions for row ${rowId}`}
        aria-haspopup="menu"
        aria-expanded={open}
        className="appbana-row-actions-btn"
        onClick={(e) => { e.stopPropagation(); setOpen((v) => !v); }}
      >
        <svg viewBox="0 0 20 20" width="16" height="16" fill="currentColor" aria-hidden="true">
          <circle cx="4"  cy="10" r="1.5" />
          <circle cx="10" cy="10" r="1.5" />
          <circle cx="16" cy="10" r="1.5" />
        </svg>
      </button>
      {open && (
        <div className="appbana-row-actions-menu" role="menu">
          {onEdit && (
            <button
              type="button"
              role="menuitem"
              onClick={() => { setOpen(false); onEdit(); }}
            >
              Edit
            </button>
          )}
          {extraActions?.map((action) => (
            <button
              key={action.label}
              type="button"
              role="menuitem"
              className={action.tone === 'danger' ? 'danger' : undefined}
              disabled={action.disabled}
              title={action.title}
              onClick={() => { setOpen(false); action.onClick(); }}
            >
              {action.label}
            </button>
          ))}
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              setOpen(false);
              if (onCopy) onCopy();
              else if (navigator?.clipboard) navigator.clipboard.writeText(rowId).catch(() => {});
            }}
          >
            Copy ID
          </button>
          {onDelete && (
            <button
              type="button"
              role="menuitem"
              className="danger"
              onClick={() => { setOpen(false); onDelete(); }}
            >
              Delete
            </button>
          )}
        </div>
      )}
    </td>
  );
}
