import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { appStore } from '../store/AppStore';
import type { PageMeta } from '../../models/metadata';
import styles from './BuilderShell.css?inline';
import './LivePreview';
import './ComponentLibrary';
import './PageManager';
import './PropertiesPanel';
import './AppManager';
import './EntityManager';
import '../../components/ai-builder/ai-chat-builder';
import '../../workflow-designer/WorkflowDesignerPage';

@customElement('appbana-builder-shell')
export class BuilderShell extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;

  @state() private activeLeftTab = 'ai-builder' as 'components' | 'entities' | 'workflow' | 'ai-builder';
  @state() private leftPanelWidth = 300; // Default width in pixels
  private isResizing = false;
  private startX = 0;
  private startWidth = 0;

  @state() private hasActiveApp = false;
  private unsubscribeAppStore?: () => void;

  connectedCallback(): void {
    super.connectedCallback();
    console.log('[BuilderShell] Initializing...');

    // Subscribe to store changes
    this.unsubscribeAppStore = appStore.onChange(() => {
      this.checkActiveApp();
    });

    // Initial check
    this.checkActiveApp();

    // Load saved panel width from localStorage
    const savedWidth = localStorage.getItem('builder-left-panel-width');
    if (savedWidth) {
      this.leftPanelWidth = parseInt(savedWidth, 10);
    }

    // Load saved active tab from localStorage
    const savedTab = localStorage.getItem('builder-active-tab') as 'components' | 'entities' | 'workflow' | 'ai-builder' | null;
    if (savedTab) {
      this.activeLeftTab = savedTab;
    }

    this.requestUpdate();
  }

  disconnectedCallback(): void {
    super.disconnectedCallback();
    if (this.unsubscribeAppStore) {
      this.unsubscribeAppStore();
    }
  }

  private checkActiveApp() {
    const app = appStore.getCurrentApp();
    this.hasActiveApp = !!app;

    // If we're on workflow tab but lost the active app, switch to components
    if (this.activeLeftTab === 'workflow' && !this.hasActiveApp) {
      this.activeLeftTab = 'components';
    }

    this.requestUpdate();
  }

  private handleResizeStart = (e: MouseEvent) => {
    this.isResizing = true;
    this.startX = e.clientX;
    this.startWidth = this.leftPanelWidth;

    // Prevent text selection during drag
    e.preventDefault();
    e.stopPropagation();

    // Add global mouse move and mouse up listeners
    document.addEventListener('mousemove', this.handleResizeMove);
    document.addEventListener('mouseup', this.handleResizeEnd);

    // Add cursor style to body during resize
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
  }

  private readonly handleResizeMove = (e: MouseEvent) => {
    if (!this.isResizing) return;

    e.preventDefault();

    const delta = e.clientX - this.startX;
    const newWidth = this.startWidth + delta;

    // Constrain width between 200px and 800px
    if (newWidth >= 200 && newWidth <= 800) {
      this.leftPanelWidth = newWidth;
      this.requestUpdate();
    }
  }

  private readonly handleResizeEnd = () => {
    if (this.isResizing) {
      this.isResizing = false;

      // Restore cursor and user-select
      document.body.style.cursor = '';
      document.body.style.userSelect = '';

      // Save to localStorage
      localStorage.setItem('builder-left-panel-width', this.leftPanelWidth.toString());

      // Remove global listeners
      document.removeEventListener('mousemove', this.handleResizeMove);
      document.removeEventListener('mouseup', this.handleResizeEnd);
    }
  }

  private handleTabChange(tab: 'components' | 'entities' | 'workflow' | 'ai-builder') {
    this.activeLeftTab = tab;
    localStorage.setItem('builder-active-tab', tab);
  }

  private renderLeftPanelContent() {
    if (this.activeLeftTab === 'components') {
      return html`<appbana-component-library></appbana-component-library>`;
    }
    if (this.activeLeftTab === 'entities') {
      return html`<appbana-entity-manager></appbana-entity-manager>`;
    }
    if (this.activeLeftTab === 'ai-builder') {
      return html`<ai-chat-builder></ai-chat-builder>`;
    }
    if (this.activeLeftTab === 'workflow') {
      if (!this.hasActiveApp) {
        return html`
          <div style="padding: 24px; text-align: center; color: #64748b;">
            <p>Please select or create an app to design workflows.</p>
          </div>
        `;
      }
      return html`<workflow-canvas .metadata=${{ nodes: [], connections: [] }}></workflow-canvas>`;
    }
    // Default: Components tab
    return html`<appbana-component-library></appbana-component-library>`;
  }

  render() {
    // Set CSS custom property for dynamic width
    this.style.setProperty('--left-panel-width', `${this.leftPanelWidth}px`);

    // Full-page mode for Workflow Designer
    if (this.activeLeftTab === 'workflow') {
      this.setAttribute('data-workflow-mode', 'true');

      if (!this.hasActiveApp) {
        return html`
          <div class="app-manager-panel">
            <appbana-app-manager></appbana-app-manager>
          </div>
          <div style="flex: 1; display: flex; align-items: center; justify-content: center; background: #f8fafc; color: #64748b; flex-direction: column; gap: 16px;">
            <div style="font-size: 48px;">⚡</div>
            <h2 style="margin: 0; font-weight: 600; color: #1e293b;">Workflow Designer</h2>
            <p style="margin: 0;">Select an app from the header to start building workflows.</p>
          </div>
        `;
      }

      return html`
        <div class="app-manager-panel">
          <appbana-app-manager></appbana-app-manager>
        </div>
        <workflow-designer-page .appId=${appStore.getCurrentApp()?.id}></workflow-designer-page>
      `;
    }

    this.removeAttribute('data-workflow-mode');

    return html`
      <!-- Top: App Manager -->
      <div class="app-manager-panel">
        <appbana-app-manager></appbana-app-manager>
      </div>

      <!-- Second Row: Page Manager (only show when app is active) -->
      ${this.hasActiveApp ? html`
        <div class="page-manager-panel">
          <appbana-page-manager></appbana-page-manager>
        </div>
      ` : ''}

      <!-- Main Content Grid -->
      <div class="content-grid">
        <!-- Left: Tabbed Panel (Component Library or Entity Manager or AI Builder) -->
        <div class="library-panel">
          <div class="left-panel-tabs">
            <button 
              class="tab ${this.activeLeftTab === 'components' ? 'active' : ''}"
              @click=${() => this.handleTabChange('components')}>
              Components
            </button>
            <button 
              class="tab ${this.activeLeftTab === 'entities' ? 'active' : ''}"
              @click=${() => this.handleTabChange('entities')}>
              Entities
            </button>
            <button 
              class="tab ${this.activeLeftTab === 'ai-builder' ? 'active' : ''}"
              @click=${() => this.handleTabChange('ai-builder')}>
              🤖 AI Agent
            </button>
            <button 
              class="tab ${(this.activeLeftTab as any) === 'workflow' ? 'active' : ''}"
              @click=${() => this.handleTabChange('workflow')}>
              ⚡ Workflow
            </button>
          </div>
          <div class="left-panel-content">
            ${this.renderLeftPanelContent()}
          </div>
          <!-- Resize Handle -->
          <div 
            class="resize-handle"
            @mousedown=${this.handleResizeStart}
            title="Drag to resize panel"
          ></div>
        </div>

        <!-- Center: Live Preview (WYSIWYG Canvas) -->
        <div class="center-panel">
          <appbana-live-preview></appbana-live-preview>
        </div>

        <!-- Right: Properties Panel -->
        <div class="right-panel">
          <appbana-properties-panel></appbana-properties-panel>
        </div>
      </div>
    `;
  }
}
