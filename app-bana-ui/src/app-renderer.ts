import { ensureCoreRegistered, getComponent } from './core/registry';
import type { PageMeta, ComponentNode } from './models/metadata';

/**
 * Render a PageMeta tree into a host container element.
 * Minimal Phase A implementation: static props -> attributes, children depth-first.
 */
export async function renderPage(page: PageMeta, host: HTMLElement): Promise<void> {
  await ensureCoreRegistered();
  host.innerHTML = ''; // clean slate
  const nodeMap = new Map<string, ComponentNode>();
  page.nodes.forEach(n => nodeMap.set(n.id, n));

  function createElement(node: ComponentNode): HTMLElement {
    const ctor = getComponent(node.type) || getComponent('unknown');
    const isUnknown = !getComponent(node.type);
    // Fallback: if still missing (shouldn't), create a div
    let el: any;
    try {
      el = ctor ? new (ctor as any)() : document.createElement('div');
    } catch {
      el = document.createElement('div');
    }
    // Apply primitive props as attributes (heuristic)
    if (node.props) {
      for (const [k, v] of Object.entries(node.props)) {
        if (v == null) continue;
        const primitive = ['string','number','boolean'].includes(typeof v);
        if (primitive) {
            el.setAttribute(k, String(v));
        } else {
            // store JSON for future runtime to hydrate
            el.setAttribute(`data-prop-${k}`, JSON.stringify(v));
        }
      }
    }
    if (isUnknown) {
      el.setAttribute('data-type', node.type);
    }
    // Recurse children
    if (node.children) {
      for (const childId of node.children) {
        const child = nodeMap.get(childId);
        if (child) el.appendChild(createElement(child));
      }
    }
    return el;
  }

  const rootNode = nodeMap.get(page.rootId);
  if (!rootNode) {
    const warn = document.createElement('div');
    warn.textContent = `Studio renderer: root node '${page.rootId}' not found.`;
    host.appendChild(warn);
    return;
  }
  host.appendChild(createElement(rootNode));
}

// Convenience boot function for entrypoint scripts.
export async function renderDemoIfPresent() {
  const el = document.getElementById('studio-root');
  if (!el) return;
  try {
    const page: PageMeta = (await import('./demo/demo-page.json')).default as any;
    await renderPage(page, el);
  } catch (e) {
    el.innerHTML = `<pre style="color:red">Failed to load demo page: ${(e as Error).message}</pre>`;
  }
}

// If this module is loaded directly from studio-entry it will be invoked there; not auto-run here to allow test control.
