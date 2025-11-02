/**
 * App Store - Manages applications and their pages
 * Persists to localStorage with hierarchical structure
 */

import type { AppMeta, AppWithPages, CreateAppRequest, UpdateAppRequest, AppListItem } from '../../models/app-metadata';
import type { PageMeta } from '../../models/metadata';

const STORAGE_KEY_PREFIX = 'appbana.apps.';
const APPS_LIST_KEY = 'appbana.apps.list';
const CURRENT_APP_KEY = 'appbana.current.app';

export class AppStore {
  private apps: Map<string, AppMeta> = new Map();
  private currentAppId: string | null = null;
  private listeners = new Set<() => void>();

  constructor() {
    this.loadApps();
  }

  // ==================== Lifecycle ====================

  /**
   * Load all apps from localStorage
   */
  private loadApps() {
    try {
      const appsList = localStorage.getItem(APPS_LIST_KEY);
      if (appsList) {
        const appIds: string[] = JSON.parse(appsList);
        appIds.forEach(id => {
          const appData = localStorage.getItem(`${STORAGE_KEY_PREFIX}${id}`);
          if (appData) {
            const app: AppMeta = JSON.parse(appData);
            this.apps.set(id, app);
          }
        });
      }

      // Load current app ID
      const currentId = localStorage.getItem(CURRENT_APP_KEY);
      if (currentId && this.apps.has(currentId)) {
        this.currentAppId = currentId;
      }
    } catch (error) {
      console.error('[AppStore] Failed to load apps:', error);
    }
  }

  /**
   * Save apps list to localStorage
   */
  private saveAppsList() {
    const appIds = Array.from(this.apps.keys());
    localStorage.setItem(APPS_LIST_KEY, JSON.stringify(appIds));
  }

  /**
   * Save a single app to localStorage
   */
  private saveApp(app: AppMeta) {
    localStorage.setItem(`${STORAGE_KEY_PREFIX}${app.id}`, JSON.stringify(app));
    this.saveAppsList();
  }

  /**
   * Delete app from localStorage
   */
  private deleteAppFromStorage(appId: string) {
    localStorage.removeItem(`${STORAGE_KEY_PREFIX}${appId}`);
    this.saveAppsList();
  }

  // ==================== Events ====================

