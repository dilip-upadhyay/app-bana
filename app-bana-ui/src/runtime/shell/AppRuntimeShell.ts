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

  @state()
  private showThemeModal: boolean = false;
  @state()
  private runtimeTheme: any = null;

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
    this.applyTheme();
    this._initialized = true;
  }

  private applyTheme() {
    if (!this.runtimeState?.app.theme) return;
    const theme = this.runtimeState.app.theme;

    if (theme.primaryColor) {
      this.style.setProperty('--color-brand', theme.primaryColor);
      // Simple darkening for accent - in real app use color manipulation lib
      this.style.setProperty('--color-brand-accent', theme.primaryColor);
      this.style.setProperty('--color-focus-ring', theme.primaryColor);
    }

    if (theme.secondaryColor) {
      this.style.setProperty('--color-text-secondary', theme.secondaryColor);
      this.style.setProperty('--color-border', theme.secondaryColor + '40');
    }

    if (theme.surfaceColor) {
      this.style.setProperty('--color-surface', theme.surfaceColor);
      // For simple theming, we'll make bg same or slightly darker than surface
      this.style.setProperty('--color-bg', theme.surfaceColor);
      this.style.setProperty('--tbl-container-bg', theme.surfaceColor);
    }

    if (theme.surfaceAltColor) {
      this.style.setProperty('--color-surface-alt', theme.surfaceAltColor);
      this.style.setProperty('--tbl-header-bg', theme.surfaceAltColor);
    } else if (theme.surfaceColor) {
      // Fallback: use surface color if alt not provided
      this.style.setProperty('--color-surface-alt', theme.surfaceColor);
    }

    if (theme.textColor) {
      this.style.setProperty('--color-text', theme.textColor);
    }

    if (theme.fontFamily) {
      this.style.setProperty('--font-sans', theme.fontFamily);
    }

    // Apply Custom CSS if present
    // Note: In Shadow DOM, this needs to be a style tag, or constructable stylesheet
    // For now, we trust the host styles cascade or we might need a <style> tag in render
  }

  private openThemeModal() {
    this.showThemeModal = true;
  }

  private closeThemeModal() {
    this.showThemeModal = false;
  }

  private applyRuntimePreset(preset: string) {
    let theme = {};
    switch (preset) {
      case 'modern-blue':
        theme = { primaryColor: '#2563eb', secondaryColor: '#64748b', surfaceColor: '#ffffff', textColor: '#1e293b', fontFamily: 'Inter' };
        break;
      case 'forest-green':
        theme = { primaryColor: '#059669', secondaryColor: '#3f6212', surfaceColor: '#f0fdf4', textColor: '#14532d', fontFamily: 'Inter' };
        break;
      case 'crimson-red':
        theme = { primaryColor: '#dc2626', secondaryColor: '#7f1d1d', surfaceColor: '#fef2f2', textColor: '#450a0a', fontFamily: 'Inter' };
        break;
      case 'midnight-violet':
        theme = { primaryColor: '#8b5cf6', secondaryColor: '#a78bfa', surfaceColor: '#1e1b4b', textColor: '#f5f3ff', fontFamily: 'Inter' };
        break;
      case 'peach-fuzz':
        theme = { primaryColor: '#ffbe98', secondaryColor: '#d99f7e', surfaceColor: '#fff9f5', textColor: '#4a3b32', fontFamily: 'Outfit' };
        break;
      case 'cyber-lime':
        theme = { primaryColor: '#ccff00', secondaryColor: '#88a80d', surfaceColor: '#000000', textColor: '#ccff00', fontFamily: 'Courier New' };
        break;
      case 'blue-nova':
        theme = { primaryColor: '#5b7c99', secondaryColor: '#8f9ead', surfaceColor: '#f4f7f6', textColor: '#2c3e50', fontFamily: 'Inter' };
        break;
      case 'earthy-greens':
        theme = { primaryColor: '#556b2f', secondaryColor: '#8fbc8f', surfaceColor: '#f5f5dc', textColor: '#333333', fontFamily: 'Georgia' };
        break;
      case 'luxury-dark':
        theme = { primaryColor: '#cfb53b', secondaryColor: '#a89f91', surfaceColor: '#121212', surfaceAltColor: '#1e1e1e', textColor: '#e0e0e0', fontFamily: 'Playfair Display' };
        break;
    }
    // Deep merge for runtime
    if (this.runtimeState?.app) {
      this.runtimeState.app.theme = { ...this.runtimeState.app.theme, ...theme };
      this.applyTheme();
      this.requestUpdate();
    }
  }

  private async generateRuntimeTheme() {
    const input = this.shadowRoot?.getElementById('runtimeMagicInput') as HTMLInputElement;
    if (!input || !input.value.trim()) return;

    const btn = input.nextElementSibling as HTMLButtonElement;
    const originalText = btn.textContent;
    btn.textContent = '...';
    btn.disabled = true;

    try {
      const res = await fetch('/api/ai/theme-generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ description: input.value })
      });
      if (!res.ok) throw new Error('Failed');
      const data = await res.json();

      const theme: any = {};
      if (data.colors) {
        theme.primaryColor = data.colors.brand;
        theme.secondaryColor = data.colors.textSecondary;
      }
      if (data.radius?.sm) theme.borderRadius = data.radius.sm;

      // Apply
      if (this.runtimeState?.app) {
        this.runtimeState.app.theme = { ...this.runtimeState.app.theme, ...theme };
        this.applyTheme();
        this.requestUpdate();
      }
      input.value = '';
    } catch (e) {
      console.error(e);
      alert('Failed to generate theme');
    } finally {
      btn.textContent = originalText;
      btn.disabled = false;
    }
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
          <div class="header-right" style="margin-left:auto;">
             <button @click=${this.openThemeModal} style="background:transparent;border:none;cursor:pointer;font-size:1.2rem;" title="Change Theme">🎨</button>
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
        
        ${this.showThemeModal ? html`
          <div class="modal-overlay" style="position:fixed;inset:0;background:rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;z-index:9999;" @click=${this.closeThemeModal}>
            <div class="modal" style="background:var(--color-surface, white);color:var(--color-text, #1e293b);padding:24px;border-radius:12px;width:400px;max-width:90%;box-shadow:0 10px 25px rgba(0,0,0,0.2);border:1px solid var(--color-border, #e2e8f0);" @click=${(e: Event) => e.stopPropagation()}>
               <div style="display:flex;justify-content:space-between;margin-bottom:16px;">
                 <h3 style="margin:0;font-size:1.1rem;color:var(--color-text, #1e293b);">Change Theme</h3>
                 <button @click=${this.closeThemeModal} style="background:none;border:none;cursor:pointer;font-size:1.2rem;color:var(--color-text, #1e293b);">×</button>
               </div>
               
               <div style="margin-bottom:16px;">
                 <label style="display:block;font-size:0.85rem;color:#64748b;margin-bottom:8px;">Presets</label>
                 <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;">
                   <button @click=${() => this.applyRuntimePreset('modern-blue')} style="padding:8px;border:1px solid #e2e8f0;border-radius:6px;background:white;cursor:pointer;">🔵 Blue</button>
                   <button @click=${() => this.applyRuntimePreset('forest-green')} style="padding:8px;border:1px solid #e2e8f0;border-radius:6px;background:white;cursor:pointer;">🌲 Green</button>
                   <button @click=${() => this.applyRuntimePreset('crimson-red')} style="padding:8px;border:1px solid #e2e8f0;border-radius:6px;background:white;cursor:pointer;">🔴 Red</button>
                   <button @click=${() => this.applyRuntimePreset('midnight-violet')} style="padding:8px;border:1px solid #e2e8f0;border-radius:6px;background:white;cursor:pointer;">🌙 Violet</button>
                   
                   <button @click=${() => this.applyRuntimePreset('peach-fuzz')} style="padding:8px;border:1px solid #e2e8f0;border-radius:6px;background:white;cursor:pointer;">🍑 Peach</button>
                   <button @click=${() => this.applyRuntimePreset('cyber-lime')} style="padding:8px;border:1px solid #e2e8f0;border-radius:6px;background:black;color:#ccff00;cursor:pointer;border-color:#ccff00;">⚡ Cyber</button>
                   <button @click=${() => this.applyRuntimePreset('blue-nova')} style="padding:8px;border:1px solid #e2e8f0;border-radius:6px;background:white;cursor:pointer;">🌌 Nova</button>
                   <button @click=${() => this.applyRuntimePreset('earthy-greens')} style="padding:8px;border:1px solid #e2e8f0;border-radius:6px;background:#f5f5dc;cursor:pointer;">🌿 Earth</button>
                   <button @click=${() => this.applyRuntimePreset('luxury-dark')} style="padding:8px;border:1px solid #e2e8f0;border-radius:6px;background:#121212;color:#cfb53b;cursor:pointer;border-color:#cfb53b;">👑 Lux</button>
                  </div>
                </div>
               
               <div style="margin-bottom:16px;border-top:1px solid #e2e8f0;padding-top:16px;">
                 <label style="display:block;font-size:0.85rem;color:#64748b;margin-bottom:8px;">✨ Magic Theme (AI)</label>
                 <div style="display:flex;gap:8px;">
                   <input type="text" id="runtimeMagicInput" placeholder="e.g. Retro vaporwave..." style="flex:1;padding:8px;border:1px solid #cbd5e1;border-radius:6px;">
                   <button @click=${this.generateRuntimeTheme} style="padding:8px 12px;background:linear-gradient(135deg, #6366f1 0%, #a855f7 100%);color:white;border:none;border-radius:6px;cursor:pointer;">Generate</button>
                 </div>
               </div>

               <p style="font-size:0.8rem;color:#94a3b8;font-style:italic;">
                 Note: Runtime theme changes are temporary for this session.
               </p>
            </div>
          </div>
        `: ''}
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'app-runtime-shell': AppRuntimeShell;
  }
}
