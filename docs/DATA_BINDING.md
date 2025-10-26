# Data Binding in AppBana Studio UI

This document describes the data binding patterns currently implemented and planned for AppBana Studio UI components.

## Current Implementation (Phase A/B)

### 1. Reactive State Binding (`@state`)

**What it is:** Internal component state that automatically triggers re-renders when changed.

**Usage:**
```typescript
@state() private count = 0;
@state() private isActive = false;
@state() private items: string[] = [];

private increment() {
  this.count++; // Automatic re-render
}
```

**Examples in codebase:**
- **BuilderCanvas:** `selectedId`, `expanded`, `paletteOpen`, `toast`
- **EntityExplorer:** `entities`, `loadingData`, `result`, `query`
- **TokenPanel:** `tokens`, `undoable`, `redoable`, `announce`

**Characteristics:**
- ✅ Automatic reactivity
- ✅ Private to component
- ✅ No attribute reflection
- ✅ Triggers Lit's update lifecycle

### 2. Property Binding (`.value=`)

**What it is:** One-way data binding from component to DOM element properties.

**Usage:**
```typescript
render() {
  return html`
    <input .value=${this.inputText} />
    <select .value=${this.selectedOption}>
      ${this.options.map(opt => html`<option value=${opt}>${opt}</option>`)}
    </select>
  `;
}
```

**Examples in codebase:**
- **BuilderInspector:** Input field binding to node props
- **EntityExplorer:** Filter inputs, query parameters
- **TokenPanel:** Token value inputs

**Characteristics:**
- ✅ Sets element property (not attribute)
- ✅ Works with complex types (objects, arrays)
- ✅ One-way: component → DOM

### 3. Event Binding (`@event`)

**What it is:** Listening to DOM events and updating state.

**Usage:**
```typescript
render() {
  return html`
    <input 
      .value=${this.text} 
      @input=${(e: Event) => this.text = (e.target as HTMLInputElement).value} />
    <button @click=${() => this.handleClick()}>Click</button>
  `;
}
```

**Examples in codebase:**
- **BuilderCanvas:** `@click`, `@dragstart`, `@dragover`, `@drop`, `@keydown`
- **EntityExplorer:** `@input`, `@change`, `@click` for filters and forms
- **TokenPanel:** `@input` for token editing

**Characteristics:**
- ✅ Standard DOM event listeners
- ✅ TypeScript event typing
- ✅ Automatic `this` binding

### 4. Two-Way Binding Pattern

**What it is:** Manual two-way binding using property + event.

**Usage:**
```typescript
@state() private inputValue = '';

render() {
  return html`
    <input 
      .value=${this.inputValue}
      @input=${(e: Event) => {
        this.inputValue = (e.target as HTMLInputElement).value;
      }} />
  `;
}
```

**Examples in codebase:**
- **BuilderInspector:** Property editor updates
- **EntityExplorer:** All form inputs (query, filters, batch input)
- **TokenPanel:** Token value editing with live preview

**Characteristics:**
- ✅ Controlled component pattern
- ✅ State is source of truth
- ✅ Predictable updates

### 5. Attribute Binding

**What it is:** Setting HTML attributes on elements.

**Usage:**
```typescript
render() {
  return html`
    <div 
      id=${this.nodeId}
      class=${this.isActive ? 'active' : ''}
      ?disabled=${this.isDisabled}
      aria-label=${this.label}>
    </div>
  `;
}
```

**Binding Types:**
- `attr=${value}` - String attribute
- `?attr=${bool}` - Boolean attribute (presence/absence)
- `.prop=${value}` - Property assignment

**Examples in codebase:**
- **BuilderCanvas:** `data-selected`, `aria-selected`, `draggable`, `role`
- **EntityExplorer:** `aria-label`, `?disabled`, `placeholder`

### 6. Conditional Rendering

**What it is:** Show/hide elements based on state.

**Usage:**
```typescript
render() {
  return html`
    ${this.isLoading 
      ? html`<div>Loading...</div>` 
      : html`<div>Content: ${this.data}</div>`}
    
    ${this.error ? html`<span class="error">${this.error}</span>` : null}
  `;
}
```

**Examples in codebase:**
- **BuilderCanvas:** Palette dialog, toast notifications, edit mode
- **EntityExplorer:** Loading states, error messages, empty states
- **TokenPanel:** Snapshot textarea, revision timeline

### 7. List Rendering

**What it is:** Rendering arrays of data.

**Usage:**
```typescript
@state() private items = ['a', 'b', 'c'];

render() {
  return html`
    <ul>
      ${this.items.map(item => html`<li>${item}</li>`)}
    </ul>
  `;
}
```

**Examples in codebase:**
- **BuilderCanvas:** Tree nodes, palette items, children
- **EntityExplorer:** Filter rows, result table rows, entity list
- **TokenPanel:** Category sections, token rows, revisions

### 8. Store/Global State (Custom Pattern)

**What it is:** Shared state across multiple components using a store pattern.

**Usage:**
```typescript
// Store
export class TreeStore {
  private listeners: Array<() => void> = [];
  private page: PageMeta;
  
  onChange(callback: () => void) {
    this.listeners.push(callback);
  }
  
  private notify() {
    this.listeners.forEach(cb => cb());
  }
}

// Component
connectedCallback() {
  currentStore?.onChange(() => {
    this.page = currentStore.getPage();
    this.requestUpdate();
  });
}
```

