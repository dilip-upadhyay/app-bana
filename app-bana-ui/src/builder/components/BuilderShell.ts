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
import './AiChatBuilder';
import '../../workflow-designer/WorkflowDesignerPage';

@customElement('studio-builder-shell')
export class BuilderShell extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;

  @state() private activeLeftTab = 'components' as 'components' | 'entities' | 'ai-builder' | 'workflow';
  @state() private leftPanelWidth = 300; // Default width in pixels
  private isResizing = false;
  private startX = 0;
  private startWidth = 0;

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

    // Load saved panel width from localStorage
    const savedWidth = localStorage.getItem('builder-left-panel-width');
    if (savedWidth) {
      this.leftPanelWidth = parseInt(savedWidth, 10);
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

  private renderLeftPanelContent() {
    if (this.activeLeftTab === 'components') {
      return html`<studio-component-library></studio-component-library>`;
    }
    if (this.activeLeftTab === 'entities') {
      return html`<studio-entity-manager></studio-entity-manager>`;
    }
    if (this.activeLeftTab === 'workflow') {
      return html`<workflow-canvas .metadata=${{ nodes: [], connections: [] }}></workflow-canvas>`;
    }
    return html`<ai-chat-builder></ai-chat-builder>`;
  }

  render() {
    // Set CSS custom property for dynamic width
    this.style.setProperty('--left-panel-width', `${this.leftPanelWidth}px`);

    // Full-page mode for Workflow Designer
    if (this.activeLeftTab === 'workflow') {
      this.setAttribute('data-workflow-mode', 'true');
      return html`
        <div class="app-manager-panel">
          <studio-app-manager></studio-app-manager>
        </div>
        <workflow-designer-page></workflow-designer-page>
      `;
    }

    this.removeAttribute('data-workflow-mode');

    return html`
      <!-- Top: App Manager -->
      <div class="app-manager-panel">
        <studio-app-manager></studio-app-manager>
      </div>

      <!-- Second Row: Page Manager -->
      <div class="page-manager-panel">
        <studio-page-manager></studio-page-manager>
      </div>

      <!-- Left: Tabbed Panel (Component Library or Entity Manager or AI Builder) -->
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
          <button 
            class="tab ${(this.activeLeftTab as any) === 'workflow' ? 'active' : ''}"
            @click=${() => this.activeLeftTab = 'workflow'}>
            ⚡ Workflow
          </button>
          <button 
            class="tab ${this.activeLeftTab === 'ai-builder' ? 'active' : ''}"
            @click=${() => this.activeLeftTab = 'ai-builder'}>
            🤖 AI Builder
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
        <studio-live-preview></studio-live-preview>
      </div>

      <!-- Right: Properties Panel -->
      <div class="right-panel">
        <studio-properties-panel></studio-properties-panel>
      </div>
    `;
  }
}