  onChange(fn: () => void): () => void {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  subscribe(fn: () => void): () => void {
    return this.onChange(fn);
  }

  private notify() {
    this.listeners.forEach(fn => fn());
  }

  // ==================== App Management ====================

  /**
   * Create a new app
   */
  createApp(request: CreateAppRequest): AppMeta {
    const id = this.generateAppId(request.name);
    const now = Date.now();

    const app: AppMeta = {
      id,
      name: request.name,
      description: request.description,
      version: '1.0.0',
      created: now,
      updated: now,
      pages: [],
      theme: {
        primaryColor: '#2563eb',
        secondaryColor: '#64748b',
        fontFamily: 'system-ui, -apple-system, sans-serif',
        darkMode: false,
      },
      routes: {
        baseUrl: `/${id}`,
        mode: 'hash',
      },
    };

    // Save app (no initial page created)
    this.apps.set(id, app);
    this.saveApp(app);

    // Set as current app
    this.currentAppId = app.id;
    localStorage.setItem(CURRENT_APP_KEY, app.id);

    this.notify();
    return app;
  }

  /**
   * Update an existing app
   */
  updateApp(appId: string, updates: UpdateAppRequest): AppMeta {
    const app = this.apps.get(appId);
    if (!app) {
      throw new Error(`App not found: ${appId}`);
    }

    const updatedApp: AppMeta = {
      ...app,
      ...updates,
      theme: updates.theme ? { ...app.theme, ...updates.theme } : app.theme,
      routes: updates.routes ? { ...app.routes, ...updates.routes } : app.routes,
      updated: Date.now(),
    };

    this.apps.set(appId, updatedApp);
    this.saveApp(updatedApp);
    this.notify();
    return updatedApp;
  }

  /**
   * Delete an app and all its pages
   */
  deleteApp(appId: string): void {
    const app = this.apps.get(appId);
    if (!app) {
      throw new Error(`App not found: ${appId}`);
    }

    // Delete all pages
    app.pages.forEach((pageId: string) => {
      this.deletePageFromStorage(appId, pageId);
    });

    // Delete app
    this.apps.delete(appId);
    this.deleteAppFromStorage(appId);

    // If this was the current app, select another
    if (this.currentAppId === appId) {
      const nextApp = this.apps.values().next().value;
      this.currentAppId = nextApp ? nextApp.id : null;
      if (this.currentAppId) {
        localStorage.setItem(CURRENT_APP_KEY, this.currentAppId);
      } else {
        localStorage.removeItem(CURRENT_APP_KEY);
      }
    }

    this.notify();
  }

  /**
   * Get an app by ID
   */
  getApp(appId: string): AppMeta | undefined {
    return this.apps.get(appId);
  }

  /**
   * Get current app
   */
  getCurrentApp(): AppMeta | undefined {
    return this.currentAppId ? this.apps.get(this.currentAppId) : undefined;
  }

  /**
   * Set current app
   */
  setCurrentApp(appId: string): void {
    if (!this.apps.has(appId)) {
      throw new Error(`App not found: ${appId}`);
    }
    this.currentAppId = appId;
    localStorage.setItem(CURRENT_APP_KEY, appId);
    this.notify();
  }

  /**
   * List all apps
   */
  listApps(): AppListItem[] {
    return Array.from(this.apps.values()).map(app => ({
      id: app.id,
      name: app.name,
      description: app.description,
      pageCount: app.pages.length,
      updated: app.updated,
    }));
  }

  /**
   * Get app with all pages loaded
   */
  getAppWithPages(appId: string): AppWithPages | undefined {
    const app = this.apps.get(appId);
    if (!app) return undefined;

    const pages = new Map<string, PageMeta>();
    app.pages.forEach((pageId: string) => {
      const page = this.loadPage(appId, pageId);
      if (page) {
        pages.set(pageId, page);
      }
    });

    return { app, pages };
  }

  // ==================== Page Management ====================

  /**
   * Add a page to an app
   */
  addPage(appId: string, page: PageMeta): void {
    const app = this.apps.get(appId);
    if (!app) {
      throw new Error(`App not found: ${appId}`);
    }

    if (!app.pages.includes(page.id)) {
      app.pages.push(page.id);
      app.updated = Date.now();
      this.saveApp(app);
      this.savePage(appId, page);
      this.notify();
    }
  }

  /**
   * Remove a page from an app
   */
  removePage(appId: string, pageId: string): void {
    const app = this.apps.get(appId);
    if (!app) {
      throw new Error(`App not found: ${appId}`);
    }

    // If this is the default page, clear it
    if (app.defaultPage === pageId) {
      app.defaultPage = undefined;
    }

    app.pages = app.pages.filter((id: string) => id !== pageId);
    app.updated = Date.now();
    this.saveApp(app);
    this.deletePageFromStorage(appId, pageId);
    this.notify();
  }

  /**
   * Load a page from storage
   */
  loadPage(appId: string, pageId: string): PageMeta | undefined {
    try {
      const key = `${STORAGE_KEY_PREFIX}${appId}.page.${pageId}`;
      const data = localStorage.getItem(key);
      return data ? JSON.parse(data) : undefined;
    } catch (error) {
      console.error(`[AppStore] Failed to load page ${pageId}:`, error);
      return undefined;
    }
  }

  /**
   * Save a page to storage
   */
  savePage(appId: string, page: PageMeta): void {
    const key = `${STORAGE_KEY_PREFIX}${appId}.page.${page.id}`;
    localStorage.setItem(key, JSON.stringify(page));
  }

  /**
   * Duplicate an existing page
   */
  duplicatePage(appId: string, pageId: string): PageMeta {
    const app = this.apps.get(appId);
    if (!app) {
      throw new Error(`App not found: ${appId}`);
    }

    // Load the source page
    const sourcePage = this.loadPage(appId, pageId);
    if (!sourcePage) {
      throw new Error(`Page not found: ${pageId}`);
    }

    // Generate new unique ID
    const newId = this.generateUniquePageId(app, sourcePage.id);
    
    // Generate new name (e.g., "Dashboard" -> "Dashboard Copy")
    const newName = this.generateCopyName(app, sourcePage.name);

    // Deep clone the page with new ID and name
    const duplicatedPage: PageMeta = {
      ...sourcePage,
      id: newId,
      name: newName,
      // Deep clone nodes array to avoid reference issues
      nodes: JSON.parse(JSON.stringify(sourcePage.nodes)),
    };

    // Add the duplicated page to the app
    this.addPage(appId, duplicatedPage);

    return duplicatedPage;
  }

  /**
   * Generate a unique page ID based on source ID
   */
  private generateUniquePageId(app: AppMeta, sourceId: string): string {
    let counter = 1;
    let newId = `${sourceId}-copy`;

    while (app.pages.includes(newId)) {
      counter++;
      newId = `${sourceId}-copy-${counter}`;
    }

    return newId;
  }

  /**
   * Generate a copy name (e.g., "Dashboard" -> "Dashboard Copy")
   */
  private generateCopyName(app: AppMeta, sourceName: string): string {
    // Check if name already ends with " Copy" or " Copy N"
    const copyPattern = /^(.+?)( Copy)?( \d+)?$/;
    const match = sourceName.match(copyPattern);
    
    if (!match) {
      return `${sourceName} Copy`;
    }

    const baseName = match[1];
    
    // Find all pages with similar names
    const existingNames = new Set(
      app.pages.map(pageId => {
        const page = this.loadPage(app.id, pageId);
        return page?.name;
      }).filter(Boolean)
    );

    // Try "Name Copy", "Name Copy 2", "Name Copy 3", etc.
    let newName = `${baseName} Copy`;
    let counter = 2;
    
    while (existingNames.has(newName)) {
      newName = `${baseName} Copy ${counter}`;
      counter++;
    }

    return newName;
  }

  /**
   * Delete a page from storage
   */
  private deletePageFromStorage(appId: string, pageId: string): void {
    const key = `${STORAGE_KEY_PREFIX}${appId}.page.${pageId}`;
    localStorage.removeItem(key);
  }

  // ==================== Helpers ====================

  /**
   * Generate a unique app ID from name
   */
  private generateAppId(name: string): string {
    let id = name.toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-|-$/g, '');

    let counter = 1;
    let uniqueId = id;
    while (this.apps.has(uniqueId)) {
      uniqueId = `${id}-${counter}`;
      counter++;
    }
    return uniqueId;
  }

