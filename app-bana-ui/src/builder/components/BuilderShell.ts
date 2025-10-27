import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement } from 'lit/decorators.js';
import demoPage from '../../demo/demo-page.json';
import { initStore, currentStore } from '../store/TreeStore';
import type { PageMeta } from '../../models/metadata';
import styles from './BuilderShell.css?inline';
import './LivePreview';
import './ComponentLibrary';
import './PageManager';
import './BuilderInspector';
import './AppManager';

@customElement('studio-builder-shell')
export class BuilderShell extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;

  connectedCallback(): void {
    super.connectedCallback();
    console.log('[BuilderShell] connectedCallback - currentStore before init:', currentStore);
    console.log('[BuilderShell] demoPage data:', demoPage);
    if (!currentStore) {
      initStore(demoPage as PageMeta);
      console.log('[BuilderShell] Store initialized, currentStore:', currentStore);
    }
    // Force children to re-check the store
    this.requestUpdate();
  }

  render() {
    return html`
      <!-- Top: App Manager -->
      <div class="app-manager-panel">
        <studio-app-manager></studio-app-manager>
      </div>

      <!-- Second Row: Page Manager -->
      <div class="page-manager-panel">
        <studio-page-manager></studio-page-manager>
      </div>

      <!-- Left: Component Library -->
      <div class="library-panel">
        <studio-component-library></studio-component-library>
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
