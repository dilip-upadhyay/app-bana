/**
 * Renderer.tsx — React port of app-bana-ui/src/runtime/renderer/Renderer.ts
 *
 * Converts a PageMeta node tree into JSX, emitting data-appbana-* attrs on
 * every element so Stage 6 (select-and-instruct) can identify them.
 *
 * Form controls (input / select / textarea) are always wrapped in an
 * `.appbana-field` block with a persistent `<label>` derived from
 * props.label / name / placeholder — so the field's meaning stays visible
 * after the user starts typing.
 */
import { useEffect, useState } from 'react';
import type { PageMeta, ComponentNode } from '@appbana/shared';
import { fetchEntityRows, insertEntityRow } from '@appbana/shared';
import { StudioTableLive } from './StudioTableLive';
import { qualifyEntityKey, getRuntimeToken } from './qualifyEntityKey';

/** Turn "full_name" / "first-name" / "firstName" into "Full name". */
function humanize(raw: string | undefined): string {
  if (!raw) return '';
  return raw
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/^./, (c) => c.toUpperCase());
}

function fieldLabel(props: Record<string, unknown>): string {
  const label = props.label as string | undefined;
  if (label && String(label).trim()) return String(label);
  const name = props.name as string | undefined;
  if (name && String(name).trim()) return humanize(String(name));
  const placeholder = props.placeholder as string | undefined;
  if (placeholder && String(placeholder).trim()) return String(placeholder);
  return '';
}

export function renderPage(page: PageMeta): React.ReactElement {
  const nodeMap = new Map(page.nodes.map((n) => [n.id, n]));
  const root = nodeMap.get(page.rootId);
  if (!root) return <div className="text-red-500 p-4">Root node not found: {page.rootId}</div>;
  return renderNode(root, nodeMap, page.id);
}