  /**
   * Create initial page based on template
   */
  private createInitialPage(app: AppMeta, template: string): PageMeta {
    const pageId = 'home';

    switch (template) {
      case 'single-page':
        return {
          metaVersion: 1,
          id: pageId,
          name: 'Home',
          path: '/',
          rootId: 'root',
          nodes: [
            {
              id: 'root',
              type: 'container',
              props: { className: 'app-container' },
              children: ['header', 'main', 'footer'],
            },
            {
              id: 'header',
              type: 'container',
              props: { tag: 'header', className: 'app-header' },
              children: ['title'],
            },
            {
              id: 'title',
              type: 'text',
              props: { tag: 'h1', text: app.name },
            },
            {
              id: 'main',
              type: 'container',
              props: { tag: 'main', className: 'app-main' },
              children: ['content'],
            },
            {
              id: 'content',
              type: 'text',
              props: { tag: 'p', text: 'Welcome to your new app!' },
            },
            {
              id: 'footer',
              type: 'container',
              props: { tag: 'footer', className: 'app-footer' },
              children: ['footer-text'],
            },
            {
              id: 'footer-text',
              type: 'text',
              props: { tag: 'p', text: '© 2025 Your Company' },
            },
          ],
        };

      case 'dashboard':
        return {
          metaVersion: 1,
          id: pageId,
          name: 'Dashboard',
          path: '/dashboard',
          rootId: 'root',
          nodes: [
            {
              id: 'root',
              type: 'container',
              props: { className: 'dashboard-layout' },
              children: ['sidebar', 'content-area'],
            },
            {
              id: 'sidebar',
              type: 'container',
              props: { className: 'sidebar' },
              children: ['nav-title'],
            },
            {
              id: 'nav-title',
              type: 'text',
              props: { tag: 'h2', text: 'Navigation' },
            },
            {
              id: 'content-area',
              type: 'container',
              props: { className: 'dashboard-content' },
              children: ['dashboard-title'],
            },
            {
              id: 'dashboard-title',
              type: 'text',
              props: { tag: 'h1', text: 'Dashboard' },
            },
          ],
        };

      default: // 'blank' or any other
        return {
          metaVersion: 1,
          id: pageId,
          name: 'Home',
          path: '/',
          rootId: 'root',
          nodes: [
            {
              id: 'root',
              type: 'container',
              props: {},
              children: ['welcome-text'],
            },
            {
              id: 'welcome-text',
              type: 'text',
              props: { tag: 'h1', text: `Welcome to ${app.name}` },
            },
          ],
        };
    }
  }

  /**
   * Clear all data (for testing)
   */
  clearAll(): void {
    // Clear all app data
    this.apps.forEach(app => {
      app.pages.forEach((pageId: string) => {
        this.deletePageFromStorage(app.id, pageId);
      });
      this.deleteAppFromStorage(app.id);
    });

    this.apps.clear();
    this.currentAppId = null;
    localStorage.removeItem(CURRENT_APP_KEY);
    this.notify();
  }
}

// Global instance
export const appStore = new AppStore();

