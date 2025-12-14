import { LitElement, html, css, unsafeCSS } from 'lit';
import './PipelineDashboard';
import './ThemeEditor';
import { customElement, state } from 'lit/decorators.js';
import { appStore } from '../store/AppStore';
import type { AppMeta, AppListItem, CreateAppRequest } from '../../models/app-metadata';
import styles from './AppManager.css?inline';

@customElement('studio-app-manager')
export class AppManager extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;

  @state() private currentApp: AppMeta | undefined;
  @state() private showCreateModal = false;
  @state() private showSelectModal = false;
  @state() private showPublishModal = false;
  @state() private showPipelineModal = false;
  @state() private showThemeModal = false;
  @state() private isLoadingApps = false;
  @state() private appsLoadError: string | null = null;
  @state() private apps: AppListItem[] = [];

  // Form state
  @state() private formName = '';
  @state() private formDescription = '';
  @state() private formTemplate: 'blank' | 'single-page' | 'multi-page' | 'dashboard' = 'single-page';

  @state() private publishLabel = '';
  @state() private publishDescription = '';
  @state() private user: any = null;

  connectedCallback() {
    super.connectedCallback();
    appStore.onChange(() => this.updateState());

    // Load user val
    try {
      const u = localStorage.getItem('appbana_user');
      if (u) this.user = JSON.parse(u);
    } catch (e) { console.error(e); }

    this.updateState();
  }

  private handleLogout = () => {
    localStorage.removeItem('appbana_token');
    localStorage.removeItem('appbana_user');
    window.location.href = '/login';
  }

  private updateState() {
    this.currentApp = appStore.getCurrentApp();
    this.apps = appStore.listApps();
  }

  private handleCreateApp = (e?: Event) => {
    e?.stopPropagation();
    // Use setTimeout to prevent the click event from immediately closing the modal
    setTimeout(() => {
      this.showCreateModal = true;
      this.formName = '';
      this.formDescription = '';
      this.formTemplate = 'single-page';
    }, 0);
  }

  private handleSelectApp = (e?: Event) => {
    e?.stopPropagation();
    this.isLoadingApps = true;
    this.appsLoadError = null;
    appStore.loadApps()
      .then(() => {
        this.isLoadingApps = false;
        this.showSelectModal = true;
        this.updateState();
      })
      .catch((err) => {
        this.isLoadingApps = false;
        this.appsLoadError = 'Failed to load apps. Please try again.';
        this.showSelectModal = true;
        this.updateState();
      });
  }

  private handleCloseModal = (e?: Event) => {
    e?.stopPropagation();
    this.showCreateModal = false;
    this.showSelectModal = false;
    this.showPublishModal = false;
    this.showPipelineModal = false;
    this.showThemeModal = false;
  }

  private handleSubmitCreate = async (e: Event) => {
    e.preventDefault();
    e.stopPropagation();

    if (!this.formName.trim()) {
      alert('Please enter an app name');
      return;
    }

    const request: CreateAppRequest = {
      name: this.formName.trim(),
      description: this.formDescription.trim() || undefined,
      template: this.formTemplate,
    };

    try {
      await appStore.createApp(request);
      this.showCreateModal = false;
      this.showToast(`✅ Created app: ${request.name}`);
    } catch (error) {
      console.error('Failed to create app:', error);
      alert('Failed to create app. Please try again.');
    }
  }

  private handleSelectExistingApp = async (appId: string) => {
    try {
      await appStore.setCurrentApp(appId);
      this.showSelectModal = false;
      const app = appStore.getApp(appId);
      this.showToast(`✅ Switched to: ${app?.name}`);
    } catch (error) {
      console.error('Failed to select app:', error);
      alert('Failed to select app. Please try again.');
    }
  }

  private handleDeleteApp = async (appId: string, appName: string, e: Event) => {
    e.stopPropagation();

    if (!confirm(`Are you sure you want to delete "${appName}"? This will delete all pages in this app.`)) {
      return;
    }

    try {
      await appStore.deleteApp(appId);
      this.showToast(`🗑️ Deleted app: ${appName}`);
    } catch (error) {
      console.error('Failed to delete app:', error);
      alert(error instanceof Error ? error.message : 'Failed to delete app');
    }
  }

  private showToast(message: string) {
    const toast = document.createElement('div');
    toast.style.cssText = `
      position: fixed;
      bottom: 24px;
      right: 24px;
      padding: 12px 20px;
      background: #111827;
      color: white;
      border-radius: 8px;
      font-size: 14px;
      z-index: 10000;
      animation: slideIn 0.3s ease;
    `;
    toast.textContent = message;
    document.body.appendChild(toast);
    setTimeout(() => {
      toast.style.animation = 'slideOut 0.3s ease';
      setTimeout(() => document.body.removeChild(toast), 300);
    }, 3000);
  }

  private formatDate(timestamp: number): string {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;

    return date.toLocaleDateString();
  }

  render() {
    return html`
      <div class="app-manager">
        <div class="app-header">
          <div class="app-title">
            <h2>📱 App Manager</h2>
            ${this.currentApp ? html`
              <div class="current-app">
                <span class="app-icon">📦</span>
                <span class="app-name">${this.currentApp.name}</span>
                <span class="page-count">${this.currentApp.pages.length} page${this.currentApp.pages.length !== 1 ? 's' : ''}</span>
              </div>
            ` : html`
              <div class="current-app" style="color: #9ca3af;">
                No app selected
              </div>
            `}
          </div>

          <div class="app-actions">
            ${this.user ? html`
              <div style="display:flex; align-items:center; margin-right:1rem; padding-right:1rem; border-right:1px solid rgba(255,255,255,0.2);">
                <span style="margin-right:0.5rem; font-weight:500;">👤 ${this.user.name}</span>
                <button class="btn small" style="padding:4px 8px; font-size:0.8rem;" @click=${this.handleLogout}>Logout</button>
              </div>
            ` : ''}
            <button class="btn" @click=${this.handleSelectApp}>
              📂 Open App
            </button>
            <button class="btn btn-primary" @click=${this.handleCreateApp}>
              ➕ New App
            </button>
            ${this.currentApp ? html`
              <button class="btn btn-success" @click=${this.handlePublishClick} style="background: #10b981; color: white; margin-left: 8px;">
                🚀 Publish
              </button>
              <button class="btn" @click=${() => this.showPipelineModal = true} style="margin-left: 8px;" title="CD Pipeline">
                🔄 Pipeline
              </button>
              <button class="btn" @click=${() => this.showThemeModal = true} style="margin-left: 8px;" title="Appearance">
                🎨 Theme
              </button>
            ` : ''}
          </div>
        </div>
      </div>

      ${this.showCreateModal ? this.renderCreateModal() : ''}
      ${this.showSelectModal ? this.renderSelectModal() : ''}
      ${this.showPublishModal ? this.renderPublishModal() : ''}
      ${this.showPipelineModal ? this.renderPipelineModal() : ''}
      ${this.showThemeModal ? this.renderThemeModal() : ''}
    `;
  }

  private renderCreateModal() {
    const templates = [
      {
        id: 'blank',
        icon: '📄',
        name: 'Blank',
        description: 'Start from scratch with an empty canvas'
      },
      {
        id: 'single-page',
        icon: '🌐',
        name: 'Single Page',
        description: 'Header, content area, and footer layout'
      },
      {
        id: 'dashboard',
        icon: '📊',
        name: 'Dashboard',
        description: 'Sidebar navigation with content area'
      },
    ];

    return html`
      <div class="modal-overlay" @click=${this.handleCloseModal}>
        <div class="modal" @click=${(e: Event) => e.stopPropagation()}>
          <div class="modal-header">
            <h3>Create New App</h3>
            <button class="modal-close" @click=${this.handleCloseModal}>×</button>
          </div>

          <form @submit=${this.handleSubmitCreate}>
            <div class="modal-body">
              <div class="form-group">
                <label for="app-name">App Name *</label>
                <input
                  id="app-name"
                  type="text"
                  placeholder="My Awesome App"
                  .value=${this.formName}
                  @input=${(e: Event) => this.formName = (e.target as HTMLInputElement).value}
                  required
                  autofocus
                />
                <div class="form-help">Give your app a descriptive, memorable name</div>
              </div>

              <div class="form-group">
                <label for="app-description">Description (Optional)</label>
                <textarea
                  id="app-description"
                  placeholder="A powerful CRM system for managing customer relationships..."
                  .value=${this.formDescription}
                  @input=${(e: Event) => this.formDescription = (e.target as HTMLTextAreaElement).value}
                ></textarea>
                <div class="form-help">Help others understand what this app does</div>
              </div>

              <div class="form-group">
                <div class="template-label">Choose a Template</div>
                <div class="template-options">
                  ${templates.map(template => html`
                    <div
                      class="template-option ${this.formTemplate === template.id ? 'selected' : ''}"
                      @click=${() => this.formTemplate = template.id as any}
                    >
                      <div class="template-icon">${template.icon}</div>
                      <div class="template-name">${template.name}</div>
                      <div class="template-description">${template.description}</div>
                    </div>
                  `)}
                </div>
                <div class="form-help">Templates provide a starting structure you can customize</div>
              </div>
            </div>

            <div class="modal-footer">
              <button type="button" class="btn" @click=${this.handleCloseModal}>
                ✕ Cancel
              </button>
              <button type="submit" class="btn btn-primary">
                ✓ Create App
              </button>
            </div>
          </form>
        </div>
      </div>
    `;
  }

  private renderSelectModal() {
    return html`
      <div class="modal-overlay" @click=${this.handleCloseModal}>
        <div class="modal" @click=${(e: Event) => e.stopPropagation()}>
          <div class="modal-header">
            <h3>Select App</h3>
            <button class="modal-close" @click=${this.handleCloseModal}>×</button>
          </div>
          <div class="modal-body">
            ${this.isLoadingApps ? html`
              <div class="empty-state">
                <div class="empty-state-icon">⏳</div>
                <p><strong>Loading apps...</strong></p>
              </div>
            ` : this.appsLoadError ? html`
              <div class="empty-state">
                <div class="empty-state-icon">❌</div>
                <p><strong>${this.appsLoadError}</strong></p>
                <button class="btn" @click=${this.handleSelectApp}>Retry</button>
              </div>
            ` : this.apps.length === 0 ? html`
              <div class="empty-state">
                <div class="empty-state-icon">📱</div>
                <p><strong>No apps yet</strong></p>
                <p>Create your first app to get started</p>
              </div>
            ` : html`
              <div class="app-list">
                ${this.apps.map(app => html`
                  <div
                    class="app-item ${this.currentApp?.id === app.id ? 'selected' : ''}"
                    @click=${() => this.handleSelectExistingApp(app.id)}
                  >
                    <div class="app-item-content">
                      <div class="app-item-title">${app.name}</div>
                      ${app.description ? html`
                        <div class="app-item-description">${app.description}</div>
                      ` : ''}
                      <div class="app-item-meta">
                        <span>📄 ${app.pageCount} page${app.pageCount !== 1 ? 's' : ''}</span>
                        <span>🕒 ${this.formatDate(app.updated)}</span>
                      </div>
                    </div>
                    <div class="app-item-actions">
                      <button
                        class="icon-btn danger"
                        @click=${(e: Event) => this.handleDeleteApp(app.id, app.name, e)}
                        title="Delete app"
                      >
                        🗑️
                      </button>
                    </div>
                  </div>
                `)}
              </div>
            `}
          </div>
          <div class="modal-footer">
            <button class="btn" @click=${this.handleCloseModal}>
              Close
            </button>
          </div>
        </div>
      </div>
    `;
  }
  private handlePublishClick = (e: Event) => {
    e.stopPropagation();
    if (!this.currentApp) return;

    // Default label: v1.0.{next} (we don't know next, so just v{Date})
    const date = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    this.publishLabel = `v${date}`;
    this.publishDescription = '';
    this.showPublishModal = true;
  }

  private handleSubmitPublish = async (e: Event) => {
    e.preventDefault();
    if (!this.currentApp) return;

    try {
      const response = await fetch(`/api/apps/${this.currentApp.id}/versions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          label: this.publishLabel,
          description: this.publishDescription
        })
      });

      if (!response.ok) throw new Error('Failed to create version');

      const versionData = await response.json();

      // Auto-deploy to DEV
      const deployRes = await fetch(`/api/apps/${this.currentApp.id}/deploy/${versionData.id}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ environment: 'DEV' })
      });

      if (!deployRes.ok) console.warn('Auto-deploy to DEV failed');

      this.showPublishModal = false;
      this.showToast('✅ App published & deployed to DEV!');
    } catch (error) {
      console.error('Publish failed:', error);
      alert('Failed to publish app');
    }
  }

  private renderPublishModal() {
    return html`
      <div class="modal-overlay" @click=${this.handleCloseModal}>
        <div class="modal" @click=${(e: Event) => e.stopPropagation()}>
          <div class="modal-header">
            <h3>Publish Release</h3>
            <button class="modal-close" @click=${this.handleCloseModal}>×</button>
          </div>
          <form @submit=${this.handleSubmitPublish}>
            <div class="modal-body">
              <div class="form-group">
                <label>Version Label</label>
                <input 
                  type="text" 
                  .value=${this.publishLabel}
                  @input=${(e: Event) => this.publishLabel = (e.target as HTMLInputElement).value}
                  required
                  placeholder="v1.0.0"
                />
              </div>
              <div class="form-group">
                <label>Release Notes</label>
                <textarea 
                  .value=${this.publishDescription}
                  @input=${(e: Event) => this.publishDescription = (e.target as HTMLTextAreaElement).value}
                  placeholder="What's new in this release?"
                  rows="4"
                ></textarea>
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn" @click=${this.handleCloseModal}>Cancel</button>
              <button type="submit" class="btn btn-primary" style="background: #10b981;">🚀 Publish Release</button>
            </div>
          </form>
        </div>
      </div>
    `;
  }

  private renderPipelineModal() {
    if (!this.currentApp) return '';
    return html`
      <div class="modal-overlay" @click=${this.handleCloseModal}>
        <div class="modal" style="max-width: 1000px; width: 90%;" @click=${(e: Event) => e.stopPropagation()}>
          <div class="modal-header">
            <h3>DevOps Pipeline: ${this.currentApp.name}</h3>
            <button class="modal-close" @click=${this.handleCloseModal}>×</button>
          </div>
          <div class="modal-body" style="background: #f1f5f9; border-radius: 8px;">
            <pipeline-dashboard .appId=${this.currentApp.id}></pipeline-dashboard>
          </div>
          <div class="modal-footer">
            <button class="btn" @click=${this.handleCloseModal}>Close</button>
          </div>
        </div>
      </div>
    `;
  }

  private renderThemeModal() {
    if (!this.currentApp) return '';
    return html`
      <div class="modal-overlay" @click=${this.handleCloseModal}>
        <div class="modal" style="max-width: 500px;" @click=${(e: Event) => e.stopPropagation()}>
          <div class="modal-header">
            <h3>App Appearance</h3>
            <button class="modal-close" @click=${this.handleCloseModal}>×</button>
          </div>
          <div class="modal-body">
            <theme-editor .app=${this.currentApp}></theme-editor>
          </div>
          <div class="modal-footer">
            <button class="btn" @click=${this.handleCloseModal}>Close</button>
          </div>
        </div>
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'studio-app-manager': AppManager;
  }
}
