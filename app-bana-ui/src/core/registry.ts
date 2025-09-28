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

