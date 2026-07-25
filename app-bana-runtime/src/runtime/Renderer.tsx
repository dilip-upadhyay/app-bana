/**
 * Renderer.tsx — React port of app-bana-ui/src/runtime/renderer/Renderer.ts
 *
 * Converts a PageMeta node tree into JSX, emitting data-appbana-* attrs on
 * every element so Stage 6 (select-and-instruct) can identify them.
 */
import type { PageMeta, ComponentNode } from '@appbana/shared';
import { StudioTableLive } from './StudioTableLive';

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

    case 'input':
      return (
        <input
          key={node.id}
          className={`appbana-input ${className}`}
          style={styleObj}
          type={String(props.type ?? props.inputType ?? 'text')}
          placeholder={String(props.placeholder ?? '')}
          defaultValue={String(props.value ?? '')}
          name={String(props.name ?? '')}
          required={Boolean(props.required)}
          {...dataAttrs}
          {...entityAttr}
          {...fieldAttr}
        />
      );

    case 'select':
      return (
        <select
          key={node.id}
          className={`appbana-select ${className}`}
          style={styleObj}
          name={String(props.name ?? '')}
          defaultValue={String(props.value ?? '')}
          required={Boolean(props.required)}
          {...dataAttrs}
          {...entityAttr}
          {...fieldAttr}
        >
          {((props.options as string[]) ?? []).map((opt: string) => (
            <option key={opt} value={opt}>{opt}</option>
          ))}
        </select>
      );

    case 'textarea':
      return (
        <textarea
          key={node.id}
          className={`appbana-textarea ${className}`}
          style={styleObj}
          name={String(props.name ?? '')}
          placeholder={String(props.placeholder ?? '')}
          defaultValue={String(props.value ?? '')}
          rows={Number(props.rows ?? 4)}
          required={Boolean(props.required)}
          {...dataAttrs}
          {...entityAttr}
          {...fieldAttr}
        />
      );

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
          className={className}
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
