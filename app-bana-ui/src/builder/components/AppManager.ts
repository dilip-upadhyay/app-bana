import { LitElement, html, css, unsafeCSS } from 'lit';
import './PipelineDashboard';
import './ThemeEditor';
import { customElement, state } from 'lit/decorators.js';
import { appStore } from '../store/AppStore';
import { currentStore } from '../store/TreeStore';
import { apiClient } from '../../core/api-client';
import { AuthService } from '../../pages/auth/auth-service';
import { RuntimeContext } from '../../runtime/RuntimeContext';
import type { AppMeta, AppListItem, CreateAppRequest } from '../../models/app-metadata';
import styles from './AppManager.css?inline';

@customElement('appbana-app-manager')
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
  @state() private isDirty = false;

  connectedCallback() {
    super.connectedCallback();
    appStore.onChange(() => this.updateState());

    // Load user val
    try {
      const u = localStorage.getItem('appbana_user');
      if (u) this.user = JSON.parse(u);
    } catch (e) { console.error(e); }

    this.updateState();
    window.addEventListener('app-bana-run-change', this.handleAppChange);
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    window.removeEventListener('app-bana-run-change', this.handleAppChange);
  }

  private handleAppChange = () => {
    this.isDirty = true;
  };

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

      // Update Runtime Context (new app is automatically selected by store usually, but let's be safe)
      const current = appStore.getCurrentApp();
      if (current) {
        const tenantId = AuthService.getUser()?.tenantId || 'default';
        RuntimeContext.getInstance().setContext(tenantId, current.id, 'dev');
      }
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

      // Update Runtime Context
      if (app) {
        const tenantId = AuthService.getUser()?.tenantId || 'default';
        RuntimeContext.getInstance().setContext(tenantId, app.id, 'dev');
      }

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

  private handleSaveApp = async (e: Event) => {
    e.stopPropagation();
    if (!this.currentApp) return;

    try {
      // Save current page from store if active
      if (currentStore) {
        const page = currentStore.getPage();
        console.log('[AppManager] Saving current page:', page.id);
        await appStore.savePage(this.currentApp.id, page);
      }

      // Also update app metadata just in case (though entities/pages save separately usually)
      // If we need to save specific app-level things, do it here.
      // For now, we assume saving the page is the primary action needed for "Save" in builder.

      this.isDirty = false;
      this.showToast('✅ App saved successfully');
    } catch (error) {
      console.error('Failed to save app:', error);
      this.showToast('❌ Failed to save app');
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
              <div class="current-app no-app">
                <span class="app-icon">📂</span>
                <span class="no-app-text">No app selected • Create or open an app to get started</span>
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
              <button class="btn success" @click=${this.handleSaveApp} style="margin-left: 8px;" title="${this.isDirty ? 'You have unsaved changes' : 'Save current changes'}">
                ${this.isDirty ? '💾 Save *' : '💾 Save'}
              </button>
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
                <label for="app-name">
                  App Name *
                  <span class="form-help">Give your app a descriptive, memorable name</span>
                </label>
                <input
                  id="app-name"
                  type="text"
                  placeholder="My Awesome App"
                  .value=${this.formName}
                  @input=${(e: Event) => this.formName = (e.target as HTMLInputElement).value}
                  required
                  autofocus
                />
              </div>

              <div class="form-group">
                <label for="app-description">
                  Description (Optional)
                  <span class="form-help">Help others understand what this app does</span>
                </label>
                <textarea
                  id="app-description"
                  placeholder="A powerful CRM system for managing customer relationships..."
                  .value=${this.formDescription}
                  @input=${(e: Event) => this.formDescription = (e.target as HTMLTextAreaElement).value}
                ></textarea>
              </div>

              <div class="form-group">
                <div class="template-label">
                  Choose a Template
                  <span class="form-help">Templates provide a starting structure you can customize</span>
                </div>
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
      console.log('[PUBLISH] 🚀 Starting backend-driven publish for app:', this.currentApp.name);
      console.log('[PUBLISH] 📦 App has', this.currentApp.entities?.length || 0, 'entities');
      
      // Log entities
      if (this.currentApp.entities && this.currentApp.entities.length > 0) {
        console.log('[PUBLISH] 📊 Entities to deploy:');
        this.currentApp.entities.forEach(entity => {
          console.log(`  - ${entity.name} (${entity.fields?.length || 0} fields)`);
        });
      }
      
      // Save current page before publish
      if (currentStore) {
        const page = currentStore.getPage();
        console.log('[PUBLISH] Auto-saving current page before publish. PageID:', page.id);
        await appStore.savePage(this.currentApp.id, page);
        console.log('[PUBLISH] Current page saved');
      }

      const tenantId = AuthService.getUser()?.tenantId || 'default';
      const env = 'DEV'; // Default to DEV environment

      // NEW BACKEND-DRIVEN PUBLISH
      // Call the new transactional publish endpoint
      console.log('[PUBLISH] 📡 Calling backend publish endpoint...');
      console.log('[PUBLISH] Endpoint: POST /api/${tenantId}/apps/${appId}/publish?env=${env}');
      console.log('[PUBLISH] Sending full AppMeta with entities, pages, navigation...');
      
      const publishResponse = await apiClient.post(
        `/api/${tenantId}/apps/${this.currentApp.id}/publish?env=${env}`,
        this.currentApp  // Send complete AppMeta
      );

      console.log('[PUBLISH] ✅ Backend publish response:', publishResponse);

      if (publishResponse.success) {
        console.log(`[PUBLISH] 🎉 SUCCESS - Version ${publishResponse.version} deployed to ${publishResponse.environment}`);
        console.log(`[PUBLISH] 🗄️  Tables created: ${publishResponse.tablesCreated.join(', ')}`);
        console.log(`[PUBLISH] ⏱️  Duration: ${publishResponse.durationMs}ms`);
        console.log(`[PUBLISH] 📝 Summary: ${publishResponse.summary}`);
        
        this.showPublishModal = false;
        this.showToast(`✅ App published! Version ${publishResponse.version} deployed to ${publishResponse.environment}. ${publishResponse.tablesCreated.length} tables created.`);
      } else {
        console.error('[PUBLISH] ❌ Publish failed:', publishResponse.error);
        console.error('[PUBLISH] Details:', publishResponse.details);
        alert(`Publish failed: ${publishResponse.error}`);
      }
    } catch (error) {
      console.error('[PUBLISH] ❌ Exception during publish:', error);
      alert(`Failed to publish app: ${error.message || error}`);
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
