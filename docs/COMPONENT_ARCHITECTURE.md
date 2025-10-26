# Component Architecture Reference

This document describes the component architecture used in AppBana Studio UI.

## Overview

All components follow a clean 3-file structure similar to Angular, providing clear separation of concerns and maintainability.

## File Structure

```
ComponentName/
├── ComponentName.ts    # TypeScript: logic, state, templates
├── ComponentName.css   # Styles: scoped to component
└── ComponentName.html  # Reference: documentation and structure
```

## Component Template

### TypeScript File (ComponentName.ts)

```typescript
import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state, property } from 'lit/decorators.js';
import styles from './ComponentName.css?inline';

@customElement('component-name')
export class ComponentName extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;
  
  // Public properties (component API)
  @property({ type: String }) label = '';
  
  // Private state (internal only)
  @state() private isActive = false;

  // Lifecycle
  connectedCallback() {
    super.connectedCallback();
    // Initialize component
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    // Cleanup
  }

  // Event handlers
  private handleClick() {
    this.isActive = !this.isActive;
    this.dispatchEvent(new CustomEvent('toggle', { 
      detail: { active: this.isActive } 
    }));
  }

  // Render method using Lit's html template
  render() {
    return html`
      <div class="container ${this.isActive ? 'active' : ''}">
        <button @click=${this.handleClick}>
          ${this.label}
        </button>
      </div>
    `;
  }
}

// TypeScript declaration for HTML tag
declare global {
  interface HTMLElementTagNameMap {
    'component-name': ComponentName;
  }
}
```

### CSS File (ComponentName.css)

```css
:host {
  display: block;
}

.container {
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
}

.container.active {
  background: #f0f9ff;
  border-color: #2563eb;
}

button {
  padding: 8px 16px;
  border: none;
  background: #2563eb;
  color: white;
  border-radius: 4px;
  cursor: pointer;
}

button:hover {
  background: #1d4ed8;
}
```

### HTML File (ComponentName.html)

```html
<!-- ComponentName - Reference Documentation -->

<!-- 
  Description: A toggleable button component
  
  Properties:
    - label: string - Button text
  
  Events:
    - toggle: { detail: { active: boolean } } - Fired when toggled
  
  Usage:
    <component-name label="Click Me"></component-name>
-->

<div class="container">
  <button data-slot="button">
    <span data-slot="label"></span>
  </button>
</div>
```

## Key Concepts

### CSS Import with ?inline

CSS files must be imported with the `?inline` query parameter:

```typescript
import styles from './ComponentName.css?inline';
```

This tells Vite to import the CSS as a string rather than processing it separately.

### Type Declarations

The `src/vite-env.d.ts` file provides TypeScript declarations:

```typescript
declare module '*.css?inline' {
  const content: string;
  export default content;
}
```

### Shadow DOM and :host

The `:host` selector in CSS targets the component's host element:

```css
:host {
  display: block;
  /* Styles applied to <component-name> element */
}

:host(.dark-mode) {
  /* Conditional styles based on host classes */
}
```

### Reactive State with @state

Use `@state()` for internal reactive state:

```typescript
@state() private count = 0;

private increment() {
  this.count++; // Automatically triggers re-render
}
```

### Public API with @property

Use `@property()` for component inputs:

```typescript
@property({ type: String }) label = '';
@property({ type: Number }) max = 100;
@property({ type: Boolean }) disabled = false;
```

### Event Dispatching

Dispatch custom events for component outputs:

```typescript
this.dispatchEvent(new CustomEvent('change', {
  detail: { value: this.count },
  bubbles: true,
  composed: true
}));
```

## Best Practices

### 1. Keep Components Focused

Each component should have a single, well-defined responsibility.

### 2. Use Shadow DOM

Leverage Shadow DOM for style encapsulation. Avoid global styles.

### 3. Document Your Components

Keep the `.html` file updated with:
- Component description
- Properties and their types
- Events and their payloads
- Usage examples

### 4. Write Tests

Create `.test.ts` files for components:

```typescript
import { fixture, expect } from '@open-wc/testing';
import { html } from 'lit';
import './ComponentName';

describe('ComponentName', () => {
  it('renders with label', async () => {
    const el = await fixture(html`
      <component-name label="Test"></component-name>
    `);
    
    const button = el.shadowRoot!.querySelector('button');
    expect(button?.textContent?.trim()).to.equal('Test');
  });
});
```

### 5. Follow Naming Conventions

- **File names:** PascalCase (e.g., `BuilderCanvas.ts`)
- **Element names:** kebab-case (e.g., `builder-canvas`)
- **CSS classes:** kebab-case (e.g., `tree-node`)
- **Private methods:** camelCase with prefix (e.g., `private handleClick()`)

### 6. Use TypeScript Features

- Leverage interfaces for complex props
- Use enums for fixed value sets
- Add return type annotations
- Enable strict mode

## Component Categories

### Builder Components (`src/builder/components/`)

Components for the design-time builder interface:
- `BuilderCanvas` - Tree editor with drag-drop
- `BuilderInspector` - Property editor
- `BuilderShell` - Layout shell
- `TokenPanel` - Design token editor

### Application Components (`src/components/`)

Components for the runtime application:
- `AppSidebar` - Navigation
- `ComponentGallery` - Component showcase
- `EntityExplorer` - CRUD interface

### Runtime Components (`src/runtime/`)

Components that are rendered from metadata:
- `ContainerElement` - Layout container
- `TextElement` - Text display
- `ButtonElement` - Interactive button
- `UnknownElement` - Fallback for unknown types

## Migration from Old Structure

If you have old components without the 3-file structure:

1. **Extract CSS:** Move all styles from the `static styles` property to a new `.css` file
2. **Import CSS:** Add `import styles from './Component.css?inline'`
3. **Update static styles:** Change to `static styles = css\`${unsafeCSS(styles)}\``
4. **Create .html:** Document the component structure and usage
5. **Test:** Ensure the component still works correctly

## Troubleshooting

### CSS not loading

**Problem:** Styles don't apply to component
**Solution:** Ensure you're using `?inline` suffix: `import styles from './File.css?inline'`

### TypeScript errors on imports

**Problem:** Cannot find module '*.css?inline'
**Solution:** Ensure `src/vite-env.d.ts` exists and is included in `tsconfig.json`

### Styles leaking between components

**Problem:** Styles from one component affect another
**Solution:** Verify Shadow DOM is enabled (default for LitElement) and use `:host` selector

### Component not registering

**Problem:** Component doesn't appear in browser
**Solution:** Ensure `@customElement('name')` decorator is present and component is imported somewhere

## Resources

- [Lit Documentation](https://lit.dev/)
- [Web Components Standards](https://developer.mozilla.org/en-US/docs/Web/Web_Components)
- [Vite Documentation](https://vitejs.dev/)
- [TypeScript Handbook](https://www.typescriptlang.org/docs/)

