import { LitElement, html } from 'lit';
import { customElement } from 'lit/decorators.js';
import './schema-builder';
import './app-renderer';
import './components/StudioWelcome';
import './components/entity-explorer';
import './components/app-sidebar';
import './components/component-gallery';
import './components/ButtonElement';
import './components/ContainerElement';
import './components/TextElement';
import './components/UnknownElement';
import './runtime/shell/AppRuntimeShell';
import demoPage from './demo/demo-page.json';
import { ensureCoreRegistered } from './core/registry';
import { renderPage } from './runtime/renderer/Renderer';
import { PageMeta } from './models/metadata';
import { decodeRuntimeState } from './models/runtime-state';
import type { AppRuntimeState } from './models/runtime-state';
import './styles/theme.css';

@customElement('app-root')
export class AppRoot extends LitElement {
  async firstUpdated() {
    const path = window.location.pathname;
    const hash = window.location.hash;
    const searchParams = new URLSearchParams(window.location.search);
    
    // Check for runtime state parameter (new preview mode)
    const stateParam = searchParams.get('state');
    if (stateParam) {
      await this.loadAppRuntime(stateParam);
      return;
    }
    
    // Legacy: If hash-based routing is present, load page from app store
    if (hash && hash.startsWith('#/')) {
      await this.loadPageByHash(hash.substring(1)); // Remove the '#' prefix
      return;
    }
    
    if (path.includes('/studio')) {
      await ensureCoreRegistered();
      if (path.includes('/studio') && !path.includes('/studio/builder')) {
        const host = this.renderRoot.querySelector('#studio-root') as HTMLElement | null;
        if (host) {
          renderPage(demoPage as PageMeta, host);
        }
      }
    }
  }
  
  /**
   * Load and render app runtime with full context
   * This is the NEW proper way to preview/run apps
   */
  private async loadAppRuntime(stateParam: string) {
    await ensureCoreRegistered();
    const host = this.renderRoot.querySelector('#app-runtime');
    if (!host) return;

    try {
      // Decode compact runtime state from URL parameter
      const compactState = decodeRuntimeState(stateParam);
      console.log('[AppRoot] Loading app runtime with compact state:', compactState);

      // Load app WITH FULL PAGES from backend API
      const response = await fetch(`http://localhost:8080/apps/${compactState.appId}/full`);
      if (!response.ok) {
        throw new Error(`Failed to load app: ${response.statusText}`);
      }

      const app = await response.json();
      console.log('[AppRoot] Loaded app:', app.name, 'with', app.pages?.length || 0, 'pages');

      // Build full runtime state
      const currentPageId = compactState.pageId || app.defaultPage || app.pages?.[0]?.id || '';
      const fullRuntimeState: AppRuntimeState = {
        app,
        pages: app.pages || [],
        currentPageId,
        navigation: {
          history: [currentPageId],
          canGoBack: false,
          canGoForward: false
        },
        mode: compactState.mode || 'preview',
        context: {
          timestamp: Date.now(),
          studioUrl: '/studio.html'
        }
      };

      // Create and mount the AppRuntimeShell component
      const shell = document.createElement('app-runtime-shell');
      (shell as any).runtimeState = fullRuntimeState;
      
      // Clear existing content and mount shell
      host.innerHTML = '';
      host.appendChild(shell);

      console.log('[AppRoot] App runtime shell mounted successfully');
    } catch (error) {
      console.error('[AppRoot] Error loading app runtime:', error);
      host.innerHTML = `
        <div style="padding: 2rem; color: red; font-family: sans-serif;">
          <h2>Error Loading App</h2>
          <p>${(error as Error).message}</p>
          <button onclick="globalThis.location.href='/studio.html'" style="margin-top: 1rem; padding: 0.5rem 1rem; background: #0d6efd; color: white; border: none; border-radius: 4px; cursor: pointer;">
            Go to Studio
          </button>
        </div>
      `;
    }
  }

