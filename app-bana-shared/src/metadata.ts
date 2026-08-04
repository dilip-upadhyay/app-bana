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
  /**
   * Phase B additions — all optional and backward compatible. When absent
   * the runtime falls back to the legacy flat-form / flat-list rendering.
   */
  layout?: 'form' | 'list' | 'detail' | 'wizard' | 'detail_tabs'; // B1, B4
  steps?: WizardStep[];             // B1
  filters?: FilterDef[];            // B5
  groupBy?: string;                 // B5
  defaultSort?: SortDef;            // B5
  aggregates?: AggregateDef[];      // B5
  savedViews?: SavedView[];         // B5 (server-side owned; may be denormalised here)
}

/** Phase B1 — one step of a wizard-layout page. */
export interface WizardStep {
  id: string;
  title: string;
  subtitle?: string;
  fields: string[];                 // field names to render in this step
  validation?: 'onNext' | 'onSubmit';
}

/** Phase B5 — filter chip descriptor for list pages. */
export interface FilterDef {
  field: string;
  op:
    | 'equals'
    | 'in'
    | 'range'
    | 'contains'
    | 'dateRange';
  label: string;
  default?: unknown;
}

export interface SortDef {
  field: string;
  direction: 'asc' | 'desc';
}

export interface AggregateDef {
  field: string;
  agg: 'sum' | 'avg' | 'count' | 'min' | 'max';
  label?: string;
}

export interface SavedView {
  id: string;
  name: string;
  filters: Record<string, unknown>;
  groupBy?: string;
  sort?: SortDef;
  isDefault?: boolean;
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
  | 'phone' | 'status' | 'reference'
  // Phase B additions
  | 'file'         // B3 — uploaded file/attachment (stored as file id)
  | 'child_table'; // B4 — 1:N child entity rendered inline

/**
 * Phase B2 — Declarative expression grammar for conditional field metadata.
 * Evaluated purely (no `eval`, no `Function`) against a form-values object
 * by the runtime `evaluateExpression` helper.
 */
export type Expression =
  | {
      field: string;
      op:
        | 'equals'
        | 'notEquals'
        | 'in'
        | 'notIn'
        | 'gt'
        | 'lt'
        | 'gte'
        | 'lte'
        | 'contains'
        | 'isEmpty'
        | 'isNotEmpty';
      value?: unknown;
    }
  | { and: Expression[] }
  | { or: Expression[] }
  | { not: Expression };

/**
 * Phase B2 — Per-field visibility / requiredness / disabled state driven by
 * declarative expressions.
 */
export interface FieldCondition {
  showWhen?: Expression;
  requiredWhen?: Expression;
  disabledWhen?: Expression;
}

/**
 * Phase B3 — File-type field constraints. Enforced client-side (before
 * upload) and server-side (on `POST /api/files/upload`).
 */
export interface FileConstraints {
  maxSizeBytes: number;
  acceptedMimeTypes: string[]; // e.g. ["image/*", "application/pdf"]
  maxFiles?: number;           // default 1
}

/**
 * Phase B4 — Child-entity descriptor for a `child_table` field / node. The
 * runtime uses `fkField` to filter the child entity by the parent's id.
 */
export interface ChildEntityRef {
  entityName: string;   // qualified key `{tenantId}_{appId}_{Entity}` or bare name
  fkField: string;      // FK column on the child pointing back to parent id
  displayFields: string[];
}

export interface EntityField {
  name: string;
  label?: string;
  type: FieldType;
  required?: boolean;
  autoIncrement?: boolean;
  options?: string[];         // for status fields
  referenceEntity?: string;   // for reference fields
  existingName?: string;      // for renames
  // Phase B additions (all optional / backward compatible)
  conditions?: FieldCondition;    // B2
  fileConstraints?: FileConstraints; // B3
  childEntity?: ChildEntityRef;   // B4 — required when type === 'child_table'
  onDelete?: 'cascade' | 'restrict' | 'setNull'; // B4 — FK cascade rule for references
}

export interface EntitySchema {
  name: string;
  tenantId: string;
  appId: string;
  approvalRequired?: boolean; // Task C1.3 — approval workflow enabled
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
  /**
   * S2.10 — set only on apps returned by `listMyApps()`/`GET /api/users/me/apps` for a
   * CROSS-TENANT membership grant (i.e. `tenantId` differs from the caller's own session
   * tenant). Absent for the caller's own-tenant apps. Lets the Studio switcher visually
   * distinguish "an app you were granted access to elsewhere" from "an app you own".
   */
  role?: string;
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
