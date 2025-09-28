import { describe, it, expect, beforeAll } from 'vitest';
import { renderPage } from './Renderer';
import { ensureCoreRegistered } from '../../core/registry';
import { PageMeta } from '../../models/metadata';

beforeAll(async () => { await ensureCoreRegistered(); });

describe('Unknown component fallback', () => {
  it('renders studio-unknown with original type name', () => {
    const page: PageMeta = {
      id: 'p1', name: 'Test', path: '/x', rootId: 'root', nodes: [
        { id: 'root', type: 'container', children: ['c1'] },
        { id: 'c1', type: 'notRegisteredWidget', props: { foo: 1 } }
      ]
    };
    const host = document.createElement('div');
    renderPage(page, host);
    const unk = host.querySelector('studio-unknown');
    expect(unk).toBeTruthy();
    expect(unk?.getAttribute('data-type')).toBe('notRegisteredWidget');
    // Check shadow DOM text content reflects the original type name
    const shadowText = unk?.shadowRoot?.textContent || '';
    expect(shadowText).toMatch(/notRegisteredWidget/);
  });
});
