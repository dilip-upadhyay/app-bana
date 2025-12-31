/**
 * App Store - Manages applications and their pages
 * **UPDATED**: Now persists to backend REST API at /apps instead of localStorage
 * 
 * Backend storage: app-bana-service/apps/{appId}/app.json
 * Pages storage: app-bana-service/apps/{appId}/pages/{pageId}.json
 */

import type { AppMeta, AppWithPages, CreateAppRequest, UpdateAppRequest, AppListItem } from '../../models/app-metadata';
import type { PageMeta } from '../../models/metadata';
import type { EntityMeta } from '../../models/entity-metadata';
import { apiClient } from '../../core/api-client';

export type ConversationTelemetryType = 'greeting' | 'idea' | 'decision' | 'smallTalk';

export interface ConversationTelemetryEvent {
  type: ConversationTelemetryType;
  persona: string;
  detail: Record<string, any>;
  timestamp: number;
}

const CURRENT_APP_KEY = 'appbana.current.app'; // Only current app ID stored in localStorage

export class AppStore {
  private apps: Map<string, AppMeta> = new Map();
  private currentAppId: string | null = null;
  private listeners = new Set<() => void>();
  private telemetryListeners = new Set<(event: ConversationTelemetryEvent) => void>();
  private loading = false;

  constructor() {
    this.loadApps();
  }

  // ==================== Lifecycle ====================

  /**
   * Load all apps from backend
   */
  public async loadApps() {
    if (this.loading) return;
    this.loading = true;

    try {
      // Load apps list from backend
      const response = await apiClient.get<{ apps: AppListItem[] }>('/appbana-studio/apps');
      const appsList = response.apps || [];
      // Populate apps map with summary data
      this.apps.clear();
      for (const appSummary of appsList) {
        // Store just the summary initially, full data loaded on demand
        const app: AppMeta = {
          id: appSummary.id,
          name: appSummary.name,
          description: appSummary.description,
          version: '1.0.0',
          created: Date.now(),
          updated: appSummary.updated,
          pages: [], // Will be loaded when needed
        };
        this.apps.set(app.id, app);
      }

      this.notify();

      // Load current app ID from localStorage
      const currentId = localStorage.getItem(CURRENT_APP_KEY);
      if (currentId && this.apps.has(currentId)) {
        this.currentAppId = currentId;
        this.notify(); // FIX: Notify listeners (AppManager) that session was restored
        // Load full app data for current app
        await this.loadFullApp(currentId);
      }
    } catch (error) {
      console.error('[AppStore] Failed to load apps from backend:', error);
      // Fallback: try to load from localStorage (migration path)
      this.loadAppsFromLocalStorage();
    } finally {
      this.loading = false;
    }
  }

  /**
   * Load full app data including pages list (not page content)
   */
  private async loadFullApp(appId: string): Promise<void> {
    try {
      const fullApp = await apiClient.get<AppMeta>(`/appbana-studio/apps/${appId}`);
      this.apps.set(appId, fullApp);
      this.notify(); // FIX: Notify listeners that full app data is loaded (pages + entities)
    } catch (error) {
      console.error(`[AppStore] Failed to load full app ${appId}:`, error);
    }
  }

