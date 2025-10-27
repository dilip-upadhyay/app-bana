import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement } from 'lit/decorators.js';
import demoPage from '../../demo/demo-page.json';
import { initStore, currentStore } from '../store/TreeStore';
import type { PageMeta } from '../../models/metadata';
import styles from './BuilderShell.css?inline';
import './TokenPanel';
import './LivePreview';
import './ComponentLibrary';
import './PageManager';

@customElement('studio-builder-shell')
export class BuilderShell extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;

  connectedCallback(): void {
    super.connectedCallback();
    if (!currentStore) initStore(demoPage as PageMeta);
  }

  render() {
    return html`
      <!-- Top: Page Manager -->
      <div class="page-manager-panel">
        <studio-page-manager></studio-page-manager>
      </div>

      <!-- Far Left: Component Library -->
      <div class="library-panel">
        <studio-component-library></studio-component-library>
      </div>

      <!-- Left: Component Tree + Token Panel -->
      <div class="left-panel">
        <studio-builder-canvas></studio-builder-canvas>
        <studio-token-panel></studio-token-panel>
      </div>

      <!-- Center: Live Preview (WYSIWYG Canvas) -->
      <div class="center-panel">
        <studio-live-preview></studio-live-preview>
      </div>

      <!-- Right: Property Inspector -->
      <div class="right-panel">
        <studio-builder-inspector></studio-builder-inspector>
      </div>
    `;
  }
}
