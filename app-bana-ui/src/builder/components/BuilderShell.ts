import { LitElement, html, css } from 'lit';
import { customElement } from 'lit/decorators.js';
import demoPage from '../../demo/demo-page.json';
import { initStore, currentStore } from '../store/TreeStore';
import type { PageMeta } from '../../models/metadata';
import './TokenPanel';

@customElement('studio-builder-shell')
export class BuilderShell extends LitElement {
  static styles = css`
    :host { display:flex; gap:12px; align-items:stretch; }
    studio-builder-canvas { flex: 2 1 0; }
    studio-builder-inspector { flex: 1 1 260px; max-width:320px; }
    .panel { display:flex; flex-direction:column; }
  `;

  connectedCallback(): void {
    super.connectedCallback();
    if (!currentStore) initStore(demoPage as PageMeta);
  }

  render() {
    return html`
      <div class="panel" style="flex:2 1 0; display:flex; flex-direction:column; gap:12px;">
        <studio-builder-canvas></studio-builder-canvas>
        <studio-token-panel></studio-token-panel>
      </div>
      <studio-builder-inspector></studio-builder-inspector>
    `;
  }
}
