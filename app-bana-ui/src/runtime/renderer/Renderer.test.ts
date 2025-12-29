import { describe, it, expect, beforeAll } from 'vitest';
import { renderPage } from '../../app-renderer';
import demoPage from '../../demo/demo-page.json';
import { PageMeta } from '../../models/metadata';
import { ensureCoreRegistered } from '../../core/registry';

beforeAll(async () => {
  await ensureCoreRegistered();
});

describe('Renderer', () => {
  it('renders demo page metadata into DOM', () => {
    const host = document.createElement('div');
    renderPage(demoPage as PageMeta, host);
    // root container should exist
    const container = host.querySelector('studio-container');
    expect(container).toBeTruthy();
    // text element
    const text = host.querySelector('studio-text');
    expect(text).toBeTruthy();
    expect(text?.shadowRoot?.textContent).toContain('Hello');
    // button element
    const button = host.querySelector('studio-button');
    expect(button).toBeTruthy();
    // unknown component placeholder
    const unknown = host.querySelector('studio-unknown');
    expect(unknown).toBeTruthy();
  });
});
