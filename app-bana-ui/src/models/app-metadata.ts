/**
 * App Metadata Models
 * Defines the structure for multi-page applications in Studio
 */

import type { PageMeta } from './metadata';
import type { EntityMeta, NavigationMeta } from './entity-metadata';

/**
 * Application metadata
 * An app contains multiple pages and shared settings
 */
export interface AppMeta {
  id: string;                    // Unique app identifier (e.g., "my-crm-app")
  name: string;                  // Display name (e.g., "My CRM Application")
  description?: string;          // Optional description
  version: string;               // Semantic version (e.g., "1.0.0")
  author?: string;               // Author name or organization
  created: number;               // Creation timestamp (ms)
  updated: number;               // Last update timestamp (ms)

  // Pages in this app
  pages: string[];               // Array of page IDs
  defaultPage?: string;          // Default/home page ID

  // Entities (business objects)
  entities?: EntityMeta[];       // Entity definitions for this app
  schemas?: string[];            // Schema names linked to this app
  navigation?: NavigationMeta;   // Navigation structure

  // App-wide settings
  theme?: AppTheme;              // Visual theme settings
  routes?: AppRoutes;            // Routing configuration
  metadata?: Record<string, any>; // Custom metadata
}

/**
 * Theme configuration for the app
 */
export interface AppTheme {
  primaryColor?: string;
  secondaryColor?: string;
  surfaceColor?: string;
  textColor?: string;
  fontFamily?: string;
  darkMode?: boolean;
  customCSS?: string;
}

/**
 * Routing configuration
 */
export interface AppRoutes {
  basePath?: string;                      // Base URL path (e.g., "/app")
  pageRoutes?: Record<string, string>;    // Page ID -> route path mapping
}

/**
 * App list item for display in UI
 */
export interface AppListItem {
  id: string;
  name: string;
  description?: string;
  pageCount: number;
  updated: number;
}

/**
 * Complete app with all pages loaded
 */
export interface AppWithPages {
  app: AppMeta;
  pages: Map<string, PageMeta>;  // Map of page ID to PageMeta
}

/**
 * App creation request
 */
export interface CreateAppRequest {
  name: string;
  description?: string;
  template?: 'blank' | 'single-page' | 'multi-page' | 'dashboard';
}

/**
 * App update request
 */
export interface UpdateAppRequest {
  name?: string;
  description?: string;
  theme?: AppTheme;
  routes?: AppRoutes;
  defaultPage?: string;
  entities?: EntityMeta[];
  schemas?: string[];
  navigation?: NavigationMeta;
}

