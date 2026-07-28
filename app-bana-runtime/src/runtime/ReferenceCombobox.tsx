/**
 * ReferenceCombobox.tsx — Sprint 3 task 3.7.
 *
 * When a foreign-key field targets an entity with more than a handful of
 * rows, the classic `<select>` becomes unusable. This component provides
 * an accessible combobox instead:
 *
 *   - Debounced server-side search (300ms) via `fetchEntityRows(..., {q})`.
 *     (C3.10 — this used to send `{search}`, a key the backend's query-param
 *     allowlist does not read; typing here silently re-fetched page 1.)
 *   - Keyboard nav: ArrowUp / ArrowDown / Enter / Escape / Tab.
 *   - Scroll-to-bottom pagination (adds 20 more rows per fetch).
 *   - Renders the same human label as `StudioTableLive` via `pickReferenceLabel`.
 *   - Preserves the initial value's label by fetching just that record on mount
 *     so the field looks resolved even before the user types.
 *
 * A hidden `<input name>` carries the selected id so the surrounding
 * `<form>` submission continues to work exactly like the native select
 * variant that `ReferenceField` renders for small target tables.
 */
import {
  useCallback,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
} from 'react';
import { fetchEntityRows, getEntityRow } from '@appbana/shared';
import { qualifyEntityKey, getRuntimeToken } from './qualifyEntityKey';
import { pickReferenceLabel } from './cell-formatters';

export interface ReferenceComboboxProps {
  readonly id: string;
  readonly name: string;
  readonly refEntity: string;
  readonly required?: boolean;
  readonly disabled?: boolean;
  readonly defaultValue?: string;
  readonly className?: string;
  readonly style?: React.CSSProperties;
  readonly onValueChange?: (id: string, label: string) => void;
  readonly entityAttr?: Record<string, string | undefined>;
  readonly fieldAttr?: Record<string, string | undefined>;
}

interface Row {
  readonly id: string;
  readonly label: string;
}

const PAGE_SIZE = 20;
const DEBOUNCE_MS = 300;

function toRow(rec: Record<string, unknown>): Row | null {
  const raw = rec.id ?? rec.ID;
  if (raw == null) return null;
  const id = typeof raw === 'string' || typeof raw === 'number' ? String(raw) : '';
  if (!id) return null;
  return { id, label: pickReferenceLabel(rec) || `#${id}` };
}

