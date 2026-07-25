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
import type { PageMeta, ComponentNode } from '@appbana/shared';
import { StudioTableLive } from './StudioTableLive';

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
        <form
          key={node.id}
          className={`appbana-form ${className}`}
          style={styleObj}
          data-entity={String(props.entity ?? '')}
          {...dataAttrs}
        >
          {children}
        </form>
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