  /**
   * Legacy: Load page by hash (deprecated, use loadAppRuntime instead)
   */
  private async loadPageByHash(path: string) {
    await ensureCoreRegistered();
    const host = this.renderRoot.querySelector('#app-content') as HTMLElement | null;
    if (!host) return;
    
    // Load page metadata from localStorage (AppStore uses hierarchical structure)
    const appsListJson = localStorage.getItem('appbana.apps.list');
    if (!appsListJson) {
      host.innerHTML = '<div style="padding: 2rem; color: red;">No apps found. Please create an app in Studio first.</div>';
      return;
    }
    
    const appIds: string[] = JSON.parse(appsListJson);
    let targetPage: PageMeta | null = null;
    const allPages: {appId: string, pageName: string, pagePath: string}[] = [];
    
    // Find the page with matching path across all apps
    for (const appId of appIds) {
      const appJson = localStorage.getItem(`appbana.apps.${appId}`);
      if (!appJson) continue;
      
      const app = JSON.parse(appJson);
      console.log('[AppRoot] Checking app:', appId, 'Pages:', app.pages?.length || 0);
      
      if (app.pages) {
        // Collect all pages for debugging
        for (const p of app.pages) {
          allPages.push({ appId, pageName: p.name, pagePath: p.path });
        }
        
        targetPage = app.pages.find((p: PageMeta) => p.path === path);
        if (targetPage) {
          console.log('[AppRoot] Found matching page:', targetPage.name);
          break;
        }
      }
    }
    
    if (targetPage) {
      console.log('[AppRoot] Rendering page:', targetPage.name, 'at path:', path);
      renderPage(targetPage, host);
    } else {
      const pagesList = allPages.map(p => `${p.pageName} (${p.pagePath})`).join('<br>');
      host.innerHTML = `
        <div style="padding: 2rem; color: red;">
          <h3>Page not found: ${path}</h3>
          <p><strong>Available pages:</strong></p>
          <div style="padding-left: 1rem;">${pagesList || 'No pages found'}</div>
        </div>
      `;
    }
  }
  
  connectedCallback(): void {
    super.connectedCallback();
    window.addEventListener('popstate', this._onPop);
    window.addEventListener('hashchange', this._onHashChange);
  }
  disconnectedCallback(): void {
    super.disconnectedCallback();
    window.removeEventListener('popstate', this._onPop);
    window.removeEventListener('hashchange', this._onHashChange);
  }
  private _onPop = () => { this.requestUpdate(); };
  private _onHashChange = () => { 
    this.requestUpdate();
    const hash = window.location.hash;
    if (hash && hash.startsWith('#/')) {
      this.loadPageByHash(hash.substring(1));
    }
  };

  render() {
    const path = window.location.pathname;
    const hash = window.location.hash;
    const searchParams = new URLSearchParams(window.location.search);
    const stateParam = searchParams.get('state');
    
    // New runtime mode - full app context with navigation
    if (stateParam) {
      return html`<div id="app-runtime" style="width: 100%; height: 100vh; display: flex; flex-direction: column;"></div>`;
    }
    
    // Legacy: Hash-based routing for preview pages
    if (hash && hash.startsWith('#/')) {
      return html`<div id="app-content" style="width: 100%; height: 100%;"></div>`;
    }
    
    if (path.includes('/builder') && !path.includes('/studio/builder')) {
      return html`<schema-builder></schema-builder>`;
    } else if (path.includes('/explorer')) {
      return html`<entity-explorer></entity-explorer>`;
    } else if (path.includes('/app')) {
      return html`<app-renderer></app-renderer>`;
    } else if (path.includes('/studio/builder')) {
      return html`<studio-builder-shell></studio-builder-shell>`;
    } else if (path.includes('/studio')) {
      return html`<div id="studio-root"></div>`;
    } else if (path.includes('/gallery')) {
      return html`<component-gallery></component-gallery>`;
    }
    return html`
      <h1>Welcome to AppBana Studio</h1>
      <p>
        <a href="/builder">Schema Builder</a> |
        <a href="/explorer">Entity Explorer</a> |
        <a href="/app">App Renderer</a>
      </p>
      <hr />
      <h2>BaseElement Test Component:</h2>
      <studio-welcome name="AppBana Team"></studio-welcome>
    `;
  }
}