export function ReferenceCombobox(props: Readonly<ReferenceComboboxProps>) {
  const {
    id,
    name,
    refEntity,
    required = false,
    disabled = false,
    defaultValue = '',
    className = '',
    style,
    onValueChange,
    entityAttr,
    fieldAttr,
  } = props;

  const [selectedId, setSelectedId] = useState<string>(defaultValue);
  const [selectedLabel, setSelectedLabel] = useState<string>('');
  const [query, setQuery] = useState('');
  const [rows, setRows] = useState<Row[]>([]);
  const [offset, setOffset] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [activeIdx, setActiveIdx] = useState(-1);

  const wrapRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const listboxRef = useRef<HTMLUListElement>(null);
  const listboxId = useId();

  const qualifiedKey = useMemo(() => qualifyEntityKey(refEntity), [refEntity]);

  // ── Hydrate the initial value's label so the field displays it on mount.
  useEffect(() => {
    if (!defaultValue) return;
    let cancelled = false;
    getEntityRow(qualifiedKey, defaultValue, getRuntimeToken())
      .then((rec) => {
        if (cancelled || !rec) return;
        const row = toRow(rec);
        if (row) setSelectedLabel(row.label);
      })
      .catch(() => { /* label falls back to id */ });
    return () => { cancelled = true; };
  }, [defaultValue, qualifiedKey]);

  // ── Debounced search fetch.
  const fetchPage = useCallback(async (searchTerm: string, pageOffset: number, append: boolean) => {
    if (!refEntity) return;
    setLoading(true);
    try {
      const params: Record<string, string | number> = {
        limit: PAGE_SIZE,
        offset: pageOffset,
      };
      if (searchTerm) params.q = searchTerm;
      const result = await fetchEntityRows(qualifiedKey, getRuntimeToken(), params);
      const mapped: Row[] = [];
      for (const rec of result.rows) {
        const r = toRow(rec as Record<string, unknown>);
        if (r) mapped.push(r);
      }
      setRows((prev) => (append ? [...prev, ...mapped] : mapped));
      setHasMore(mapped.length === PAGE_SIZE);
    } catch {
      setRows((prev) => (append ? prev : []));
      setHasMore(false);
    } finally {
      setLoading(false);
    }
  }, [qualifiedKey, refEntity]);

  useEffect(() => {
    if (!open) return;
    const handle = setTimeout(() => {
      setOffset(0);
      fetchPage(query, 0, false).catch(() => {});
    }, DEBOUNCE_MS);
    return () => clearTimeout(handle);
  }, [query, open, fetchPage]);

  // ── Click-outside → close.
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
        setOpen(false);
        setActiveIdx(-1);
      }
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);

  const commit = useCallback((row: Row) => {
    setSelectedId(row.id);
    setSelectedLabel(row.label);
    setQuery('');
    setOpen(false);
    setActiveIdx(-1);
    onValueChange?.(row.id, row.label);
    inputRef.current?.focus();
  }, [onValueChange]);

  const clear = useCallback(() => {
    setSelectedId('');
    setSelectedLabel('');
    setQuery('');
    onValueChange?.('', '');
    inputRef.current?.focus();
  }, [onValueChange]);

  const onKeyDown = useCallback((e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (!open) { setOpen(true); return; }
      setActiveIdx((i) => Math.min(rows.length - 1, i + 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIdx((i) => Math.max(0, i - 1));
    } else if (e.key === 'Enter') {
      if (open && activeIdx >= 0 && rows[activeIdx]) {
        e.preventDefault();
        commit(rows[activeIdx]);
      }
    } else if (e.key === 'Escape') {
      if (open) { e.preventDefault(); setOpen(false); setActiveIdx(-1); }
    } else if (e.key === 'Tab') {
      setOpen(false);
    }
  }, [open, rows, activeIdx, commit]);

  const onScroll = useCallback((e: React.UIEvent<HTMLUListElement>) => {
    if (!hasMore || loading) return;
    const el = e.currentTarget;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 24) {
      const next = offset + PAGE_SIZE;
      setOffset(next);
      fetchPage(query, next, true).catch(() => {});
    }
  }, [hasMore, loading, offset, query, fetchPage]);

  const displayValue = open ? query : (selectedLabel || (selectedId ? `#${selectedId}` : ''));

  return (
    <div
      ref={wrapRef}
      className={`appbana-combobox ${className}`.trim()}
      style={style}
    >
      {/* Hidden field carries the selected id to the surrounding <form>. */}
      <input type="hidden" name={name} value={selectedId} />

      <input
        ref={inputRef}
        id={id}
        type="text"
        role="combobox"
        aria-expanded={open}
        aria-controls={listboxId}
        aria-autocomplete="list"
        aria-required={required}
        aria-activedescendant={activeIdx >= 0 ? `${listboxId}-opt-${activeIdx}` : undefined}
        className="appbana-combobox-input"
        placeholder={selectedLabel ? '' : `Search ${refEntity}…`}
        value={displayValue}
        disabled={disabled}
        onFocus={() => setOpen(true)}
        onChange={(e) => { setQuery(e.target.value); if (!open) setOpen(true); }}
        onKeyDown={onKeyDown}
        autoComplete="off"
        {...entityAttr}
        {...fieldAttr}
      />

      {selectedId && !open && !disabled && (
        <button
          type="button"
          className="appbana-combobox-clear"
          aria-label="Clear selection"
          onClick={clear}
        >
          ×
        </button>
      )}

      {open && (
        <ul
          ref={listboxRef}
          id={listboxId}
          role="listbox"
          className="appbana-combobox-listbox"
          onScroll={onScroll}
        >
          {rows.length === 0 && !loading && (
            <li className="appbana-combobox-empty" role="presentation">
              No matches
            </li>
          )}
          {rows.map((row, i) => {
            const active = i === activeIdx;
            const selected = row.id === selectedId;
            const cls = `appbana-combobox-option${active ? ' active' : ''}${selected ? ' selected' : ''}`;
            return (
              <li
                key={row.id}
                id={`${listboxId}-opt-${i}`}
                role="option"
                aria-selected={selected}
                className={cls}
                onMouseDown={(e) => { e.preventDefault(); commit(row); }}
                onMouseEnter={() => setActiveIdx(i)}
              >
                {row.label}
              </li>
            );
          })}
          {loading && (
            <li className="appbana-combobox-empty" role="presentation">
              Loading…
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