function renderNode(
  node: ComponentNode,
  nodeMap: Map<string, ComponentNode>,
  pageId: string
): React.ReactElement {
  const props = node.props ?? {};
  const className = (props.className as string) ?? '';
  const style = (props.style as React.CSSProperties | string) ?? '';
  const styleObj: React.CSSProperties = typeof style === 'string' ? {} : style;

  const dataAttrs = {
    'data-appbana-node': node.id,
    'data-appbana-page': pageId,
  };
  const entityAttr = props.entity ? { 'data-appbana-entity': props.entity as string } : {};
  const fieldAttr  = props.field  ? { 'data-appbana-field':  props.field as string  } : {};

  const children = (node.children ?? []).map((childId) => {
    const child = nodeMap.get(childId);
    return child ? renderNode(child, nodeMap, pageId) : null;
  });

  switch (node.type) {
    case 'text':
      return (
        <p
          key={node.id}
          className={className}
          style={styleObj}
          {...dataAttrs}
        >
          {String(props.content ?? props.text ?? '')}
        </p>
      );

    case 'button':
      return (
        <button
          key={node.id}
          className={`appbana-button ${className}`}
          style={styleObj}
          {...dataAttrs}
          disabled={Boolean(props.disabled)}
        >
          {String(props.label ?? props.text ?? 'Button')}
        </button>
      );

    case 'reference': {
      const label = fieldLabel(props);
      const required = Boolean(props.required);
      const inputId = `appbana-ref-${node.id}`;
      const refEntity = String(
        props.referenceEntity ?? props.entity ?? props.entityName ?? ''
      );
      const helpText = String(props.help ?? props.description ?? '');
      return (
        <div key={node.id} className="appbana-field" {...dataAttrs}>
          {label && (
            <label htmlFor={inputId} className="appbana-field-label">
              {label}{required && <span className="appbana-field-required" aria-hidden="true"> *</span>}
            </label>
          )}
          <ReferenceField
            id={inputId}
            name={String(props.name ?? refEntity)}
            refEntity={refEntity}
            required={required}
            defaultValue={String(props.value ?? '')}
            className={className}
            styleObj={styleObj}
            entityAttr={entityAttr}
            fieldAttr={fieldAttr}
          />
          {helpText && <p className="appbana-field-help">{helpText}</p>}
        </div>
      );
    }

    case 'input': {
      const label = fieldLabel(props);
      const required = Boolean(props.required);
      const inputId = `appbana-in-${node.id}`;
      const helpText = String(props.help ?? props.description ?? '');
      return (
        <div key={node.id} className="appbana-field" {...dataAttrs}>
          {label && (
            <label htmlFor={inputId} className="appbana-field-label">
              {label}{required && <span className="appbana-field-required" aria-hidden="true"> *</span>}
            </label>
          )}
          <input
            id={inputId}
            className={`appbana-input ${className}`}
            style={styleObj}
            type={String(props.type ?? props.inputType ?? 'text')}
            placeholder={String(props.placeholder ?? '')}
            defaultValue={String(props.value ?? '')}
            name={String(props.name ?? '')}
            required={required}
            {...entityAttr}
            {...fieldAttr}
          />
          {helpText && <p className="appbana-field-help">{helpText}</p>}
        </div>
      );
    }

    case 'select': {
      const label = fieldLabel(props);
      const required = Boolean(props.required);
      const inputId = `appbana-sel-${node.id}`;
      const helpText = String(props.help ?? props.description ?? '');
      // If a select node references another entity, render a live-loaded ReferenceField.
      const refEntity = String(
        props.referenceEntity ?? props.entityName ?? ''
      );
      if (refEntity) {
        return (
          <div key={node.id} className="appbana-field" {...dataAttrs}>
            {label && (
              <label htmlFor={inputId} className="appbana-field-label">
                {label}{required && <span className="appbana-field-required" aria-hidden="true"> *</span>}
              </label>
            )}
            <ReferenceField
              id={inputId}
              name={String(props.name ?? refEntity)}
              refEntity={refEntity}
              required={required}
              defaultValue={String(props.value ?? '')}
              className={className}
              styleObj={styleObj}
              entityAttr={entityAttr}
              fieldAttr={fieldAttr}
            />
            {helpText && <p className="appbana-field-help">{helpText}</p>}
          </div>
        );
      }
      return (
        <div key={node.id} className="appbana-field" {...dataAttrs}>
          {label && (
            <label htmlFor={inputId} className="appbana-field-label">
              {label}{required && <span className="appbana-field-required" aria-hidden="true"> *</span>}
            </label>
          )}
          <select
            id={inputId}
            className={`appbana-select ${className}`}
            style={styleObj}
            name={String(props.name ?? '')}
            defaultValue={String(props.value ?? '')}
            required={required}
            {...entityAttr}
            {...fieldAttr}
          >
            {((props.options as string[]) ?? []).map((opt: string) => (
              <option key={opt} value={opt}>{opt}</option>
            ))}
          </select>
          {helpText && <p className="appbana-field-help">{helpText}</p>}
        </div>
      );
    }

    case 'textarea': {
      const label = fieldLabel(props);
      const required = Boolean(props.required);
      const inputId = `appbana-ta-${node.id}`;
      const helpText = String(props.help ?? props.description ?? '');
      return (
        <div key={node.id} className="appbana-field appbana-field-full" {...dataAttrs}>
          {label && (
            <label htmlFor={inputId} className="appbana-field-label">
              {label}{required && <span className="appbana-field-required" aria-hidden="true"> *</span>}
            </label>
          )}
          <textarea
            id={inputId}
            className={`appbana-textarea ${className}`}
            style={styleObj}
            name={String(props.name ?? '')}
            placeholder={String(props.placeholder ?? '')}
            defaultValue={String(props.value ?? '')}
            rows={Number(props.rows ?? 4)}
            required={required}
            {...entityAttr}
            {...fieldAttr}
          />
          {helpText && <p className="appbana-field-help">{helpText}</p>}
        </div>
      );
    }

    case 'table':
    case 'grid':
    case 'appbana-table-live':
      return (
        <StudioTableLive
          key={node.id}
          node={node}
          pageId={pageId}
        />
      );

    case 'form':
    case 'studio-form':
      return (
        <EntityForm
          key={node.id}
          className={`appbana-form ${className}`}
          styleObj={styleObj}
          entity={String(props.entity ?? '')}
          dataAttrs={dataAttrs}
        >
          {children}
        </EntityForm>
      );

    case 'img':
      return (
        <img
          key={node.id}
          src={String(props.src ?? '')}
          alt={String(props.alt ?? '')}
          className={className}
          style={styleObj}
          {...dataAttrs}
        />
      );

    case 'a':
      return (
        <a
          key={node.id}
          href={String(props.href ?? '#')}
          className={className}
          style={styleObj}
          {...dataAttrs}
        >
          {String(props.text ?? 'Link')}
        </a>
      );

    case 'container':
    case 'section':
    case 'div':
    default:
      return (
        <div
          key={node.id}
          id={node.id}
          className={className}
          style={styleObj}
          {...dataAttrs}
        >
          {children}
        </div>
      );
  }
}

