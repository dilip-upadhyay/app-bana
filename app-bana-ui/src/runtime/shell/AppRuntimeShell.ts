import { LitElement, html, unsafeCSS } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { AppRuntimeState } from '../../models/runtime-state.js';
import { PageMeta } from '../../models/metadata.js';
import { renderPageTemplate } from '../renderer/Renderer.js';
import shellStyles from './AppRuntimeShell.css?inline';
import { ensureCoreRegistered } from '../../core/registry.js';
// Explicitly import form components to ensure they are registered in the bundle
import '../../components/InputElement';
import '../../components/SelectElement';
import '../../components/TextareaElement';
import '../../components/ButtonElement';
import '../../components/FormContainer';

/**
 * AppRuntimeShell - Main container for app preview/production runtime
 * 
 * Provides:
 * - App header with name and navigation
 * - Page tabs for switching between pages
 * - Current page rendering area
 * - Back to Studio button (in preview mode)
 */
@customElement('app-runtime-shell')
export class AppRuntimeShell extends LitElement {
  static readonly styles = unsafeCSS(shellStyles);

  @property({ type: Object })
  runtimeState: AppRuntimeState | null = null;

  @state()
  private currentPageId: string = '';

  @state()
  private currentPage: PageMeta | null = null;

  @state()
  private error: string | null = null;

  // Guard to avoid scheduling a second update inside updated()
  private _initialized: boolean = false;

  async connectedCallback() {
    super.connectedCallback();
    await ensureCoreRegistered();

    // Listen for custom navigation events from components (e.g. FormContainer)
    this.addEventListener('navigate', ((e: CustomEvent) => {
      const fullPath = e.detail.path;
      if (fullPath) {
        // Parse path and query
        const [path, queryStr] = fullPath.split('?');

        // Resolve path to page ID
        const page = this.runtimeState?.pages.find(p => p.path === path);

        if (page) {
          // Parse query params to object
          const queryParams: Record<string, string> = {};
          if (queryStr) {
            const search = new URLSearchParams(queryStr);
            search.forEach((v, k) => queryParams[k] = v);
          }
          this.navigateToPage(page.id, queryParams);
        } else {
          console.error('Page not found for path:', path);
        }
      }
    }) as EventListener);

    // Expose navigate globally for inline onclick handlers (e.g. "navigate('/apply')")
    (window as any).navigate = (path: string) => {
      this.dispatchEvent(new CustomEvent('navigate', {
        bubbles: true,
        composed: true,
        detail: { path }
      }));
    };

    // Initialization happens once when runtimeState is first available
    if (this.runtimeState && !this._initialized) {
      this.initializeRuntime();
    }
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    // Cleanup global
    if ((window as any).navigate) delete (window as any).navigate;
  }

  willUpdate(changed: Map<string, any>) {
    // If runtimeState is set later (after element connected) run initialization once
    if (changed.has('runtimeState') && this.runtimeState && !this._initialized) {
      this.initializeRuntime();
    }
  }

  private initializeRuntime() {
    if (!this.runtimeState) {
      this.error = 'No runtime state provided';
      return;
    }

    // Set initial page from URL if present
    const url = new URL(globalThis.location.href);
    const pageId = url.searchParams.get('pageId');

    if (pageId) {
      this.navigateToPage(pageId);
    } else {
      const initialPageId = this.runtimeState.currentPageId || this.runtimeState.pages[0]?.id;
      if (initialPageId) this.navigateToPage(initialPageId); else this.error = 'No pages found in app';
    }
    this._initialized = true;
  }

  private navigateToPage(pageId: string, queryParams?: Record<string, string>) {
    const page = this.runtimeState?.pages.find(p => p.id === pageId);

    if (!page) {
      this.error = `Page not found: ${pageId}`;
      return;
    }

    this.currentPageId = pageId;
    this.currentPage = page;
    this.error = null;

    // Update URL without reload
    const url = new URL(globalThis.location.href);
    url.searchParams.set('pageId', pageId);

    // Merge new query params
    if (queryParams) {
      Object.entries(queryParams).forEach(([k, v]) => url.searchParams.set(k, v));
    }

    globalThis.history.pushState({}, '', url.toString());

    // Request update to re-render with new context
    this.requestUpdate();
  }

  private getContext(): any {
    const params = new URLSearchParams(window.location.search);
    const query: Record<string, string> = {};
    params.forEach((v, k) => query[k] = v);
    return { query, user: { name: 'Guest' } }; // Add mock user context if needed
  }

  private handleBackToStudio() {
    // Navigate back to studio with current app
    const appId = this.runtimeState?.app.id;
    if (appId) {
      globalThis.location.href = `/studio.html?appId=${appId}`;
    } else {
      globalThis.location.href = '/studio.html';
    }
  }

  private handlePageTabClick(pageId: string) {
    this.navigateToPage(pageId);
  }

  render() {
    if (!this.runtimeState) {
      return html`
        <div class="runtime-shell error">
          <div class="error-message">
            <h2>No App Loaded</h2>
            <p>No runtime state provided. Please launch preview from the studio.</p>
            <button @click=${this.handleBackToStudio}>Go to Studio</button>
          </div>
        </div>
      `;
    }

    const { app, pages, mode } = this.runtimeState;
    const isPreviewMode = mode === 'preview' || mode === 'development';
    const context = this.getContext();

    // In preview mode, show page tabs but no AppBana header/branding
    if (isPreviewMode) {
      return html`
        <div class="runtime-shell preview-only">
          <!-- Page Navigation Tabs (Part of the app) -->
          ${pages.length > 1 ? html`
            <nav class="page-tabs">
              ${pages.map(page => html`
                <button
                  class="page-tab ${page.id === this.currentPageId ? 'active' : ''}"
                  @click=${() => this.handlePageTabClick(page.id)}
                >
                  ${page.name}
                </button>
              `)}
            </nav>
          ` : ''}

          <!-- Page Content -->
          <main class="runtime-content full-page">
            ${this.error ? html`
              <div class="error-message">
                <h3>Error</h3>
                <p>${this.error}</p>
              </div>
            ` : ''}
            ${this.currentPage ? renderPageTemplate(this.currentPage, context) : html`<div>Loading...</div>`}
          </main>
        </div>
      `;
    }

    // Production mode shows full navigation
    return html`
      <div class="runtime-shell">
        <!-- Header -->
        <header class="runtime-header">
          <div class="header-left">
            <h1 class="app-title">${app.name}</h1>
          </div>
        </header>

        <!-- Page Navigation Tabs -->
        ${pages.length > 1 ? html`
          <nav class="page-tabs">
            ${pages.map(page => html`
              <button
                class="page-tab ${page.id === this.currentPageId ? 'active' : ''}"
                @click=${() => this.handlePageTabClick(page.id)}
              >
                ${page.name}
              </button>
            `)}
          </nav>
        ` : ''}

        <!-- Page Content Area -->
        <main class="runtime-content">
          ${this.error ? html`
            <div class="error-message">
              <h3>Error</h3>
              <p>${this.error}</p>
            </div>
          ` : ''}
    ${this.currentPage ? renderPageTemplate(this.currentPage, context) : html`<div>Loading...</div>`}
        </main>
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'app-runtime-shell': AppRuntimeShell;
  }
}
