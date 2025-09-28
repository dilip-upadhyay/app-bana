import { describe, it, expect, beforeAll } from 'vitest';
import { renderPage } from './Renderer';
import demoPage from '../../demo/demo-page.json';
import { PageMeta } from '../../models/metadata';
import { registerComponent } from '../../core/registry';
import { ContainerElement } from '../../components/ContainerElement';
import { TextElement } from '../../components/TextElement';
import { ButtonElement } from '../../components/ButtonElement';
import { UnknownElement } from '../../components/UnknownElement';

// Ensure custom elements are registered for tests
beforeAll(() => {
  registerComponent('container', ContainerElement);
  registerComponent('text', TextElement);
  registerComponent('button', ButtonElement);
  registerComponent('unknown', UnknownElement);
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