**Examples in codebase:**
- **TreeStore:** Shared page state for BuilderCanvas and BuilderInspector
- **TokenStore:** Shared design tokens with undo/redo

**Characteristics:**
- ✅ Shared state across components
- ✅ Observer pattern
- ✅ Manual subscription/unsubscription

## NOT Currently Implemented (Planned for Phase C/D)

### 9. Metadata-Driven Bindings (Phase C)

**Planned:** Bindings defined in JSON metadata that the runtime resolver processes.

**Example Metadata:**
```json
{
  "id": "name-input",
  "type": "input",
  "props": {
    "label": "Name",
    "binding": {
      "kind": "form",
      "field": "firstName"
    }
  }
}
```

**Binding Types (Planned):**
- `static` - Literal value
- `form` - Form field binding
- `page` - Page parameter
- `expr` - Expression evaluation (Phase D)
- `data` - Backend data source (Phase D)

### 10. Expression Bindings (Phase D)

**Planned:** Sandbox-safe expression evaluation.

**Example:**
```json
{
  "props": {
    "text": {
      "kind": "expr",
      "expr": "user.firstName + ' ' + user.lastName"
    },
    "visible": {
      "kind": "expr",
      "expr": "user.role === 'admin'"
    }
  }
}
```

**Safety Features:**
- ⏳ Sandboxed evaluator (no `eval`)
- ⏳ Whitelisted functions only
- ⏳ Error boundary handling

### 11. Data Source Bindings (Phase D)

**Planned:** Binding to backend API responses.

**Example:**
```json
{
  "binding": {
    "kind": "data",
    "source": "users",
    "query": {
      "filter": "status:ACTIVE",
      "limit": 10
    }
  }
}
```

### 12. Real-time Bindings (Phase E)

**Planned:** WebSocket/MQTT subscriptions.

**Example:**
```json
{
  "binding": {
    "kind": "channel",
    "channel": "orders.updates",
    "transform": "latest"
  }
}
```

## Comparison with Angular

| Feature | AppBana (Current) | Angular |
|---------|-------------------|---------|
| Template syntax | Lit `html` tagged templates | Angular templates |
| One-way binding | `.prop=${value}` | `[prop]="value"` |
| Event binding | `@event=${handler}` | `(event)="handler()"` |
| Two-way binding | Manual (prop + event) | `[(ngModel)]="value"` |
| Conditional | `${cond ? a : b}` | `*ngIf="cond"` |
| List rendering | `.map()` | `*ngFor="let item of items"` |
| Reactive state | `@state()` | RxJS Observables/Signals |
| Form binding | Manual controlled | FormsModule/ReactiveFormsModule |
| Dependency Injection | Manual | Built-in DI system |

## Best Practices

### 1. Use @state for Internal State
```typescript
// ✅ Good
@state() private isOpen = false;

// ❌ Avoid
private isOpen = false; // Won't trigger re-render
```

### 2. Controlled Components
```typescript
// ✅ Good - State is source of truth
<input .value=${this.text} @input=${e => this.text = e.target.value} />

// ❌ Avoid - Uncontrolled
<input @change=${e => this.processInput(e)} />
```

### 3. Avoid Direct DOM Manipulation
```typescript
// ✅ Good - Declarative
render() {
  return html`<div class=${this.isActive ? 'active' : ''}></div>`;
}

// ❌ Avoid - Imperative
this.renderRoot.querySelector('.box').classList.add('active');
```

### 4. Use Type-Safe Events
```typescript
// ✅ Good
@input=${(e: Event) => {
  this.value = (e.target as HTMLInputElement).value;
}}

// ❌ Avoid
@input=${(e: any) => this.value = e.target.value}
```

### 5. Memoize Expensive Computations
```typescript
// ✅ Good - Cache derived state
private _filteredItems?: Item[];
private _lastQuery = '';

get filteredItems() {
  if (this.query !== this._lastQuery) {
    this._filteredItems = this.items.filter(i => i.name.includes(this.query));
    this._lastQuery = this.query;
  }
  return this._filteredItems;
}
```

## Performance Considerations

### 1. Batch State Updates
```typescript
// ✅ Good - Single render
this.updateState({ count: 5, isActive: true });

// ❌ Avoid - Multiple renders
this.count = 5;
this.isActive = true;
```

### 2. Use Keys for Lists
```typescript
// ✅ Good - Efficient re-rendering
${this.items.map(item => html`
  <div key=${item.id}>${item.name}</div>
`)}
```

### 3. Debounce Expensive Operations
```typescript
private debounceTimer?: number;

private onInput(value: string) {
  clearTimeout(this.debounceTimer);
  this.debounceTimer = setTimeout(() => {
    this.performSearch(value);
  }, 300) as any;
}
```

## Migration Path

### From Current to Metadata-Driven (Phase C)

**Current (Manual):**
```typescript
render() {
  return html`<input .value=${this.name} @input=${this.handleInput} />`;
}
```

**Future (Metadata-Driven):**
```json
{
  "type": "input",
  "props": {
    "binding": { "kind": "form", "field": "name" }
  }
}
```

The runtime resolver will handle the binding automatically.

## Resources

- [Lit Reactive Properties](https://lit.dev/docs/components/properties/)
- [Lit Templates](https://lit.dev/docs/templates/overview/)
- [Lit Lifecycle](https://lit.dev/docs/components/lifecycle/)
- [Web Components Best Practices](https://lit.dev/docs/components/best-practices/)

