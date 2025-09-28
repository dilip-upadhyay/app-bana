// Simple component registry for Studio runtime & builder
export interface RegisteredComponent {
  type: string;          // logical type in metadata (e.g., "Container")
  tag: string;           // custom element tag (e.g., "studio-container")
  version?: number;
  category?: string;
  defaultProps?: Record<string, any>;
}

const _registry: Map<string, RegisteredComponent> = new Map();

export function registerComponent(def: RegisteredComponent) {
  _registry.set(def.type, def);
}

export function getComponent(type: string): RegisteredComponent | undefined {
  return _registry.get(type);
}

export function listComponents(): RegisteredComponent[] { return Array.from(_registry.values()); }

