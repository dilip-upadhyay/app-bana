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
import demoPage from './demo/demo-page.json';
import { ensureCoreRegistered } from './core/registry';
import { renderPage } from './runtime/renderer/Renderer';
import { PageMeta } from './models/metadata';
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
    const host = this.renderRoot.querySelector('#app-runtime') as HTMLElement | null;
    if (!host) return;

    try {
      // Decode runtime state
      const state = JSON.parse(atob(stateParam));
      console.log('[AppRoot] Loading app runtime with state:', state);

      // Load app from localStorage
      const appJson = localStorage.getItem(`appbana.apps.${state.appId}`);
      if (!appJson) {
        host.innerHTML = `<div style="padding: 2rem; color: red;">App not found: ${state.appId}</div>`;
        return;
      }

      const app = JSON.parse(appJson);
      console.log('[AppRoot] Loaded app:', app.name, 'with', app.pages?.length || 0, 'page IDs');
      console.log('[AppRoot] Page IDs:', app.pages);

      // Load all pages from storage (they're stored separately)
      const pages: PageMeta[] = [];
      for (const pageId of (app.pages || [])) {
        const pageKey = `appbana.apps.${state.appId}.page.${pageId}`;
        const pageJson = localStorage.getItem(pageKey);
        if (pageJson) {
          const page = JSON.parse(pageJson);
          pages.push(page);
          console.log('[AppRoot] Loaded page:', page.name, '(ID:', page.id, ')');
        }
      }

      // Find the target page
      const pageId = state.pageId || app.defaultPage || pages[0]?.id;
      console.log('[AppRoot] Looking for page ID:', pageId);
      
      const targetPage = pages.find((p: PageMeta) => p.id === pageId);
      console.log('[AppRoot] Found page:', targetPage);

      if (!targetPage) {
        const pageList = pages.map((p: PageMeta) => `${p.name || 'Unnamed'} (ID: ${p.id})`).join('<br>') || 'No pages found';
        host.innerHTML = `
          <div style="padding: 2rem; color: red;">
            <h3>Page not found: ${pageId}</h3>
            <p><strong>Available pages in app:</strong></p>
            <div style="padding-left: 1rem; margin-top: 0.5rem;">${pageList}</div>
          </div>
        `;
        return;
      }

      // Render the page with full context
      console.log('[AppRoot] Rendering page:', targetPage.name);
      
      // Create navigation links with updated state
      const navLinks = pages.map((p: PageMeta) => {
        const newState = { ...state, pageId: p.id };
        const newStateParam = btoa(JSON.stringify(newState));
        return `<a href="?state=${newStateParam}" style="color: ${p.id === pageId ? '#60a5fa' : '#9ca3af'}; text-decoration: none; padding: 0.5rem 1rem; border-radius: 4px; ${p.id === pageId ? 'background: rgba(96, 165, 250, 0.1);' : ''}">${p.name}</a>`;
      }).join('');
      
      // Add app header with navigation
      host.innerHTML = `
        <div style="background: #1f2937; color: white; padding: 1rem 2rem; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #374151;">
          <div style="display: flex; align-items: center; gap: 1rem;">
            <strong style="font-size: 1.2rem;">📱 ${app.name}</strong>
            ${state.mode === 'preview' ? '<span style="padding: 0.25rem 0.5rem; background: #059669; border-radius: 4px; font-size: 0.75rem; font-weight: 600;">PREVIEW</span>' : ''}
          </div>
          <div style="display: flex; gap: 0.5rem;">
            ${navLinks}
          </div>
        </div>
        <div id="page-content" style="flex: 1; overflow: auto;"></div>
      `;

      const pageContent = host.querySelector('#page-content') as HTMLElement;
      if (pageContent) {
        renderPage(targetPage, pageContent);
      }
    } catch (error) {
      console.error('[AppRoot] Error loading app runtime:', error);
      host.innerHTML = `<div style="padding: 2rem; color: red;">Error loading app: ${(error as Error).message}</div>`;
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
