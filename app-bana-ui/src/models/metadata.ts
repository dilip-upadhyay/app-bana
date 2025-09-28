// Studio metadata model (Phase A) - aligned with current renderer implementation

export interface ComponentNode {
  id: string;
  type: string; // 'container' | 'text' | 'button' | future
  props?: Record<string, any>;
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

export interface ThemeMeta { id: string; name: string; tokens: Record<string,string|number>; }
export interface NavigationItem { id: string; label: string; path: string; children?: NavigationItem[]; }
export interface NavigationMeta { items: NavigationItem[]; }
export interface Binding { kind: string; ref?: string; expr?: string; }
export interface Action { type: string; config?: Record<string,any>; }
export interface RenderContext { /* reserved for future runtime services */ }
