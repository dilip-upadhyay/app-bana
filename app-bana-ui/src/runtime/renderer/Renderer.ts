import { PageMeta, ComponentNode } from '../../models/metadata';
import { getComponent, getComponentTagName } from '../../core/registry';
import { html, TemplateResult } from 'lit';
import { html as staticHtml, unsafeStatic } from 'lit/static-html.js';
import { ifDefined } from 'lit/directives/if-defined.js';
import './StudioTableLive';
import '../../components/GridElement';

// Helper for simple handle-bars style interpolation
// e.g. interpolate("Hello {{user.name}}", { user: { name: "World" } }) -> "Hello World"
function interpolate(text: string, context: any): string {
  if (!text || typeof text !== 'string') return text;
  return text.replace(/\{\{([^}]+)\}\}/g, (_, path) => {
    const keys = path.trim().split('.');
    let value = context;
    for (const key of keys) {
      if (value === undefined || value === null) return '';
      value = value[key];
    }
    return value !== undefined ? String(value) : '';
  });
}

/**
 * Returns a Lit html template for a PageMeta tree using the component registry.
 * Used for Lit-based reactive rendering.
 */
export function renderPageTemplate(page: PageMeta, context: any = {}): TemplateResult {
  const nodeMap = new Map(page.nodes.map(n => [n.id, n]));
  const root = nodeMap.get(page.rootId);
  if (!root) return html`<div class="error">Root node not found: ${page.rootId}</div>`;
  return renderNodeTemplate(root, nodeMap, context);
}

function renderNodeTemplate(node: ComponentNode, nodeMap: Map<string, ComponentNode>, context: any): TemplateResult {
  // Interpolate props
  const props = { ...node.props };
  for (const [key, value] of Object.entries(props)) {
    if (typeof value === 'string') {
      props[key] = interpolate(value, context);
    }
  }

  const style = props?.style || '';
  const className = props?.className || '';

  // Compose children recursively
  const children = (node.children ?? []).map(childId => {
    const child = nodeMap.get(childId);
    return child ? renderNodeTemplate(child, nodeMap, context) : null;
  });

  // Handle each component type with proper attribute binding (like LivePreview)
  switch (node.type) {
    case 'text':
      return html`
        <appbana-text
          class="${className}"
          style="${style}"
          content="${props?.content || props?.text || ''}"
          variant="${props?.variant || 'body'}"
          align="${props?.align || 'left'}"
          color="${props?.color || ''}"
          tag="${props?.tag || 'p'}"
        ></appbana-text>
      `;

    case 'button':
      return html`
        <appbana-button
          class="${className}"
          style="${style}"
          label="${props?.label || props?.text || 'Button'}"
          type="${props?.type || 'button'}"
          variant="${props?.variant || 'primary'}"
          ?disabled=${props?.disabled}
        ></appbana-button>
      `;

    case 'input':
      return html`
        <appbana-input
          class="${className}"
          style="${style}"
          label="${props?.label || ''}"
          type="${props?.type || 'text'}"
          placeholder="${props?.placeholder || ''}"
          value="${props?.value || ''}"
          name="${props?.name || ''}"
          ?required=${props?.required}
        ></appbana-input>
      `;

    case 'select':
      return html`
        <appbana-select
          class="${className}"
          style="${style}"
          label="${props?.label || ''}"
          name="${props?.name || ''}"
          value="${props?.value || ''}"
          options="${typeof props?.options === 'string' ? props.options : JSON.stringify(props?.options || [])}"
          placeholder="${props?.placeholder || ''}"
          ?required=${props?.required}
        ></appbana-select>
      `;

    case 'textarea':
      return html`
        <appbana-textarea
          class="${className}"
          style="${style}"
          label="${props?.label || ''}"
          placeholder="${props?.placeholder || ''}"
          value="${props?.value || ''}"
          name="${props?.name || ''}"
          rows="${props?.rows || 4}"
          ?required=${props?.required}
        ></appbana-textarea>
      `;

    case 'checkbox':
      return html`
        <appbana-checkbox
          class="${className}"
          style="${style}"
          label="${props?.label || ''}"
          name="${props?.name || ''}"
          value="${props?.value || 'on'}"
          ?checked=${props?.checked}
          ?disabled=${props?.disabled}
        ></appbana-checkbox>
      `;

    case 'radio-group':
      return html`
        <appbana-radio-group
          class="${className}"
          style="${style}"
          label="${props?.label || ''}"
          name="${props?.name || ''}"
          value="${props?.value || ''}"
          options="${typeof props?.options === 'string' ? props.options : JSON.stringify(props?.options || [])}"
          layout="${props?.layout || 'vertical'}"
          ?required=${props?.required}
          ?disabled=${props?.disabled}
        ></appbana-radio-group>
      `;

    case 'table':
    case 'grid':
    case 'appbana-table-live':
      return html`
        <appbana-table-live
          class="${className}"
          style="${style}"
          .node=${node}
          entity="${props?.entity || ''}"
          view-mode="${props?.viewMode || 'dynamic'}"
          theme="${props?.theme || 'default'}"
        ></appbana-table-live>
      `;

    case 'app-grid':
      return html`
        <app-grid
          class="${className}"
          style="${style}"
          cols="${props?.cols || 2}"
          rows="${props?.rows || 2}"
          gap="${props?.gap || '16px'}"
          .minCellHeight="${props?.minCellHeight || 'auto'}"
        >
          ${children}
        </app-grid>
      `;

    case 'form':
    case 'studio-form':
      return html`
        <studio-form
          class="${className}"
          style="${style}"
          entity="${props?.entity || ''}"
          record-id="${props?.['record-id'] || ''}"
        >
          ${children}
        </studio-form>
      `;

    case 'img':
      return html`
        <img
          src="${props?.src || ''}"
          alt="${props?.alt || ''}"
          class="${className}"
          style="${style}"
        />
      `;

    case 'a':
      return html`
        <a
          href="${props?.href || '#'}"
          class="${className}"
          style="${style}"
        >
          ${props?.text || 'Link'}
        </a>
      `;

    case 'container':
    case 'section':
    case 'div':
    default:
      // For containers and unknown types, just render a div with children
      return html`
        <div
          id="${node.id}"
          class="${className}"
          style="${style}"
        >
          ${children}
        </div>
      `;
  }
}