// ─── Reference-field renderer ────────────────────────────────────────────────

interface RefRow { id?: unknown; name?: unknown; title?: unknown; label?: unknown; [k: string]: unknown }

interface ReferenceFieldProps {
  id: string;
  name: string;
  refEntity: string;
  required: boolean;
  defaultValue: string;
  className: string;
  styleObj: React.CSSProperties;
  entityAttr: Record<string, string | undefined>;
  fieldAttr: Record<string, string | undefined>;
}

function optionLabelFor(row: RefRow): string {
  const candidate = row.name ?? row.title ?? row.label ?? row.email ?? row.code;
  if (candidate != null && String(candidate).trim()) return String(candidate);
  return `#${String(row.id ?? '')}`;
}

function ReferenceField(props: Readonly<ReferenceFieldProps>) {
  const { id, name, refEntity, required, defaultValue, className, styleObj, entityAttr, fieldAttr } = props;
  const [rows, setRows] = useState<RefRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    if (!refEntity) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    fetchEntityRows(qualifyEntityKey(refEntity), getRuntimeToken(), { limit: 500 })
      .then((result) => { if (!cancelled) setRows(result.rows as RefRow[]); })
      .catch((e) => { if (!cancelled) setError(e instanceof Error ? e.message : `Failed to load ${refEntity}`); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [refEntity]);

  if (loading) {
    return (
      <select id={id} className={`appbana-select ${className}`} disabled>
        <option>Loading {refEntity || 'options'}…</option>
      </select>
    );
  }
  if (error) {
    return (
      <div>
        <select id={id} className={`appbana-select ${className}`} disabled>
          <option>— unavailable —</option>
        </select>
        <p className="appbana-field-help" style={{ color: '#b91c1c' }}>{error}</p>
      </div>
    );
  }
  return (
    <select
      id={id}
      name={name}
      className={`appbana-select ${className}`}
      style={styleObj}
      required={required}
      defaultValue={defaultValue}
      {...entityAttr}
      {...fieldAttr}
    >
      <option value="">— Select {refEntity} —</option>
      {rows.map((row) => {
        const val = String(row.id ?? '');
        return <option key={val} value={val}>{optionLabelFor(row)}</option>;
      })}
    </select>
  );
}

// ─── Entity form (Save wiring) ───────────────────────────────────────────────

interface EntityFormProps {
  className: string;
  styleObj: React.CSSProperties;
  entity: string;
  dataAttrs: Record<string, string>;
  children: React.ReactNode;
}

function EntityForm(props: Readonly<EntityFormProps>) {
  const { className, styleObj, entity, dataAttrs, children } = props;
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState(false);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!entity) {
      setError('This form has no entity bound.');
      return;
    }
    const form = e.currentTarget;
    const fd = new FormData(form);
    const payload: Record<string, unknown> = {};
    for (const [k, v] of fd.entries()) {
      if (!k) continue;
      const s = typeof v === 'string' ? v : '';
      payload[k] = s === '' ? null : s;
    }
    setSaving(true);
    setError('');
    setOk(false);
    try {
      const qualified = qualifyEntityKey(entity);
      await insertEntityRow(qualified, payload, getRuntimeToken());
      form.reset();
      setOk(true);
      window.dispatchEvent(
        new CustomEvent('appbana:row-inserted', { detail: { entity: qualified } })
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    } finally {
      setSaving(false);
    }
  }

  return (
    <form
      className={className}
      style={styleObj}
      data-entity={entity}
      onSubmit={handleSubmit}
      {...dataAttrs}
    >
      {children}
      {error && (
        <div className="mx-1 my-2 p-3 bg-red-50 text-red-700 rounded-lg text-sm">
          {error}
        </div>
      )}
      {ok && (
        <div className="mx-1 my-2 p-3 bg-emerald-50 text-emerald-700 rounded-lg text-sm">
          Saved.
        </div>
      )}
      {saving && (
        <div className="mx-1 my-2 text-xs text-gray-500">Saving…</div>
      )}
    </form>
  );
}

