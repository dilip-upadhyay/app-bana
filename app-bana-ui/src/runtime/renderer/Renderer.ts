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
    return html`<my-container .node=${node}>${children}</my-container>`;
  } else if (node.type === 'text') {
    return html`<my-text .node=${node}></my-text>`;
  } else if (node.type === 'button') {
    return html`<my-button .node=${node}></my-button>`;
  } else if (node.type === 'form') {
    return html`<my-form .node=${node}>${children}</my-form>`;
  } else if (node.type === 'header') {
    return html`<my-header .node=${node}></my-header>`;
  } else if (node.type === 'list') {
    return html`<my-list .node=${node}>${children}</my-list>`;
  } else if (node.type === 'card') {
    return html`<my-card .node=${node}>${children}</my-card>`;
  } else if (node.type === 'detail') {
    return html`<my-detail .node=${node}>${children}</my-detail>`;
  } else if (node.type === 'dashboard') {
    return html`<my-dashboard .node=${node}>${children}</my-dashboard>`;
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
