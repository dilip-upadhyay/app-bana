// src/core/registry.ts
// Simple component registry for Studio UI

export type ComponentConstructor = CustomElementConstructor;

const registry = new Map<string, ComponentConstructor>();

// Export the registry for external use
export { registry };

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
export function ensureCoreRegistered(): Promise<void> {
  const proms: Promise<any>[] = [];

  // Core components
  if (!registry.has('container')) {
    proms.push(import('../components/ContainerElement.js'));
  }
  if (!registry.has('text')) {
    proms.push(import('../components/TextElement.js'));
  }
  if (!registry.has('button')) {
    proms.push(import('../components/ButtonElement.js'));
  }
  if (!registry.has('unknown')) {
    proms.push(import('../components/UnknownElement.js'));
  }

  // HTML elements
  if (!registry.has('header')) {
    proms.push(import('../components/HTMLElements.js'));
  }

  // Builder components
  if (!registry.has('ai-chat-builder')) {
    proms.push(import('../builder/components/AiChatBuilder.js'));
  }

  return Promise.all(proms).then(()=>{});
}
