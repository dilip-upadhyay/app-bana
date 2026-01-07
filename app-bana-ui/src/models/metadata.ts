// Studio metadata model (Phase A) - aligned with current renderer implementation

export interface ComponentNode {
  id: string;
  type: string; // 'container' | 'text' | 'button' | 'input' | 'select' | etc.
  label?: string; // friendly name in Studio
  props?: Record<string, any>;
  // For INPUT/SELECT/TEXTAREA components:
  //   props.entity?: string    // Entity name to bind to (e.g., "User")
  //   props.field?: string     // Field name in that entity (e.g., "email")
  // For BUTTON components with save action:
  //   props.entities?: string[] // Array of entity names to save (e.g., ["User", "Address"])
  //   props.actionType?: string // 'save' | 'navigate' | 'api'
  children?: string[]; // child node ids
  style?: { classes?: string[] };
}

export interface PageMeta {
  metaVersion?: number; // future migration hook
  id: string;
  name: string;
  path: string;
  rootId: string;
  nodes: ComponentNode[];
  type?: string; // legacy/demo compatibility
}

export interface ThemeMeta { id: string; name: string; tokens: Record<string, string | number>; }
export interface NavigationItem { id: string; label: string; path: string; children?: NavigationItem[]; }
export interface NavigationMeta { items: NavigationItem[]; }
export interface Binding { kind: string; ref?: string; expr?: string; }
export interface Action { type: string; config?: Record<string, any>; }
export interface RenderContext { /* reserved for future runtime services */ }
