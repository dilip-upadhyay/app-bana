// src/runtime/renderer/Renderer.ts
import { PageMeta, ComponentNode } from '../../models/metadata';
import { getComponent } from '../../core/registry';
import { html, TemplateResult } from 'lit';
// Ensure table live component is registered for runtime rendering
import './StudioTableLive';
/**
 * Returns a Lit html template for a PageMeta tree using the component registry.
 * Used for Lit-based reactive rendering.
 */
export function renderPageTemplate(page: PageMeta): TemplateResult {
  const nodeMap = new Map(page.nodes.map(n => [n.id, n]));
  const root = nodeMap.get(page.rootId);
  if (!root) return html`<div class="error">Root node not found: ${page.rootId}</div>`;
  return renderNodeTemplate(root, nodeMap);
}

function renderNodeTemplate(node: ComponentNode, nodeMap: Map<string, ComponentNode>): TemplateResult {
  // Compose children recursively
  const children = (node.children ?? []).map(childId => {
    const child = nodeMap.get(childId);
    return child ? renderNodeTemplate(child, nodeMap) : null;
  });

  // Explicit static tag names for each supported type
  if (node.type === 'table') {
    return html`<studio-table-live .node=${node}></studio-table-live>`;
  } else if (node.type === 'container') {
    return html`<studio-container .node=${node}>${children}</studio-container>`;
  } else if (node.type === 'text') {
    return html`<studio-text .node=${node}></studio-text>`;
  } else if (node.type === 'button') {
    return html`<studio-button .node=${node}></studio-button>`;
  } else if (node.type === 'form') {
    return html`<studio-form .node=${node}>${children}</studio-form>`;
  } else if (node.type === 'header') {
    return html`<studio-header .node=${node}></studio-header>`;
  } else if (node.type === 'list') {
    return html`<studio-list .node=${node}>${children}</studio-list>`;
  } else if (node.type === 'card') {
    return html`<studio-card .node=${node}>${children}</studio-card>`;
  } else if (node.type === 'detail') {
    return html`<studio-detail .node=${node}>${children}</studio-detail>`;
  } else if (node.type === 'dashboard') {
    return html`<studio-dashboard .node=${node}>${children}</studio-dashboard>`;
  } else if (node.type === 'unknown') {
    return html`<studio-unknown .node=${node}></studio-unknown>`;
  }
  // Fallback for truly unknown types
  return html`<div class="unknown-component" id="${node.id}">Unknown component: ${node.type}</div>`;
}

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
    for (const [k, v] of Object.entries(node.props)) {
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
