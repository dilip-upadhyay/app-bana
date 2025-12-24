// src/core/registry.ts
// Simple component registry for Studio UI

export type ComponentConstructor = CustomElementConstructor;

const registry = new Map<string, ComponentConstructor>();
const tagNameRegistry = new Map<string, string>(); // type -> tagName

// Export the registry for external use
export { registry };

export function registerComponent(type: string, ctor: ComponentConstructor, tagName?: string) {
  registry.set(type, ctor);
  if (tagName) {
    tagNameRegistry.set(type, tagName);
  }
}

export function getComponent(type: string): ComponentConstructor | undefined {
  return registry.get(type);
}

export function getComponentTagName(type: string): string | undefined {
  return tagNameRegistry.get(type);
}

export function getAllComponentTypes(): string[] {
  return Array.from(registry.keys());
}

// Bootstrap helper so runtime code can ensure core components are registered.
export function ensureCoreRegistered(): Promise<void> {
  const proms: Promise<any>[] = [];

  // Core components
  if (!registry.has('container')) {
    proms.push(import('../components/ContainerElement.js').then(m => registerComponent('container', m.ContainerElement, 'studio-container')));
  }
  if (!registry.has('text')) {
    proms.push(import('../components/TextElement.js').then(m => registerComponent('text', m.TextElement, 'studio-text')));
  }
  if (!registry.has('button')) {
    proms.push(import('../components/ButtonElement.js').then(m => registerComponent('button', m.ButtonElement, 'studio-button')));
  }
  if (!registry.has('unknown')) {
    proms.push(import('../components/UnknownElement.js').then(m => registerComponent('unknown', m.UnknownElement, 'studio-unknown')));
  }

  // Basic Layout Elements (Ghost Components -> Real)
  if (!registry.has('list')) {
    proms.push(import('../components/BasicElements.js').then(m => {
      registerComponent('list', m.ListElement, 'studio-list');
      registerComponent('card', m.CardElement, 'studio-card');
      registerComponent('detail', m.DetailElement, 'studio-detail');
      registerComponent('dashboard', m.DashboardElement, 'studio-dashboard');
    }));
  }

  // Form components
  if (!registry.has('input')) {
    proms.push(import('../components/InputElement.js').then(m => registerComponent('input', m.InputElement, 'studio-input')));
  }
  if (!registry.has('textarea')) {
    proms.push(import('../components/TextareaElement.js').then(m => registerComponent('textarea', m.TextareaElement, 'studio-textarea')));
  }
  if (!registry.has('select')) {
    proms.push(import('../components/SelectElement.js').then(m => registerComponent('select', m.SelectElement, 'studio-select')));
  }
  if (!registry.has('checkbox')) {
    proms.push(import('../components/CheckboxElement.js').then(m => registerComponent('checkbox', m.CheckboxElement, 'studio-checkbox')));
  }
  if (!registry.has('radio-group')) {
    proms.push(import('../components/RadioGroupElement.js').then(m => registerComponent('radio-group', m.RadioGroupElement, 'studio-radio-group')));
  }

  // HTML elements
  if (!registry.has('header')) {
    proms.push(import('../components/HTMLElements.js').then(() => {
      // HTMLElements.ts registers components as side-effect. 
      // We just ensure it is loaded.
      // But registry.ts expects us to register them?
      // HTMLElements.ts calls registerComponent directly!
      // So we just need to wait for import.
    }));
  }

  // Builder components
  if (!registry.has('ai-chat-builder')) {
    proms.push(import('../builder/components/AiChatBuilder.js').then(m => registerComponent('ai-chat-builder', m.AiChatBuilder, 'ai-chat-builder')));
  }

  // Runtime components
  if (!registry.has('app-runtime-shell')) {
    proms.push(import('../runtime/shell/AppRuntimeShell.js').then(m => registerComponent('app-runtime-shell', m.AppRuntimeShell, 'app-runtime-shell')));
  }

  return Promise.all(proms).then(() => { });
}