// Helper wrapper to avoid needing unsafeStatic in the main file if imports are tricky?
// No, simpler to just fix imports.


/**
 * Renders a PageMeta tree to DOM using the component registry.
 * @param page PageMeta object
 * @param container DOM element to render into
 * @param context context data for interpolation
 */
export function renderPage(page: PageMeta, container: HTMLElement, context: any = {}) {
  container.innerHTML = '';
  const nodeMap = new Map(page.nodes.map(n => [n.id, n]));
  const root = nodeMap.get(page.rootId);
  if (!root) throw new Error('Root node not found: ' + page.rootId);
  container.appendChild(renderNode(root, nodeMap, context));
}

function renderNode(node: ComponentNode, nodeMap: Map<string, ComponentNode>, context: any): HTMLElement {
  // Interpolate props
  const props = { ...node.props };
  for (const [key, value] of Object.entries(props)) {
    if (typeof value === 'string') {
      props[key] = interpolate(value, context);
    }
  }

  // Use interpolated props
  const nodeWithData = { ...node, props };

  if (node.type === 'table' || (node.type === 'grid' && node.props?.entity)) {
    // Runtime rendering: live data if in runtime, else preview
    if (window.location.pathname.includes('preview') || window.location.pathname.includes('runtime')) {
      // Use Lit component for robust async rendering
      const el = document.createElement('appbana-table-live');
      (el as any).node = nodeWithData;
      return el;
    } else {
      return requireTablePreview(nodeWithData);
    }
  }

  if (node.type === 'app-grid' || node.type === 'grid') {
    const el = document.createElement('app-grid');
    el.setAttribute('rows', String(node.props?.rows || 2));
    el.setAttribute('cols', String(node.props?.cols || 3));
    el.setAttribute('gap', String(node.props?.gap || '1rem'));
    el.setAttribute('id', node.id);
    el.setAttribute('data-component-id', node.id);

    if (node.style?.classes) el.classList.add(...node.style.classes);
    if (node.props?.style) el.setAttribute('style', node.props.style);

    if (node.children) {
      for (const childId of node.children) {
        const child = nodeMap.get(childId);
        if (child) el.appendChild(renderNode(child, nodeMap, context));
      }
    }
    return el;
  }

  let ctor = getComponent(node.type);
  if (!ctor) {
    const unknownCtor = getComponent('unknown');
    if (unknownCtor) {
      const unk = new unknownCtor();
      (unk as HTMLElement).setAttribute('data-type', node.type);
      (unk as HTMLElement).setAttribute('id', node.id);
      (unk as HTMLElement).setAttribute('data-component-id', node.id); // For live preview selection
      return unk as HTMLElement;
    }
    const fallback = document.createElement('div');
    fallback.textContent = `Unknown component: ${node.type}`;
    fallback.setAttribute('id', node.id);
    fallback.setAttribute('data-component-id', node.id); // For live preview selection
    return fallback;
  }
  const el = new ctor();
  (el as HTMLElement).setAttribute('id', node.id);
  (el as HTMLElement).setAttribute('data-component-id', node.id); // For live preview selection

  if (props) {
    // Assign interpolated props
    for (const [k, v] of Object.entries(props)) {
      if (v == null) continue;
      if (typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean') {
        (el as HTMLElement).setAttribute(k, String(v));
      } else {
        (el as any)[k] = v;
      }
    }
  }
  if (node.children) {
    for (const childId of node.children) {
      const child = nodeMap.get(childId);
      if (child) el.appendChild(renderNode(child, nodeMap, context));
    }
  }
  if (node.style?.classes) (el as HTMLElement).classList.add(...node.style.classes);
  return el as HTMLElement;
}

// ES module import workaround for browser
import { renderTablePreview } from './TablePreview';
function requireTablePreview(node: ComponentNode): HTMLElement {
  return renderTablePreview(node);
}
