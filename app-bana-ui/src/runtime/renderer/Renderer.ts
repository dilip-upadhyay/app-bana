// src/runtime/renderer/Renderer.ts
import { PageMeta, ComponentNode } from '../../models/metadata';
import { getComponent } from '../../core/registry';

/**
 * Renders a PageMeta tree to DOM using the component registry.
 * @param page PageMeta object
 * @param container DOM element to render into
 */
export function renderPage(page: PageMeta, container: HTMLElement) {
  container.innerHTML = '';
  const nodeMap = new Map(page.nodes.map(n => [n.id, n]));
  const root = nodeMap.get(page.rootId);
  if (!root) throw new Error('Root node not found: ' + page.rootId);
  container.appendChild(renderNode(root, nodeMap));
}

function renderNode(node: ComponentNode, nodeMap: Map<string, ComponentNode>): HTMLElement {
  if (node.type === 'table') {
    // Runtime rendering: live data if in runtime, else preview
    if (window.location.pathname.includes('preview') || window.location.pathname.includes('runtime')) {
      // Use Lit component for robust async rendering
      const el = document.createElement('studio-table-live');
      (el as any).node = node;
      return el;
    } else {
      // Minimal preview
      return requireTablePreview(node);
    }
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
  if (node.props) {
    // Assign props as attributes when they are simple scalars, else direct property
    for (const [k,v] of Object.entries(node.props)) {
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
      if (child) el.appendChild(renderNode(child, nodeMap));
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
