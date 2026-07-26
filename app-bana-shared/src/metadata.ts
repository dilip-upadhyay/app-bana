// Core metadata types — ported from app-bana-ui/src/models/metadata.ts
// This is the contract between the studio, runtime, and AI agent.
// Changes here affect ALL packages — treat as a versioned API.

export interface ComponentNode {
  id: string;
  type: string;
  label?: string;
  props?: Record<string, any>;
  children?: string[];
  style?: { classes?: string[] };
}

export interface PageMeta {
  metaVersion?: number;
  id: string;
  name: string;
  path: string;
  rootId: string;
  nodes: ComponentNode[];
  type?: string;
  /**
   * Sprint 3 task 3.2 — Authoritative page-kind metadata. When present the
   * runtime trusts this over sniffing the node tree. `dashboard` reserved
   * for future non-entity landing pages. Legacy pages without `kind` fall
   * back to the runtime's `classifyPage` heuristic.
   */
  kind?: 'form' | 'list' | 'detail' | 'dashboard';
  /**
   * Sprint 3 task 3.3 — For `kind: 'detail'` pages, the qualified entity key
   * (`{tenantId}_{appId}_{Entity}`) the page renders records from. Optional
   * because add / list pages don't need it (they derive entity from a node
   * prop instead).
   */
  entityKey?: string;
}

export interface ThemeMeta {
  id: string;
  name: string;
  tokens: Record<string, string | number>;
}

export interface NavigationItem {
  id: string;
  label: string;
  path: string;
  children?: NavigationItem[];
}

export interface NavigationMeta {
  items: NavigationItem[];
}

// Valid entity field types (matches SchemaManager.sqlType() in app-bana-service)
export type FieldType =
  | 'text' | 'longtext' | 'number' | 'decimal'
  | 'boolean' | 'date' | 'datetime' | 'email'
  | 'phone' | 'status' | 'reference';

export interface EntityField {
  name: string;
  label?: string;
  type: FieldType;
  required?: boolean;
  autoIncrement?: boolean;
  options?: string[];         // for status fields
  referenceEntity?: string;   // for reference fields
  existingName?: string;      // for renames
}

export interface EntitySchema {
  name: string;
  tenantId: string;
  appId: string;
  fields: EntityField[];
}

export interface AppMeta {
  id: string;
  name: string;
  tenantId: string;
  description?: string;
  status?: string;
  createdAt?: string;
  updatedAt?: string;
  entities?: EntitySchema[];
  /**
   * Legacy field: either an array of page ID strings (as returned by the
   * backend today) or full PageMeta objects. Consumers should prefer
   * `pagesData` when they need the full metadata.
   */
  pages?: PageMeta[] | string[];
  /** Full page metadata objects (backend `pagesData` field). */
  pagesData?: PageMeta[];
}

export interface TenantBranding {
  tenantId: string;
  displayName: string;
  logoUrl: string | null;
  primaryColor: string;
}

export interface AppContext {
  tenantId: string;
  appId: string;
  branding: TenantBranding;
}
