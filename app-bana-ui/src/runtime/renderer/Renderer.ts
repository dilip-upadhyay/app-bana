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
  if (!root) throw new Error('Root node not found');
  container.appendChild(renderNode(root, nodeMap));
}

function renderNode(node: ComponentNode, nodeMap: Map<string, ComponentNode>): HTMLElement {
  const ctor = getComponent(node.type);
  if (!ctor) {
    const fallback = document.createElement('div');
    fallback.textContent = `Unknown component: ${node.type}`;
    return fallback;
  }
  const el = new ctor();
  // Assign props
  if (node.props) Object.assign(el, node.props);
  // Render children
  if (node.children) {
    for (const childId of node.children) {
      const child = nodeMap.get(childId);
      if (child) el.appendChild(renderNode(child, nodeMap));
    }
  }
  // Style (optional)
  if (node.style && node.style.classes) el.classList.add(...node.style.classes);
  return el;
}

