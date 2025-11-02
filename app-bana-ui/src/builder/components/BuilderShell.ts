import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { appStore } from '../store/AppStore';
import type { PageMeta } from '../../models/metadata';
import styles from './BuilderShell.css?inline';
import './LivePreview';
import './ComponentLibrary';
import './PageManager';
import './BuilderInspector';
import './AppManager';
import './EntityManager';

@customElement('studio-builder-shell')
export class BuilderShell extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;

  @state() private activeLeftTab: 'components' | 'entities' = 'components';

  connectedCallback(): void {
    super.connectedCallback();
    console.log('[BuilderShell] Initializing...');

    // Check if there's a current app, if not, prompt user to create one
    const currentApp = appStore.getCurrentApp();
    if (currentApp) {
      console.log('[BuilderShell] Current app loaded:', currentApp.name);
    } else {
      console.log('[BuilderShell] No app selected - user needs to create or select an app');
    }

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

      <!-- Left: Tabbed Panel (Component Library or Entity Manager) -->
      <div class="library-panel">
        <div class="left-panel-tabs">
          <button 
            class="tab ${this.activeLeftTab === 'components' ? 'active' : ''}"
            @click=${() => this.activeLeftTab = 'components'}>
            Components
          </button>
          <button 
            class="tab ${this.activeLeftTab === 'entities' ? 'active' : ''}"
            @click=${() => this.activeLeftTab = 'entities'}>
            Entities
          </button>
        </div>
        <div class="left-panel-content">
          ${this.activeLeftTab === 'components' 
            ? html`<studio-component-library></studio-component-library>`
            : html`<studio-entity-manager></studio-entity-manager>`
          }
        </div>
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
