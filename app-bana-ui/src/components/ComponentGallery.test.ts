/** @vitest-environment jsdom */
import { describe, it, expect, beforeEach } from 'vitest';
import '../index';

function mount(path: string) {
  window.history.pushState({}, '', path);
  const el = document.createElement('app-root');
  document.body.appendChild(el);
  return el as HTMLElement & { shadowRoot: ShadowRoot };
}

async function waitForElement(selector: string, host: HTMLElement & { shadowRoot: ShadowRoot }, timeoutMs = 2000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const el = host.shadowRoot && host.shadowRoot.querySelector(selector);
    if (el) return el;
    await new Promise(r=>setTimeout(r,25));
  }
  return null;
}

async function waitForChildInShadow(shadow: ShadowRoot, selector: string, timeoutMs = 2000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const el = shadow.querySelector(selector);
    if (el) return el;
    await new Promise(r=>setTimeout(r,25));
  }
  return null;
}

describe('Component Gallery Page', () => {
  beforeEach(()=>{ document.body.innerHTML=''; });

  it('renders gallery with button and container samples', async () => {
    const root = mount('/gallery');
    await customElements.whenDefined('component-gallery');
    const galleryEl = await waitForElement('component-gallery', root, 2500) as any;
    expect(galleryEl).toBeTruthy();
    // Wait for Lit update cycle (updateComplete promise if available)
    if (galleryEl.updateComplete) await galleryEl.updateComplete;
    // Give a microtask for nested element registration
    await new Promise(r=>setTimeout(r,10));
    const shadow = galleryEl.shadowRoot as ShadowRoot;
    const btn = await waitForChildInShadow(shadow, 'studio-button');
    const cont = await waitForChildInShadow(shadow, 'studio-container');
    expect(btn).toBeTruthy();
    expect(cont).toBeTruthy();
  });
});
