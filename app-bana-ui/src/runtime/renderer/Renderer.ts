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

  // Create a proxy node with interpolated props to pass to render
  const nodeWithData = { ...node, props };

  // HOTFIX: Override legacy hardcoded styles for grid cells in runtime
  if (node.props?.className === 'grid-cell' && nodeWithData.props.style) {
    nodeWithData.props.style = nodeWithData.props.style
      .replace(/padding:\s*[^;]+;?/g, '')
      .replace(/gap:\s*[^;]+;?/g, '')
      .replace(/min-height:\s*[^;]+;?/g, '');
  }

  // Compose children recursively
  const children = (node.children ?? []).map(childId => {
    const child = nodeMap.get(childId);
    return child ? renderNodeTemplate(child, nodeMap, context) : null;
  });

  // Explicit static tag names for each supported type
  if (node.type === 'table' || (node.type === 'grid' && node.props?.entity)) {
    return html`<studio-table-live .node=${nodeWithData}></studio-table-live>`;
  } else if (node.type === 'app-grid' || node.type === 'grid') {
    return html`
       <app-grid
         .rows=${Number(nodeWithData.props.rows || 2)}
         .cols=${Number(nodeWithData.props.cols || 3)}
         .gap=${nodeWithData.props.gap || '1rem'}
         class="${node.style?.classes?.join(' ') || ''}"
         style="${node.props?.style || ''}"
       >
         ${children}
       </app-grid>
     `;
  }

  // Dynamic Registry Lookup
  const tagName = getComponent(node.type) ? getComponentTagName(node.type) : undefined;

  if (tagName) {
    // Correctly use static-html to render dynamic tag with children preserved in the template
    const tag = unsafeStatic(tagName);
    return staticHtml`<${tag} .node=${nodeWithData}>${children}</${tag}>`;
  }

  // Fallback for truly unknown types
  return html`<div class="unknown-component" id="${node.id}">Unknown component: ${node.type}</div>`;
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
      const el = document.createElement('studio-table-live');
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
