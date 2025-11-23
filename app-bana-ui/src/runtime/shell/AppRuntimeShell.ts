import { LitElement, html, unsafeCSS } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { AppRuntimeState } from '../../models/runtime-state.js';
import { PageMeta } from '../../models/metadata.js';
import { renderPageTemplate } from '../renderer/Renderer.js';
import shellStyles from './AppRuntimeShell.css?inline';
import { ensureCoreRegistered } from '../../core/registry.js';

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
    // Initialization happens once when runtimeState is first available
    if (this.runtimeState && !this._initialized) {
      this.initializeRuntime();
    }
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

    // Set initial page
    const initialPageId = this.runtimeState.currentPageId || this.runtimeState.pages[0]?.id;
    if (initialPageId) this.navigateToPage(initialPageId); else this.error = 'No pages found in app';
    this._initialized = true;
  }

  private navigateToPage(pageId: string) {
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
    globalThis.history.pushState({}, '', url.toString());

    // No imperative re-render; Lit will update reactively.
  }

  // Remove direct DOM manipulation. Page content is rendered via Lit's template.

  // Removed duplicate render() method. Only one render() should exist below.

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
            ${this.currentPage ? renderPageTemplate(this.currentPage) : html`<div>Loading...</div>`}
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
    ${this.currentPage ? renderPageTemplate(this.currentPage) : html`<div>Loading...</div>`}
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
