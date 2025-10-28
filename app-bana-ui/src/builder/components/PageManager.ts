import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { initStore, currentStore } from '../store/TreeStore';
import { appStore } from '../store/AppStore';
import type { PageMeta, ComponentNode } from '../../models/metadata';
import styles from './PageManager.css?inline';

@customElement('studio-page-manager')
export class PageManager extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;

  @state() private currentApp = appStore.getCurrentApp();
  @state() private pages: PageMeta[] = [];
  @state() private currentPageId: string | null = null;
  @state() private showCreateModal = false;
  @state() private showTemplateModal = false;

  // Form state
  @state() private formName = '';
  @state() private formPath = '';

  connectedCallback() {
    super.connectedCallback();

    // Listen to app changes
    appStore.onChange(() => {
      this.currentApp = appStore.getCurrentApp();
      this.loadPages();
    });

    // Listen to store changes
    if (currentStore) {
      currentStore.onChange(() => {
        this.saveCurrentPage();
      });
    }

    this.loadPages();
  }

  private loadPages() {
    if (!this.currentApp) {
      this.pages = [];
      this.currentPageId = null;
      console.log('[PageManager] No app selected - clearing pages');
      return;
    }

    console.log('[PageManager] Loading pages for app:', this.currentApp.name, 'Pages:', this.currentApp.pages);

    // Load all pages for current app
    this.pages = this.currentApp.pages
      .map((pageId: string) => appStore.loadPage(this.currentApp!.id, pageId))
      .filter((page): page is PageMeta => page !== undefined);

    console.log('[PageManager] Loaded', this.pages.length, 'pages:', this.pages.map(p => p.name));

    // Reset currentPageId if it doesn't belong to current app, or set it if not set
    const currentPageExists = this.pages.some(p => p.id === this.currentPageId);
    if (!currentPageExists || !this.currentPageId) {
      const newPageId = this.currentApp.defaultPage || (this.pages.length > 0 ? this.pages[0].id : null);
      console.log('[PageManager] Switching to', currentPageExists ? 'existing' : 'new', 'page:', newPageId);
      this.currentPageId = newPageId;
      if (this.currentPageId) {
        this.switchToPage(this.currentPageId);
      }
    } else {
      // Current page exists in new app, just refresh it
      console.log('[PageManager] Refreshing current page:', this.currentPageId);
      this.switchToPage(this.currentPageId);
    }
  }

  private switchToPage(pageId: string) {
    if (!this.currentApp) return;

    const page = appStore.loadPage(this.currentApp.id, pageId);
    if (page) {
      this.currentPageId = pageId;

      // Reinitialize TreeStore with this page
      initStore(page);

      console.log('[PageManager] Switched to page:', pageId, page);
    }
  }

  private saveCurrentPage() {
    if (!this.currentApp || !this.currentPageId || !currentStore) return;

    const page = currentStore.getPage();
    appStore.savePage(this.currentApp.id, page);
  }

  private handleCreatePage() {
    this.showCreateModal = true;
    this.formName = '';
    this.formPath = '/new-page';
  }

  private handleCloseModal() {
    this.showCreateModal = false;
    this.showTemplateModal = false;
  }

  private handleSubmitCreate(e: Event) {
    e.preventDefault();

    if (!this.currentApp || !this.formName.trim()) {
      alert('Please enter a page name');
      return;
    }

    // Generate unique page ID
    const pageId = this.generatePageId(this.formName);

    // Create blank page
    const newPage: PageMeta = {
      metaVersion: 1,
      id: pageId,
      name: this.formName.trim(),
      path: this.formPath.trim() || `/${pageId}`,
      rootId: 'root',
      nodes: [
        {
          id: 'root',
          type: 'container',
          props: { className: 'page-container' },
          children: ['welcome-text'],
        },
        {
          id: 'welcome-text',
          type: 'text',
          props: {
            tag: 'h1',
            text: `Welcome to ${this.formName}`
          },
        },
      ],
    };

    // Add page to app
    appStore.addPage(this.currentApp.id, newPage);

    // Switch to new page
    this.currentPageId = pageId;
    this.switchToPage(pageId);

    this.showCreateModal = false;
    this.showToast(`✅ Created page: ${this.formName}`);
  }

  private handleDeletePage(pageId: string, pageName: string, e: Event) {
    e.stopPropagation();

    if (!this.currentApp) return;

    if (!confirm(`Delete page "${pageName}"?`)) {
      return;
    }

    try {
      appStore.removePage(this.currentApp.id, pageId);

      // Switch to another page if any exist
      const remainingPages = this.pages.filter(p => p.id !== pageId);
      if (remainingPages.length > 0) {
        this.currentPageId = remainingPages[0].id;
        this.switchToPage(this.currentPageId);
      } else {
        // No pages left - clear current page
        this.currentPageId = null;
        console.log('[PageManager] No pages left in app');
      }

      this.showToast(`🗑️ Deleted page: ${pageName}`);
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to delete page');
    }
  }

  private generatePageId(name: string): string {
    let id = name.toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-|-$/g, '');

    let counter = 1;
    let uniqueId = id;
    while (this.pages.some(p => p.id === uniqueId)) {
      uniqueId = `${id}-${counter}`;
      counter++;
    }
    return uniqueId;
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
    `;
    toast.textContent = message;
    document.body.appendChild(toast);
    setTimeout(() => document.body.removeChild(toast), 3000);
  }

  render() {
    if (!this.currentApp) {
      return html`
        <div class="page-manager">
          <div class="no-app-message">
            <span>📱 No app selected</span>
            <span style="color: #9ca3af; font-size: 13px;">Create or select an app to manage pages</span>
          </div>
        </div>
      `;
    }

    // If app has 0 pages, show message
    if (this.pages.length === 0) {
      return html`
        <div class="page-manager">
          <div class="page-tabs">
            <div class="no-pages-message">
              <span>📄 No pages yet</span>
            </div>
            <button class="new-page-btn" @click=${this.handleCreatePage} title="Create new page">
              ➕ New Page
            </button>
          </div>
        </div>

        ${this.showCreateModal ? this.renderCreateModal() : ''}
      `;
    }

    return html`
      <div class="page-manager">
        <div class="page-tabs">
          ${this.pages.map(page => html`
            <div
              class="page-tab ${this.currentPageId === page.id ? 'active' : ''}"
              @click=${() => this.switchToPage(page.id)}
            >
              <span class="page-name">${page.name}</span>
              <button
                class="delete-page-btn"
                @click=${(e: Event) => this.handleDeletePage(page.id, page.name, e)}
                title="Delete page"
              >
                ✕
              </button>
            </div>
          `)}

          <button class="new-page-btn" @click=${this.handleCreatePage} title="Create new page">
            ➕ New Page
          </button>
        </div>
      </div>

      ${this.showCreateModal ? this.renderCreateModal() : ''}
    `;
  }

  private renderCreateModal() {
    return html`
      <div class="modal-overlay" @click=${this.handleCloseModal}>
        <div class="modal" @click=${(e: Event) => e.stopPropagation()}>
          <div class="modal-header">
            <h3>📄 Create New Page</h3>
            <button class="modal-close" @click=${this.handleCloseModal}>×</button>
          </div>

          <form @submit=${this.handleSubmitCreate}>
            <div class="modal-body">
              <div class="form-group">
                <label for="page-name">Page Name *</label>
                <input
                  id="page-name"
                  type="text"
                  placeholder="Dashboard"
                  .value=${this.formName}
                  @input=${(e: Event) => this.formName = (e.target as HTMLInputElement).value}
                  required
                  autofocus
                />
                <div class="form-help">A descriptive name for this page</div>
              </div>

              <div class="form-group">
                <label for="page-path">URL Path *</label>
                <input
                  id="page-path"
                  type="text"
                  placeholder="/dashboard"
                  .value=${this.formPath}
                  @input=${(e: Event) => this.formPath = (e.target as HTMLInputElement).value}
                  required
                />
                <div class="form-help">The URL path for this page (e.g., /dashboard, /about)</div>
              </div>
            </div>

            <div class="modal-footer">
              <button type="button" class="btn" @click=${this.handleCloseModal}>
                Cancel
              </button>
              <button type="submit" class="btn btn-primary">
                Create Page
              </button>
            </div>
          </form>
        </div>
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'studio-page-manager': PageManager;
  }
}