  /**
   * Fallback: Load from localStorage (for migration)
   * @deprecated Will be removed once all apps are migrated to backend
   */
  private loadAppsFromLocalStorage() {
    try {
      const STORAGE_KEY_PREFIX = 'appbana.apps.';
      const APPS_LIST_KEY = 'appbana.apps.list';

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

      const currentId = localStorage.getItem(CURRENT_APP_KEY);
      if (currentId && this.apps.has(currentId)) {
        this.currentAppId = currentId;
      }
    } catch (error) {
      console.error('[AppStore] Failed to load apps from localStorage:', error);
    }
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

  onTelemetry(fn: (event: ConversationTelemetryEvent) => void): () => void {
    this.telemetryListeners.add(fn);
    return () => this.telemetryListeners.delete(fn);
  }

  recordTelemetry(event: Omit<ConversationTelemetryEvent, 'timestamp'>): ConversationTelemetryEvent {
    const envelope: ConversationTelemetryEvent = {
      ...event,
      timestamp: Date.now()
    };
    console.info('[AppStore] Telemetry', envelope);
    this.telemetryListeners.forEach(listener => listener(envelope));
    return envelope;
  }

  /**
   * Get loading state
   */
  isLoading(): boolean {
    return this.loading;
  }

  /**
   * Set loading state and notify listeners
   */
  private setLoading(loading: boolean) {
    if (this.loading !== loading) {
      this.loading = loading;
      this.notify();
    }
  }

  // ==================== App Management ====================

  /**
   * Create a new app (async - saves to backend)
   */
  async createApp(request: CreateAppRequest): Promise<AppMeta> {
    this.setLoading(true);

    try {
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
          basePath: `/${id}`,
        },
      };

      // Save to backend
      const created = await apiClient.post<AppMeta>('/appbana-studio/apps', app);

      // Update local cache
      this.apps.set(created.id, created);

      // Set as current app
      this.currentAppId = created.id;
      localStorage.setItem(CURRENT_APP_KEY, created.id);

      this.notify();
      return created;
    } finally {
      this.setLoading(false);
    }
  }

  /**
   * Update an existing app (async - saves to backend)
   */
  async updateApp(appId: string, updates: UpdateAppRequest): Promise<AppMeta> {
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

    // Save to backend
    const saved = await apiClient.put<AppMeta>(`/appbana-studio/apps/${appId}`, updatedApp);

    // Update local cache
    this.apps.set(appId, saved);
    this.notify();
    return saved;
  }

  /**
   * Delete an app and all its pages (async - deletes from backend)
   */
  async deleteApp(appId: string): Promise<void> {
    const app = this.apps.get(appId);
    if (!app) {
      throw new Error(`App not found: ${appId}`);
    }

    // Backend will recursively delete app directory and all pages
      await apiClient.delete(`/appbana-studio/apps/${appId}`);    // Update local cache
    this.apps.delete(appId);

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
  async setCurrentApp(appId: string): Promise<void> {
    if (!this.apps.has(appId)) {
      throw new Error(`App not found: ${appId}`);
    }
    this.currentAppId = appId;
    localStorage.setItem(CURRENT_APP_KEY, appId);

    // Load full app data with entities from backend
    await this.loadFullApp(appId);

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
   * Get app with all pages loaded (async - loads from backend)
   */
  async getAppWithPages(appId: string): Promise<AppWithPages | undefined> {
    const app = this.apps.get(appId);
    if (!app) return undefined;

    const pages = new Map<string, PageMeta>();

    // Load all pages in parallel
    const pagePromises = app.pages.map(async (pageId: string) => {
      const page = await this.loadPage(appId, pageId);
      if (page) {
        pages.set(pageId, page);
      }
    });

    await Promise.all(pagePromises);

    return { app, pages };
  }

  // ==================== Entity Management ====================

  /**
   * Add an entity to an app (async - saves to backend)
   */
  async addEntity(appId: string, entity: EntityMeta): Promise<void> {
    const app = this.apps.get(appId);
    if (!app) {
      throw new Error(`App not found: ${appId}`);
    }

    if (!app.entities) {
      app.entities = [];
    }

    // Check if entity already exists
    const exists = app.entities.some(e => e.name === entity.name);
    if (!exists) {
      app.entities.push(entity);
      app.updated = Date.now();

      // Save entire app object since entities are embedded
      await apiClient.put<AppMeta>(`/appbana-studio/apps/${appId}`, app);

      // Update local cache
      this.apps.set(appId, app);
      this.notify();
    }
  }

  // ==================== Page Management ====================

  /**
   * Add a page to an app (async - saves to backend)
   */
  async addPage(appId: string, page: PageMeta): Promise<void> {
    const app = this.apps.get(appId);
    if (!app) {
      throw new Error(`App not found: ${appId}`);
    }

    if (!app.pages.includes(page.id)) {
      // Save page to backend
      await apiClient.put(`/appbana-studio/apps/${appId}/pages/${page.id}`, page);

      // Update app pages list
      app.pages.push(page.id);
      app.updated = Date.now();
      await apiClient.put(`/appbana-studio/apps/${appId}`, app);

      // Update local cache
      this.apps.set(appId, app);
      this.notify();
    }
  }

  /**
   * Remove a page from an app (async - deletes from backend)
   */
  async removePage(appId: string, pageId: string): Promise<void> {
    const app = this.apps.get(appId);
    if (!app) {
      throw new Error(`App not found: ${appId}`);
    }

    // If this is the default page, clear it
    if (app.defaultPage === pageId) {
      app.defaultPage = undefined;
    }

    // Delete page from backend
      await apiClient.delete(`/appbana-studio/apps/${appId}/pages/${pageId}`);    // Update app pages list
    app.pages = app.pages.filter((id: string) => id !== pageId);
    app.updated = Date.now();
    await apiClient.put(`/studio/apps/${appId}`, app);

    // Update local cache
    this.apps.set(appId, app);
    this.notify();
  }

  /**
   * Load a page from backend
   */
  async loadPage(appId: string, pageId: string): Promise<PageMeta | undefined> {
    try {
      const page = await apiClient.get<PageMeta>(`/appbana-studio/apps/${appId}/pages/${pageId}`);
      return page;
    } catch (error) {
      console.error(`[AppStore] Failed to load page ${pageId}:`, error);
      return undefined;
    }
  }

  /**
   * Save a page to backend
   */
  async savePage(appId: string, page: PageMeta): Promise<void> {
    await apiClient.put(`/appbana-studio/apps/${appId}/pages/${page.id}`, page);
  }

  /**
   * Duplicate an existing page (async - saves to backend)
   */
  async duplicatePage(appId: string, pageId: string): Promise<PageMeta> {
    const app = this.apps.get(appId);
    if (!app) {
      throw new Error(`App not found: ${appId}`);
    }

    // Load the source page (await the promise)
    const sourcePage = await this.loadPage(appId, pageId);
    if (!sourcePage) {
      throw new Error(`Page not found: ${pageId}`);
    }

    // Generate new unique ID
    const newId = this.generateUniquePageId(app, sourcePage.id);

    // Generate new name (e.g., "Dashboard" -> "Dashboard Copy")
    const newName = await this.generateCopyName(app, sourcePage.name);

    // Deep clone the page with new ID and name
    const duplicatedPage: PageMeta = {
      ...sourcePage,
      id: newId,
      name: newName,
      // Deep clone nodes array to avoid reference issues
      nodes: structuredClone(sourcePage.nodes),
    };

    // Add the duplicated page to the app
    await this.addPage(appId, duplicatedPage);

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
  private async generateCopyName(app: AppMeta, sourceName: string): Promise<string> {
    // Check if name already ends with " Copy" or " Copy N"
    const copyPattern = /^(.+?)( Copy)?( \d+)?$/;
    const match = copyPattern.exec(sourceName);

    if (!match) {
      return `${sourceName} Copy`;
    }

    const baseName = match[1];

    // Load all pages to get their names
    const pagePromises = app.pages.map(pageId => this.loadPage(app.id, pageId));
    const pages = await Promise.all(pagePromises);

    // Find all pages with similar names
    const existingNames = new Set(
      pages
        .filter(page => page !== undefined)
        .map(page => page!.name)
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
   * Clear all data (for testing) - async, deletes from backend
   */
  async clearAll(): Promise<void> {
    // Delete all apps from backend
    const deletePromises = Array.from(this.apps.keys()).map(appId =>
      this.deleteApp(appId)
    );

    await Promise.all(deletePromises);

    this.apps.clear();
    this.currentAppId = null;
    localStorage.removeItem(CURRENT_APP_KEY);
    this.notify();
  }
}

// Global instance
export const appStore = new AppStore();
