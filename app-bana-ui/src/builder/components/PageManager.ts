import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { initStore, initNewPageStore, currentStore } from '../store/TreeStore';
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

  // Template selection state
  @state() private includeNav = false;
  @state() private includeSidenav = false;
  @state() private includeFooter = false;
  @state() private includeMain = true; // Always include main by default

  // Track if we're switching to a newly created page
  private isNewPage = false;

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
      } else {
        // No pages available - clear the store
        this.clearStore();
        console.log('[PageManager] No pages available - cleared store');
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
      // If this is a newly created page, skip loading any existing draft
      if (this.isNewPage) {
        console.log('[PageManager] Initializing NEW page store (skipping draft)');
        initNewPageStore(page);
        this.isNewPage = false; // Reset flag
      } else {
        console.log('[PageManager] Initializing existing page store (loading draft if exists)');
        initStore(page);
      }

      // Re-register onChange listener for the new store
      if (currentStore) {
        currentStore.onChange(() => {
          this.saveCurrentPage();
        });
      }

      console.log('[PageManager] Switched to page:', pageId, page);
    }
  }

  private saveCurrentPage() {
    if (!this.currentApp || !this.currentPageId || !currentStore) return;

    const page = currentStore.getPage();
    appStore.savePage(this.currentApp.id, page);
  }

  private handleCreatePage() {
    // Reset template selections
    this.includeNav = false;
    this.includeSidenav = false;
    this.includeFooter = false;
    this.includeMain = true;

    this.showCreateModal = true;
    this.formName = '';
    this.formPath = '/new-page';
  }

  private handleCloseModal() {
    this.showCreateModal = false;
    this.showTemplateModal = false;
  }

  private handleNextToTemplate(e: Event) {
    e.preventDefault();

    if (!this.formName.trim()) {
      alert('Please enter a page name');
      return;
    }

    // Move to template selection
    this.showCreateModal = false;
    this.showTemplateModal = true;
  }

  private handleBackToBasicInfo() {
    this.showTemplateModal = false;
    this.showCreateModal = true;
  }

  private handleSubmitCreate(e?: Event) {
    if (e) e.preventDefault();

    if (!this.currentApp || !this.formName.trim()) {
      alert('Please enter a page name');
      return;
    }

    // Generate unique page ID
    const pageId = this.generatePageId(this.formName);

    console.log('[PageManager] Creating new page with ID:', pageId);

    // Build page structure based on template selections
    const newPage = this.buildPageFromTemplate(pageId);

    console.log('[PageManager] New page data:', newPage);

    // Clear any existing draft for this page ID (in case it was used before)
    const draftKey = `studio.draft.${pageId}`;
    console.log('[PageManager] Clearing existing draft:', draftKey);
    localStorage.removeItem(draftKey);

    // Add page to app
    appStore.addPage(this.currentApp.id, newPage);

    // Switch to new page - mark as new so we skip draft loading
    this.isNewPage = true;
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
      // Clear the draft from localStorage before deleting
      const draftKey = `studio.draft.${pageId}`;
      console.log('[PageManager] Clearing draft for deleted page:', draftKey);
      localStorage.removeItem(draftKey);

      appStore.removePage(this.currentApp.id, pageId);

      // Switch to another page if any exist
      const remainingPages = this.pages.filter(p => p.id !== pageId);
      if (remainingPages.length > 0) {
        this.currentPageId = remainingPages[0].id;
        this.switchToPage(this.currentPageId);
      } else {
        // No pages left - clear current page and store
        this.currentPageId = null;
        this.clearStore();
        console.log('[PageManager] No pages left in app - cleared store');
      }

      this.showToast(`🗑️ Deleted page: ${pageName}`);
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to delete page');
    }
  }

  private buildPageFromTemplate(pageId: string): PageMeta {
    const nodes: ComponentNode[] = [];
    const rootChildren: string[] = [];

    // Build the page structure based on template selections
    let nodeCounter = 1;

    // Add Nav section if selected
    if (this.includeNav) {
      const navId = `nav-${nodeCounter++}`;
      nodes.push({
        id: navId,
        type: 'container',
        props: {
          className: 'nav-container',
          style: 'display: flex; justify-content: space-between; align-items: center; padding: 1rem 2rem; background: #1f2937; color: white; min-height: 60px;',
          'data-section': 'nav'
        },
        children: []
      });
      rootChildren.push(navId);
    }

    // Create main content wrapper (if sidenav is included, we need a flex layout)
    if (this.includeSidenav) {
      const contentWrapperId = `content-wrapper-${nodeCounter++}`;
      const contentWrapperChildren: string[] = [];

      // Add Sidenav
      const sidenavId = `sidenav-${nodeCounter++}`;
      nodes.push({
        id: sidenavId,
        type: 'container',
        props: {
          className: 'sidenav-container',
          style: 'width: 250px; background: #f3f4f6; padding: 1rem; min-height: 400px; border-right: 1px solid #e5e7eb;',
          'data-section': 'sidenav'
        },
        children: []
      });
      contentWrapperChildren.push(sidenavId);

      // Add Main section
      if (this.includeMain) {
        const mainId = `main-${nodeCounter++}`;
        nodes.push({
          id: mainId,
          type: 'container',
          props: {
            className: 'main-container',
            style: 'flex: 1; padding: 2rem; min-height: 400px;',
            'data-section': 'main'
          },
          children: []
        });
        contentWrapperChildren.push(mainId);
      }

      // Add the content wrapper
      nodes.push({
        id: contentWrapperId,
        type: 'container',
        props: {
          className: 'content-wrapper',
          style: 'display: flex; flex: 1;'
        },
        children: contentWrapperChildren
      });
      rootChildren.push(contentWrapperId);
    } else {
      // No sidenav, just add main directly
      if (this.includeMain) {
        const mainId = `main-${nodeCounter++}`;
        nodes.push({
          id: mainId,
          type: 'container',
          props: {
            className: 'main-container',
            style: 'padding: 2rem; min-height: 400px;',
            'data-section': 'main'
          },
          children: []
        });
        rootChildren.push(mainId);
      }
    }

    // Add Footer section if selected
    if (this.includeFooter) {
      const footerId = `footer-${nodeCounter++}`;
      nodes.push({
        id: footerId,
        type: 'container',
        props: {
          className: 'footer-container',
          style: 'padding: 2rem; background: #1f2937; color: white; text-align: center; min-height: 80px;',
          'data-section': 'footer'
        },
        children: []
      });
      rootChildren.push(footerId);
    }

    // Create root container
    const rootNode: ComponentNode = {
      id: 'root',
      type: 'container',
      props: {
        style: 'display: flex; flex-direction: column; min-height: 100vh;'
      },
      children: rootChildren
    };

    nodes.unshift(rootNode); // Add root at the beginning

    return {
      metaVersion: 1,
      id: pageId,
      name: this.formName.trim(),
      path: this.formPath.trim() || `/${pageId}`,
      rootId: 'root',
      nodes
    };
  }

  private clearStore() {
    // Clear the current store to ensure canvas is empty
    if (currentStore) {
      console.log('[PageManager] Clearing current store');
      // Create an empty page to clear the canvas
      const emptyPage: PageMeta = {
        metaVersion: 1,
        id: 'empty',
        name: 'Empty',
        path: '/empty',
        rootId: 'root',
        nodes: [
          {
            id: 'root',
            type: 'container',
            props: {},
            children: [],
          },
        ],
      };
      initStore(emptyPage, { persist: false });
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
      ${this.showTemplateModal ? this.renderTemplateModal() : ''}
    `;
  }

  private renderCreateModal() {
    return html`
      <div class="modal-overlay" @click=${this.handleCloseModal}>
        <div class="modal" @click=${(e: Event) => e.stopPropagation()}>
          <div class="modal-header">
            <h3>📄 Create New Page - Step 1</h3>
            <button class="modal-close" @click=${this.handleCloseModal}>×</button>
          </div>

          <form @submit=${this.handleNextToTemplate}>
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
                Next →
              </button>
            </div>
          </form>
        </div>
      </div>
    `;
  }

  private renderTemplateModal() {
    return html`
      <div class="modal-overlay" @click=${this.handleCloseModal}>
        <div class="modal modal-wide" @click=${(e: Event) => e.stopPropagation()}>
          <div class="modal-header">
            <h3>🎨 Choose Page Sections - Step 2</h3>
            <div class="header-actions">
              <button type="button" class="btn btn-primary" @click=${this.handleSubmitCreate}>
                ✓ Create Page
              </button>
              <button class="modal-close" @click=${this.handleCloseModal}>×</button>
            </div>
          </div>

          <div class="modal-body">
            <p class="template-help">Select the sections you want to include in your page:</p>

            <div class="template-options">
              <div class="template-option ${this.includeNav ? 'selected' : ''}"
                   @click=${() => this.includeNav = !this.includeNav}>
                <div class="option-icon">🧭</div>
                <div class="option-content">
                  <h4>Navigation Bar</h4>
                  <p>Top navigation with logo and menu</p>
                </div>
                <div class="option-checkbox">
                  ${this.includeNav ? '✓' : ''}
                </div>
              </div>

              <div class="template-option ${this.includeSidenav ? 'selected' : ''}"
                   @click=${() => this.includeSidenav = !this.includeSidenav}>
                <div class="option-icon">📁</div>
                <div class="option-content">
                  <h4>Side Navigation</h4>
                  <p>Left sidebar for secondary navigation</p>
                </div>
                <div class="option-checkbox">
                  ${this.includeSidenav ? '✓' : ''}
                </div>
              </div>

              <div class="template-option selected disabled">
                <div class="option-icon">📄</div>
                <div class="option-content">
                  <h4>Main Content</h4>
                  <p>Primary content area (always included)</p>
                </div>
                <div class="option-checkbox">✓</div>
              </div>

              <div class="template-option ${this.includeFooter ? 'selected' : ''}"
                   @click=${() => this.includeFooter = !this.includeFooter}>
                <div class="option-icon">📝</div>
                <div class="option-content">
                  <h4>Footer</h4>
                  <p>Bottom footer section</p>
                </div>
                <div class="option-checkbox">
                  ${this.includeFooter ? '✓' : ''}
                </div>
              </div>
            </div>

            <div class="template-preview">
              <h4>Preview:</h4>
              <div class="preview-layout">
                ${this.includeNav ? html`<div class="preview-section nav">Nav</div>` : ''}
                <div class="preview-content">
                  ${this.includeSidenav ? html`<div class="preview-section sidenav">Sidenav</div>` : ''}
                  <div class="preview-section main">Main</div>
                </div>
                ${this.includeFooter ? html`<div class="preview-section footer">Footer</div>` : ''}
              </div>
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn" @click=${this.handleBackToBasicInfo}>
              ← Back
            </button>
          </div>
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
