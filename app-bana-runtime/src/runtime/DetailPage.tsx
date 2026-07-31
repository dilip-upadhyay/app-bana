/**
 * DetailPage.tsx — Sprint 3 tasks 3.3 + 3.4 + 3.5.
 *
 * Renders one record of an entity in either "view" or "edit" mode. Hydrates
 * the record on mount via `getEntityRow`. In view mode we show a plain
 * label/value list; in edit mode we render a light entity form and PUT the
 * updates back through `updateEntityRow`. Delete is available in both modes
 * and confirms via the ConfirmDialog before firing DELETE.
 *
 * Why a bespoke form here (rather than reusing Renderer's EntityForm):
 * EntityForm reads its input tree from ComponentNodes authored in the page
 * meta. Detail pages can be reached from many entities and we don't want to
 * force the scaffolder to emit an edit-mode form for every entity. Instead
 * we derive the fields at runtime from the entity schema — the same shape
 * the studio's Data Drawer already relies on. This keeps DetailPage a
 * single self-contained file with zero coupling to page-meta shape.
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ApprovalTarget, EntityField, EntitySchema, PageMeta } from '@appbana/shared';
import {
  ApiFieldError,
  deleteEntityRow,
  getEntityRow,
  getEntitySchema,
  insertEntityRow,
  resolveAppContext,
  updateEntityRow,
} from '@appbana/shared';
import { Button } from './Button';
import { useConfirm } from './ConfirmDialog';
import { useRuntimeNavigation } from './runtime-navigation';
import { toast } from './Toaster';
import { PageShell } from './PageShell';
import { formatDate } from './cell-formatters';
import { entityNameFromKey } from './page-classifier';
import { renderChildTablesFromPage } from './Renderer';
import { RecordContextProvider } from './RecordContext';
import { RecordApprovalPanel } from './RecordApprovalPanel';
import { readRowValue, APPROVAL_COLUMNS, APPROVAL_STATUS_COLUMN } from './approval-columns';

interface Props {
  readonly page: PageMeta;
  readonly recordId: string;
  /** Called after successful delete so the shell can pop the detail overlay. */
  readonly onDismiss?: () => void;
}

const TOKEN_KEY = 'appbana_token';

