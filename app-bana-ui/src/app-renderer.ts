import { ensureCoreRegistered } from './core/registry';
import type { PageMeta } from './models/metadata';
import { renderPageTemplate } from './runtime/renderer/Renderer';
import { render } from 'lit';

// Thin facade kept for backward compatibility (tests or legacy imports)
export async function renderPage(page: PageMeta, host: HTMLElement): Promise<void> {
  await ensureCoreRegistered();
  const rendered = renderPageTemplate(page, {});
  render(rendered, host);
}

// Convenience boot function for studio-entry
export async function renderDemoIfPresent() {
  const el = document.getElementById('studio-root');
  if (!el) return;
  try {
    await ensureCoreRegistered();
    const page: PageMeta = (await import('./demo/demo-page.json')).default as any;
    const rendered = renderPageTemplate(page, {});
    render(rendered, el);
  } catch (e) {
    el.innerHTML = `<pre style="color:red">Failed to load demo page: ${(e as Error).message}</pre>`;
  }
}
