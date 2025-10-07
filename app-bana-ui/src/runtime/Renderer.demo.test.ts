/** @vitest-environment jsdom */
import { describe, it, expect, beforeAll } from 'vitest';
import { renderPage } from '../app-renderer';
import page from '../demo/demo-page.json';

// Basic smoke test for Phase A renderer.
describe('Studio Renderer Demo Page', () => {
  let host: HTMLElement;
  beforeAll(() => {
    host = document.createElement('div');
    document.body.appendChild(host);
  });

  it('renders root container, text content, button, and unknown placeholder', async () => {
    await renderPage(page as any, host);
    const container = host.querySelector('studio-container');
    expect(container).toBeTruthy();
    const textEl = host.querySelector('studio-text');
    expect(textEl?.textContent).toContain('Hello from metadata-driven Studio');
    const buttonEl = host.querySelector('studio-button');
    expect(buttonEl).toBeTruthy();
    const unknown = host.querySelector('studio-unknown[data-type="fancyChart"]');
    expect(unknown).toBeTruthy();
    // Ensure hierarchy: root contains three direct children matching demo JSON order
    const rootChildrenIds = Array.from(container?.children || []).map(c => c.getAttribute('id'));
    expect(rootChildrenIds.length).toBe(3);
  });
});