function humanizeFieldName(name: string): string {
  return name
    .replace(/_+/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

/**
 * The backend returns different column casing depending on the read path —
 * a plain `SELECT *` over quoted UPPER-case identifiers (single-row GET)
 * yields UPPER-case keys, while `listAdvanced` yields lower-case keys (see
 * `readRowValue`'s doc comment in approval-columns.ts). `EntityField.name`
 * is always lower-case snake_case. Without this normalization, every
 * `record[f.name]` / `draft[f.name]` lookup below silently misses and
 * renders/edits as if the record were empty. Building a fresh object keyed
 * by the canonical field name (plus the approval columns) fixes both view
 * and edit mode at the source, once, instead of special-casing every read.
 */
function normalizeRowKeys(
  row: Record<string, unknown> | null,
  fieldNames: ReadonlyArray<string>
): Record<string, unknown> {
  if (!row) return {};
  const normalized: Record<string, unknown> = { ...row };
  for (const name of [...fieldNames, ...APPROVAL_COLUMNS]) {
    normalized[name] = readRowValue(row, name);
  }
  return normalized;
}

function renderReadOnlyValue(value: unknown, field: EntityField): JSX.Element {
  if (value == null || value === '') {
    return <span className="appbana-detail-value empty">—</span>;
  }
  const text = (() => {
    switch (field.type) {
      case 'date':
      case 'datetime': {
        const fmt = formatDate(value, field.type === 'datetime' ? 'datetime' : 'date');
        return fmt.label || String(value ?? '');
      }
      case 'boolean':
        return value === true || value === 'true' ? 'Yes' : 'No';
      default:
        return typeof value === 'object' ? JSON.stringify(value) : String(value);
    }
  })();
  return <span className="appbana-detail-value">{text}</span>;
}

function inputFor(field: EntityField, value: unknown, onChange: (v: unknown) => void, disabled: boolean) {
  const common = {
    id: `detail-field-${field.name}`,
    name: field.name,
    disabled,
    required: field.required,
  };
  switch (field.type) {
    case 'longtext':
      return (
        <textarea
          {...common}
          className="appbana-textarea"
          rows={4}
          value={value == null ? '' : String(value)}
          onChange={(e) => onChange(e.target.value)}
        />
      );
    case 'boolean':
      return (
        <input
          {...common}
          type="checkbox"
          checked={value === true || value === 'true'}
          onChange={(e) => onChange(e.target.checked)}
        />
      );
    case 'number':
    case 'decimal':
      return (
        <input
          {...common}
          type="number"
          step={field.type === 'decimal' ? 'any' : '1'}
          className="appbana-input"
          value={value == null ? '' : String(value)}
          onChange={(e) => onChange(e.target.value === '' ? null : e.target.value)}
        />
      );
    case 'date':
      return (
        <input
          {...common}
          type="date"
          className="appbana-input"
          value={value ? String(value).slice(0, 10) : ''}
          onChange={(e) => onChange(e.target.value || null)}
        />
      );
    case 'datetime':
      return (
        <input
          {...common}
          type="datetime-local"
          className="appbana-input"
          value={value ? String(value).slice(0, 16) : ''}
          onChange={(e) => onChange(e.target.value || null)}
        />
      );
    case 'status':
      return (
        <select
          {...common}
          className="appbana-select"
          value={value == null ? '' : String(value)}
          onChange={(e) => onChange(e.target.value || null)}
        >
          <option value="">—</option>
          {(field.options ?? []).map((opt) => (
            <option key={opt} value={opt}>{opt}</option>
          ))}
        </select>
      );
    default:
      return (
        <input
          {...common}
          type={field.type === 'email' ? 'email' : 'text'}
          className="appbana-input"
          value={value == null ? '' : String(value)}
          onChange={(e) => onChange(e.target.value)}
        />
      );
  }
}

export function DetailPage({ page, recordId, onDismiss }: Readonly<Props>) {
  const entityKey = page.entityKey ?? ''; // populated by scaffolder for kind='detail'
  const [schema, setSchema] = useState<EntitySchema | null>(null);
  const [record, setRecord] = useState<Record<string, unknown> | null>(null);
  const [draft, setDraft] = useState<Record<string, unknown>>({});
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [mode, setMode] = useState<'view' | 'edit'>('view');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const nav = useRuntimeNavigation();
  const confirm = useConfirm();
  const mountedRef = useRef(true);

  useEffect(() => () => { mountedRef.current = false; }, []);

  // Hydrate schema + record every time the target changes.
  useEffect(() => {
    if (!entityKey || !recordId) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    const token = localStorage.getItem(TOKEN_KEY) ?? '';
    Promise.all([
      getEntitySchema(entityKey, token),
      getEntityRow(entityKey, recordId, token),
    ])
      .then(([sch, rec]) => {
        if (cancelled) return;
        const normalized = rec ? normalizeRowKeys(rec, sch.fields.map((f) => f.name)) : null;
        setSchema(sch);
        setRecord(normalized);
        setDraft(normalized ?? {});
      })
      .catch((err) => {
        if (cancelled) return;
        toast.error('Failed to load record', {
          description: err instanceof Error ? err.message : String(err),
        });
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [entityKey, recordId]);

  const displayFields = useMemo<EntityField[]>(() => {
    if (!schema) return [];
    return schema.fields.filter((f) => !f.autoIncrement);
  }, [schema]);

  // created_at/updated_at are system-managed audit columns (see
  // EntityCrudService's auto-fill-on-insert and ApprovalService's
  // CREATED_AT-is-immutable rule) — they must never be hand-edited. Still
  // shown read-only via `displayFields` in view mode, but excluded here so
  // edit mode never renders them as inputs. Rendering them as editable
  // native datetime-local inputs previously sent a seconds-less value the
  // backend's TIMESTAMP coercion rejected with "invalid format" on every
  // save attempt, even when the user never touched those fields.
  const editableFields = useMemo<EntityField[]>(
    () => displayFields.filter((f) => f.name !== 'created_at' && f.name !== 'updated_at'),
    [displayFields]
  );

  const entityLabel = entityNameFromKey(entityKey) || 'Record';

  // C3.9 — the maker's approval surface needs the unqualified entity name plus
  // the tenant/app the record lives in, which is the shape the approval
  // endpoints take. Null when the runtime cannot resolve a context, in which
  // case the panel degrades to nothing rather than issuing bad URLs.
  const approvalTarget = useMemo<ApprovalTarget | null>(() => {
    const ctx = resolveAppContext(window.location);
    if (!ctx || !entityKey || !recordId) return null;
    return {
      tenantId: ctx.tenantId,
      appId: ctx.appId,
      entityName: entityNameFromKey(entityKey) || entityKey,
      rowId: recordId,
    };
  }, [entityKey, recordId]);

  const refreshRecord = useCallback(async () => {
    const token = localStorage.getItem(TOKEN_KEY) ?? '';
    try {
      const fresh = await getEntityRow(entityKey, recordId, token);
      if (!mountedRef.current) return;
      const normalized = fresh && schema ? normalizeRowKeys(fresh, schema.fields.map((f) => f.name)) : fresh;
      setRecord(normalized);
      setDraft(normalized ?? {});
      window.dispatchEvent(new CustomEvent('appbana:row-updated', {
        detail: { entity: entityKey, id: recordId },
      }));
    } catch {
      // A failed refresh must not look like a failed action — the caller has
      // already reported the outcome of the action itself.
    }
  }, [entityKey, recordId, schema]);

  // A PENDING record is owned by the checker; the backend refuses PUTs on it
  // (applyApprovalPutGuard → BLOCKED_PENDING). Offering Edit would be an
  // invitation to retype a form that cannot be saved.
  const approvalStatus = readRowValue(record, APPROVAL_STATUS_COLUMN);
  const editLocked = String(approvalStatus ?? '').toUpperCase() === 'PENDING';

  const setField = useCallback((name: string, value: unknown) => {
    setDraft((prev) => ({ ...prev, [name]: value }));
    setFieldErrors((prev) => {
      if (!(name in prev)) return prev;
      const { [name]: _drop, ...rest } = prev;
      return rest;
    });
  }, []);

  const handleSave = useCallback(async () => {
    if (!schema || !record) return;
    setSaving(true);
    try {
      const token = localStorage.getItem(TOKEN_KEY) ?? '';
      // Send only the user-editable fields — never the full `draft` blob,
      // which still carries read-only/system columns (created_at, updated_at,
      // approval metadata) copied straight from the fetched record. Forwarding
      // those back on every save is what caused "invalid format" 400s on
      // saves that never touched those fields.
      const payload: Record<string, unknown> = {};
      for (const f of editableFields) {
        payload[f.name] = draft[f.name];
      }
      const result = await updateEntityRow(entityKey, recordId, payload, token);
      // Refresh the canonical record after save.
      const fresh = await getEntityRow(entityKey, recordId, token);
      if (!mountedRef.current) return;
      const normalized = fresh ? normalizeRowKeys(fresh, schema.fields.map((f) => f.name)) : fresh;
      setRecord(normalized);
      setDraft(normalized ?? draft);
      setMode('view');
      setFieldErrors({});

      // C3.9 — an approval-required entity answers an edit of an APPROVED
      // record with a *revision*: a separate DRAFT row (C2.3). The original is
      // deliberately left untouched. Reporting a bare "Saved" here and then
      // re-rendering the unchanged parent told the user their edit had been
      // lost. Say what actually happened, and give them the way to act on it.
      if (result.revision && result.revisionId) {
        toast.success('Saved as a new draft revision', {
          description: 'The approved record is unchanged until a checker approves your revision.',
          action: {
            label: 'Open revision',
            onClick: () => nav?.navigateToRecord(page, String(result.revisionId)),
          },
        });
      } else {
        toast.success('Saved');
      }
      window.dispatchEvent(new CustomEvent('appbana:row-updated', {
        detail: { entity: entityKey, id: recordId },
      }));
    } catch (err) {
      if (err instanceof ApiFieldError) {
        setFieldErrors(err.fieldErrors);
        toast.error('Please fix the highlighted fields', {
          description: err.fieldErrors._form,
        });
      } else {
        toast.error('Save failed', {
          description: err instanceof Error ? err.message : String(err),
        });
      }
    } finally {
      if (mountedRef.current) setSaving(false);
    }
  }, [schema, record, entityKey, recordId, draft, editableFields, nav, page]);

  const handleDelete = useCallback(async () => {
    if (!record) return;
    const ok = await confirm({
      title: `Delete ${entityLabel}?`,
      message: 'This cannot be truly undone — the notification lets you recreate the row with the same fields, but any links to other records will not be restored.',
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!ok) return;
    const token = localStorage.getItem(TOKEN_KEY) ?? '';
    const snapshot = record;
    try {
      await deleteEntityRow(entityKey, recordId, token);
      window.dispatchEvent(new CustomEvent('appbana:row-deleted', {
        detail: { entity: entityKey, id: recordId },
      }));
      toast.success(`${entityLabel} deleted`, {
        description: 'Use Recreate to insert the same fields back as a new record (with a new id).',
        // Sprint 3 task 3.10 (post-review: honest copy) — action slot
        // re-inserts the row as a fresh record. It gets a new PK, so this
        // is a "Recreate", not a genuine undo. Backend soft-delete would
        // be needed to restore the original id + inbound FKs.
        action: {
          label: 'Recreate',
          onClick: async () => {
            try {
              await insertEntityRow(entityKey, snapshot, token);
              window.dispatchEvent(new CustomEvent('appbana:row-inserted', {
                detail: { entity: entityKey },
              }));
              toast.info(`${entityLabel} recreated as a new record`);
            } catch (err) {
              toast.error('Recreate failed', {
                description: err instanceof Error ? err.message : String(err),
              });
            }
          },
        },
      });
      onDismiss?.();
    } catch (err) {
      toast.error('Delete failed', {
        description: err instanceof Error ? err.message : String(err),
      });
    }
  }, [record, entityKey, recordId, entityLabel, onDismiss, confirm]);

  const backAction = nav
    ? {
        label: 'Back',
        onClick: () => onDismiss?.(),
      }
    : undefined;

  if (loading) {
    return (
      <PageShell title={`Loading ${entityLabel}…`}>
        <div className="appbana-form-skeleton">
          {Array.from({ length: 4 }, (_, i) => (
            <div key={i} className="appbana-form-skeleton-field">
              <span className="appbana-skeleton h-3 w-32" />
              <span className="appbana-skeleton h-9 w-full" />
            </div>
          ))}
        </div>
      </PageShell>
    );
  }

  if (!record) {
    return (
      <PageShell title={`${entityLabel} not found`} subtitle={`No record with id ${recordId}.`}>
        {backAction && (
          <Button variant="secondary" onClick={backAction.onClick}>
            {backAction.label}
          </Button>
        )}
      </PageShell>
    );
  }

  return (
    <PageShell
      title={mode === 'edit' ? `Edit ${entityLabel}` : entityLabel}
      subtitle={`Record #${recordId}`}
      actions={
        <>
          {backAction && (
            <Button variant="tertiary" onClick={backAction.onClick}>
              {backAction.label}
            </Button>
          )}
          {mode === 'view' ? (
            <>
              <Button
                variant="secondary"
                onClick={() => setMode('edit')}
                disabled={editLocked}
                title={editLocked ? 'This record is awaiting approval and cannot be edited' : undefined}
              >
                Edit
              </Button>
              <Button variant="danger" onClick={handleDelete}>Delete</Button>
            </>
          ) : (
            <>
              <Button
                variant="tertiary"
                onClick={() => {
                  setDraft(record);
                  setFieldErrors({});
                  setMode('view');
                }}
                disabled={saving}
              >
                Cancel
              </Button>
              <Button variant="primary" loading={saving} onClick={handleSave}>
                Save
              </Button>
            </>
          )}
        </>
      }
    >
      {/* C3.9 — the maker's approval surface: current state, the checker's
          rejection reason, the audit trail, and the submit/resubmit action
          that C3 never built. Renders nothing for non-approval entities. */}
      <RecordApprovalPanel
        record={record}
        target={approvalTarget}
        recordLabel={`${entityLabel} #${recordId}`}
        onChanged={() => { void refreshRecord(); }}
      />

      <div className="appbana-page-card">
        {mode === 'view'
          ? displayFields.map((f) => (
              <div key={f.name} className="appbana-detail-field">
                <span className="appbana-detail-label">
                  {f.label ?? humanizeFieldName(f.name)}
                </span>
                {renderReadOnlyValue(record[f.name], f)}
              </div>
            ))
          : (
            <form className="appbana-form" onSubmit={(e) => { e.preventDefault(); void handleSave(); }}>
              {editableFields.map((f) => {
                const err = fieldErrors[f.name];
                return (
                  <div
                    key={f.name}
                    className={`appbana-field ${err ? 'appbana-field-invalid' : ''}`}
                  >
                    <label
                      htmlFor={`detail-field-${f.name}`}
                      className="appbana-field-label"
                    >
                      {f.label ?? humanizeFieldName(f.name)}
                      {f.required && <span className="appbana-field-required"> *</span>}
                    </label>
                    {inputFor(f, draft[f.name], (v) => setField(f.name, v), saving)}
                    {err && <p role="alert" className="appbana-field-error">{err}</p>}
                  </div>
                );
              })}
            </form>
          )
        }
      </div>
      {/* H2 hardening — surface any child_table nodes the scaffolder placed
          in the page meta. RecordContextProvider makes the current recordId
          available so ChildTable can auto-filter its rows to this parent. */}
      <RecordContextProvider value={{ recordId, entityKey }}>
        {renderChildTablesFromPage(page)}
      </RecordContextProvider>
    </PageShell>
  );
}
