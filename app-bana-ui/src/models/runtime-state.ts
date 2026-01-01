/**
 * Runtime State Models
 * Defines the complete state needed for running an app in preview/production
 */

import type { AppMeta } from './app-metadata';
import type { PageMeta } from './metadata';

/**
 * Complete runtime state for an application
 * This is what gets passed to the preview/runtime renderer
 */
export interface AppRuntimeState {
  // Tenant ID (optional, defaults to 'default' in runtime)
  tenantId?: string;

  // App metadata
  app: AppMeta;

  // All pages in the app
  pages: PageMeta[];

  // Current page being viewed
  currentPageId: string;

  // Navigation context
  navigation: {
    history: string[];           // Page IDs visited
    canGoBack: boolean;
    canGoForward: boolean;
  };

  // Runtime mode
  mode: 'preview' | 'production' | 'development';

  // Additional context
  context?: {
    studioUrl?: string;          // Link back to studio for preview mode
    timestamp: number;           // When this state was generated
    [key: string]: any;
  };
}

/**
 * Serialized version for URL/localStorage
 * More compact for transmission
 */
export interface AppRuntimeStateCompact {
  tenantId?: string;             // Tenant ID (defaults to 'default' if not provided)
  appId: string;                 // App ID to load
  pageId?: string;               // Optional specific page (otherwise use default)
  env?: string;                  // Optional environment (e.g. DEV, PROD) to load snapshot from
  mode?: 'preview' | 'production';
}

/**
 * Helper to create runtime state from app store
 */
export function createRuntimeState(
  app: AppMeta,
  pages: PageMeta[],
  currentPageId?: string,
  mode: 'preview' | 'production' | 'development' = 'preview'
): AppRuntimeState {
  const pageId = currentPageId || app.defaultPage || pages[0]?.id;

  return {
    app,
    pages,
    currentPageId: pageId,
    navigation: {
      history: [pageId],
      canGoBack: false,
      canGoForward: false
    },
    mode,
    context: {
      timestamp: Date.now(),
      studioUrl: mode === 'preview' ? '/studio.html' : undefined
    }
  };
}

/**
 * Helper to encode runtime state for URL
 */
export function encodeRuntimeState(state: AppRuntimeStateCompact): string {
  return btoa(JSON.stringify(state));
}

/**
 * Helper to decode runtime state from URL
 */
export function decodeRuntimeState(encoded: string): AppRuntimeStateCompact {
  return JSON.parse(atob(encoded));
}
