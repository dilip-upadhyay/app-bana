import { LitElement, html, css, unsafeCSS, render } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { initStore, initNewPageStore, currentStore } from '../store/TreeStore';
import { appStore } from '../store/AppStore';
import type { PageMeta, ComponentNode } from '../../models/metadata';
import { templateStore, PageTemplate } from '../store/TemplateStore';
import { renderPageTemplate } from '../../runtime/renderer/Renderer';
import styles from './PageManager.css?inline';

@customElement('appbana-page-manager')
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

  // Template selection state - for custom builder
  @state() private includeNav = false;
  @state() private includeSidenav = false;
  @state() private includeFooter = false;
  @state() private includeMain = true; // Always include main by default

  // Pre-built template selection
  // Pre-built template selection
  @state() private selectedTemplate: string = 'custom';

  // Context menu state
  @state() private contextMenuVisible = false;
  @state() private contextMenuPageId: string | null = null;
  @state() private contextMenuX = 0;
  @state() private contextMenuY = 0;

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

    window.addEventListener('request-page-navigation', this.handlePageNavigationRequest as EventListener);

    window.addEventListener('request-page-navigation', this.handlePageNavigationRequest as EventListener);

    // Initialize templates
    this.initTemplates();

    this.loadPages();
  }

  private async initTemplates() {
    try {
      await templateStore.loadTemplates();
      this.requestUpdate(); // Re-render to show templates in modal if open
    } catch (e) {
      console.error('[PageManager] Failed to load templates:', e);
    }
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    window.removeEventListener('request-page-navigation', this.handlePageNavigationRequest as EventListener);
  }

  private async loadPages() {
    if (!this.currentApp) {
      this.pages = [];
      this.currentPageId = null;
      console.log('[PageManager] No app selected - clearing pages');
      return;
    }

    console.log('[PageManager] Loading pages for app:', this.currentApp.name, 'Pages:', this.currentApp.pages);

    // Load all pages for current app
    const pagePromises = this.currentApp.pages.map((pageId: string) =>
      appStore.loadPage(this.currentApp!.id, pageId)
    );
    this.pages = (await Promise.all(pagePromises))
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

  private async switchToPage(pageId: string) {
    if (!this.currentApp) return;

    const page = await appStore.loadPage(this.currentApp.id, pageId);
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

  private async saveCurrentPage() {
    if (!this.currentApp || !this.currentPageId || !currentStore) return;

    const page = currentStore.getPage();
    await appStore.savePage(this.currentApp.id, page);
  }

  private handleCreatePage() {
    // Reset template selections
    this.selectedTemplate = 'custom';
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

  private handleNextToTemplate = (e: Event) => {
    e.preventDefault();
    e.stopPropagation();

    if (!this.formName.trim()) {
      alert('Please enter a page name');
      return;
    }

    // Move to template selection using setTimeout to ensure clean state transition
    this.showCreateModal = false;

    // Use setTimeout to ensure the first modal is fully unmounted before showing the second
    setTimeout(() => {
      this.showTemplateModal = true;
      this.requestUpdate();
    }, 50);
  }

  private handleBackToBasicInfo() {
    this.showTemplateModal = false;
    this.showCreateModal = true;
  }

  private async handleSubmitCreate(e?: Event) {
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
    await appStore.addPage(this.currentApp.id, newPage);

    // Switch to new page - mark as new so we skip draft loading
    this.isNewPage = true;
    this.currentPageId = pageId;
    this.switchToPage(pageId);

    // Close both modals
    this.showCreateModal = false;
    this.showTemplateModal = false;

    this.showToast(`✅ Created page: ${this.formName}`);
  }

  private async handleDeletePage(pageId: string, pageName: string, e: Event) {
    e.stopPropagation();

    if (!this.currentApp) return;

    if (!confirm(`Delete page "${pageName}"?`)) {
      return;
    }

    try {
      // Capture pageId explicitly to avoid scope issues in closures
      const deletedPageId = pageId;
      
      // Clear the draft from localStorage before deleting
      const draftKey = `studio.draft.${deletedPageId}`;
      console.log('[PageManager] Clearing draft for deleted page:', draftKey);
      localStorage.removeItem(draftKey);

      await appStore.removePage(this.currentApp.id, deletedPageId);

      // Switch to another page if any exist
      const remainingPages = this.pages.filter(p => p.id !== deletedPageId);
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

  private handleContextMenu = (e: MouseEvent, pageId: string) => {
    e.preventDefault();
    e.stopPropagation();

    this.contextMenuVisible = true;
    this.contextMenuPageId = pageId;
    this.contextMenuX = e.clientX;
    this.contextMenuY = e.clientY;

    // Close context menu when clicking anywhere
    const closeMenu = () => {
      this.contextMenuVisible = false;
      document.removeEventListener('click', closeMenu);
    };

    // Delay to prevent immediate closure
    setTimeout(() => {
      document.addEventListener('click', closeMenu);
    }, 10);
  };

  private handleSaveAsTemplate = async (e: Event) => {
    e.stopPropagation();

    if (!this.contextMenuPageId) return;

    const page = this.pages.find(p => p.id === this.contextMenuPageId);
    if (!page) return;

    // Close menu first
    this.contextMenuVisible = false;

    const name = prompt("Start a new template based on '" + page.name + "'\n\nEnter template name:", page.name + " Template");
    if (!name) return;

    const description = prompt("Enter a description for this template:", "Custom template created from " + page.name) || "";

    try {
      await templateStore.createTemplate({
        name,
        description,
        category: 'user',
        nodes: page.nodes
      });
      this.showToast(`✅ Template "${name}" saved!`);
    } catch (err) {
      console.error(err);
      alert("Failed to save template: " + (err instanceof Error ? err.message : 'Unknown error'));
    }
  };

  private handleDuplicatePage = async (e: Event) => {
    e.stopPropagation();

    if (!this.currentApp || !this.contextMenuPageId) return;

    const pageId = this.contextMenuPageId;
    const page = this.pages.find(p => p.id === pageId);

    if (!page) {
      console.error('[PageManager] Page not found for duplication:', pageId);
      return;
    }
    try {
      // Use AppStore's duplicatePage method
      const duplicatedPage = await appStore.duplicatePage(this.currentApp.id, pageId);

      // Switch to the duplicated page
      this.isNewPage = true;
      this.currentPageId = duplicatedPage.id;
      this.switchToPage(duplicatedPage.id);

      this.showToast(`📋 Duplicated "${page.name}" as "${duplicatedPage.name}"`);

      // Close context menu
      this.contextMenuVisible = false;
    } catch (error) {
      console.error('[PageManager] Failed to duplicate page:', error);
      alert(error instanceof Error ? error.message : 'Failed to duplicate page');
    }
  }

  private handlePageNavigationRequest = (e: CustomEvent) => {
    const pageName = e.detail?.pageName;
    if (!pageName || !this.pages.length) return;

    console.log('[PageManager] AI requested page navigation:', pageName);

    // Find page by name (case-insensitive)
    const targetPage = this.pages.find(p => p.name.toLowerCase() === pageName.toLowerCase());

    if (targetPage) {
      this.switchToPage(targetPage.id);
      this.showToast(`📄 Opened page: ${targetPage.name}`);
    } else {
      // Try identifying by entity name in page
      // e.g. "Playlist List" -> matches page named "Playlist List"
      // If no exact match, try fuzzy
      const fuzzy = this.pages.find(p => p.name.toLowerCase().includes(pageName.toLowerCase()));
      if (fuzzy) {
        this.switchToPage(fuzzy.id);
        this.showToast(`📄 Opened page: ${fuzzy.name}`);
      } else {
        console.warn('[PageManager] Page not found for navigation request:', pageName);
      }
    }
  }

  /**
   * Build pre-built page templates with full component trees
   */
  private buildPrebuiltTemplate(pageId: string, templateId: string): PageMeta {
    // Try to get from template store
    const template = templateStore.getTemplate(templateId);

    if (template) {
      // Deep clone nodes to prevent mutation of cached template
      const nodes = JSON.parse(JSON.stringify(template.nodes));
      return {
        metaVersion: 1,
        id: pageId,
        name: this.formName.trim(),
        path: this.formPath.trim() || `/${pageId}`,
        rootId: 'root',
        nodes: nodes
      };
    }

    console.warn(`[PageManager] Template '${templateId}' not found in store. Using empty container.`);
    return {
      metaVersion: 1,
      id: pageId,
      name: this.formName.trim(),
      path: this.formPath.trim() || `/${pageId}`,
      rootId: 'root',
      nodes: [{
        id: 'root',
        type: 'container',
        props: { style: 'padding: 2rem;' },
        children: []
      }]
    };
  }


  private buildPageFromTemplate(pageId: string): PageMeta {
    console.log('[PageManager] buildPageFromTemplate - formName:', this.formName, 'formPath:', this.formPath);

    // Check if using a pre-built template
    if (this.selectedTemplate !== 'custom') {
      return this.buildPrebuiltTemplate(pageId, this.selectedTemplate);
    }

    // Otherwise, build custom template from selected sections
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
        ${this.showTemplateModal ? this.renderTemplateModal() : ''}
      `;
    }

    return html`
      <div class="page-manager">
        <div class="page-tabs">
          ${this.pages.map(page => html`
            <div
              class="page-tab ${this.currentPageId === page.id ? 'active' : ''}"
              @click=${() => this.switchToPage(page.id)}
              @contextmenu=${(e: MouseEvent) => this.handleContextMenu(e, page.id)}
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
      ${this.contextMenuVisible ? this.renderContextMenu() : ''}
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

  private getTemplateIcon(templateId: string): string {
    const icons: Record<string, string> = {
      'login': '🔐',
      'signup': '📝',
      'dashboard': '📊',
      'contact': '✉️',
      'landing': '🚀',
      'profile': '👤',
      'data-table': '📋'
    };
    return icons[templateId] || '📄';
  }

  private renderTemplatePreview(templateId: string) {
    // For custom builder, show layout diagram
    if (templateId === 'custom') {
      return html`
        <div class="template-preview">
          <h4>Preview:</h4>
          <div class="preview-layout">
            ${this.includeNav ? html`<div class="preview-section nav">Navigation</div>` : ''}
            <div class="preview-content">
              ${this.includeSidenav ? html`<div class="preview-section sidenav">Side</div>` : ''}
              <div class="preview-section main">Main Content</div>
            </div>
            ${this.includeFooter ? html`<div class="preview-section footer">Footer</div>` : ''}
          </div>
        </div>
      `;
    }

    // For predefined templates, render actual template content
    const template = templateStore.getTemplate(templateId);
    if (!template) {
      return html`
        <div class="template-preview">
          <h4>Preview:</h4>
          <div style="padding: 20px; text-align: center; color: #9ca3af;">
            Template preview not available
          </div>
        </div>
      `;
    }

    // Use runtime renderer for accurate preview (with scaling)
    return html`
      <div class="template-preview">
        <h4>Preview: <span style="font-weight: 400; color: #6b7280; font-size: 10px;">${template.description}</span></h4>
        <div class="template-preview-content" style="transform: scale(0.4); transform-origin: top left; width: 250%; height: 250%;">
          ${this.renderTemplateWithRuntimeRenderer(template)}
        </div>
      </div>
    `;
  }

  private renderTemplateWithRuntimeRenderer(template: PageTemplate) {
    // Create a temporary container for runtime renderer
    const tempPage: PageMeta = {
      id: template.id,
      name: template.name,
      path: `/${template.id}`,
      rootId: 'root',
      nodes: template.nodes
    };

    // Return a ref callback that renders using runtime renderer
    return html`
      <div ${(el: any) => {
        if (el && el instanceof HTMLElement) {
          try {
            const rendered = renderPageTemplate(tempPage, {});
            render(rendered, el);
          } catch (err) {
            console.error('Preview render error:', err);
            el.innerHTML = '<div style="color: red; padding: 10px;">Preview error</div>';
          }
        }
      }}></div>
    `;
  }

  private renderTemplateModal() {
    const templates = templateStore.getAllTemplates();

    // Add custom builder option manually as it's not a server-side template
    const allOptions = [
      ...templates.map(t => ({
        id: t.id,
        icon: this.getTemplateIcon(t.id),
        name: t.name,
        description: t.description
      })),
      {
        id: 'custom',
        icon: '🎨',
        name: 'Custom Builder',
        description: 'Build from scratch with sections'
      }
    ];

    return html`
      <div class="modal-overlay" @click=${this.handleCloseModal}>
        <div class="modal modal-wide" @click=${(e: Event) => e.stopPropagation()}>
          <div class="modal-header">
            <h3>🎨 Choose Template - Step 2</h3>
            <div class="header-actions">
              <button type="button" class="btn btn-primary" @click=${this.handleSubmitCreate}>
                ✓ Create Page
              </button>
              <button class="modal-close" @click=${this.handleCloseModal}>×</button>
            </div>
          </div>

          <div class="modal-body">
            <p class="template-help">Choose a ready-to-use template or build custom:</p>

            <div class="template-container">
              <!-- Left: Template Gallery -->
              <div class="template-left">
                <div class="template-gallery">
                  ${allOptions.map(template => html`
                    <div
                      class="template-card ${this.selectedTemplate === template.id ? 'selected' : ''}"
                      @click=${() => this.selectedTemplate = template.id}
                    >
                      <div class="template-card-icon">${template.icon}</div>
                      <h4 class="template-card-title">${template.name}</h4>
                      <p class="template-card-desc">${template.description}</p>
                      ${this.selectedTemplate === template.id ? html`<div class="template-card-check">✓</div>` : ''}
                    </div>
                  `)}
                </div>
              </div>

              <!-- Right: Custom Builder Options (only for 'custom') or Preview (for all templates) -->
              <div class="template-right">
                ${this.selectedTemplate === 'custom' ? html`
                  <div class="custom-builder-section">
                    <h4 style="margin: 0 0 6px 0; font-size: 12px; font-weight: 600; color: #374151;">Select sections to include:</h4>
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
                  </div>

                  <div class="template-preview">
                    <h4>Preview:</h4>
                    <div class="preview-layout">
                      ${this.includeNav ? html`<div class="preview-section nav">Navigation</div>` : ''}
                      <div class="preview-content">
                        ${this.includeSidenav ? html`<div class="preview-section sidenav">Side</div>` : ''}
                        <div class="preview-section main">Main Content</div>
                      </div>
                      ${this.includeFooter ? html`<div class="preview-section footer">Footer</div>` : ''}
                    </div>
                  </div>
                ` : this.selectedTemplate ? this.renderTemplatePreview(this.selectedTemplate) : ''}
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

  private renderContextMenu() {
    if (!this.contextMenuPageId) return html``;

    const page = this.pages.find(p => p.id === this.contextMenuPageId);
    if (!page) return html``;

    return html`
      <div 
        class="context-menu" 
        style="left: ${this.contextMenuX}px; top: ${this.contextMenuY}px;"
      >
        <div class="context-menu-item" @click=${this.handleSaveAsTemplate}>
          <span class="context-menu-icon">💾</span>
          <span class="context-menu-label">Save as Template</span>
        </div>
        <div class="context-menu-item" @click=${this.handleDuplicatePage}>
          <span class="context-menu-icon">📋</span>
          <span class="context-menu-label">Duplicate</span>
        </div>
        <div 
          class="context-menu-item danger" 
          @click=${(e: Event) => {
        this.contextMenuVisible = false;
        this.handleDeletePage(page.id, page.name, e);
      }}
        >
          <span class="context-menu-icon">🗑️</span>
          <span class="context-menu-label">Delete</span>
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
