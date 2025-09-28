// src/core/registry.ts
// Simple component registry for Studio UI

export type ComponentConstructor = CustomElementConstructor;

const registry = new Map<string, ComponentConstructor>();

export function registerComponent(type: string, ctor: ComponentConstructor) {
  registry.set(type, ctor);
}

export function getComponent(type: string): ComponentConstructor | undefined {
  return registry.get(type);
}

export function getAllComponentTypes(): string[] {
  return Array.from(registry.keys());
}

// Bootstrap helper so runtime code can ensure core components are registered.
export function ensureCoreRegistered() {
  if (!registry.has('container')) {
    // Dynamic import to avoid hard coupling when tree-shaken
    import('../components/ContainerElement.js').then(()=>{/*noop*/});
  }
  if (!registry.has('text')) {
    import('../components/TextElement.js').then(()=>{/*noop*/});
  }
  if (!registry.has('button')) {
    import('../components/ButtonElement.js').then(()=>{/*noop*/});
  }
  if (!registry.has('unknown')) {
    import('../components/UnknownElement.js').then(()=>{/*noop*/});
  }
}
