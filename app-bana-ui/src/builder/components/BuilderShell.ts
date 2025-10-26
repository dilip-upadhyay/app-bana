import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement } from 'lit/decorators.js';
import demoPage from '../../demo/demo-page.json';
import { initStore, currentStore } from '../store/TreeStore';
import type { PageMeta } from '../../models/metadata';
import styles from './BuilderShell.css?inline';
import './TokenPanel';

@customElement('studio-builder-shell')
export class BuilderShell extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;

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
