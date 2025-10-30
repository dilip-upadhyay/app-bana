# GitHub Copilot Instructions for AppBana

## Project Overview
AppBana is a **metadata-driven platform** that generates end-to-end functionality from a single source of truth. The core flow: `Schema Definition (Metadata) → Database Table → REST CRUD APIs → UI Pages (Runtime)`. Changes to metadata propagate automatically through all layers.

## Technology Stack
- **Backend**: Java 25 LTS with virtual threads, JDK HttpServer (default) or Tomcat, H2 embedded database, HikariCP, Jackson, Maven multi-module build
- **Frontend**: TypeScript 5.2.2+, Lit 3.1.4 Web Components, Vite 5.3.1+ dev server, Vitest 1.5.0+ testing
- **Architecture**: Shadow DOM component system, custom element registry, metadata-driven rendering

## Critical Development Patterns

### 1. Component System (3-File Pattern)
All UI components follow this structure:
```typescript
// MyComponent.ts
import { customElement } from 'lit/decorators.js';
import { html } from 'lit';
import { BaseElement } from '../core/BaseElement';
import styles from './MyComponent.css?inline';

@customElement('my-component')
export class MyComponent extends BaseElement {
  static styles = styles;
  
  render() {
    return html`<div>Content</div>`;
  }
}
```
- **MyComponent.ts**: Logic + template (extends `BaseElement`)
- **MyComponent.css**: Scoped Shadow DOM styles (imported with `?inline`)
- **MyComponent.html**: Reference template (optional, for large templates)

### 2. Component Registration
All components must register in `src/core/registry.ts`:
```typescript
registerComponent('my-component', () => import('../components/MyComponent'));
```
The registry uses dynamic imports for lazy-loading. Call `ensureCoreRegistered()` before using components.

### 3. Metadata Structure
Core interfaces in `src/models/metadata.ts`:
```typescript
interface ComponentNode {
  id: string;           // Unique identifier
  type: string;         // Component type (e.g., 'container', 'text', 'button')
  props?: Record<string, any>;
  children?: string[];  // Array of child node IDs
  style?: Record<string, string>;
}

interface PageMeta {
  metaVersion?: string;
  id: string;
  name: string;
  path: string;
  rootId: string;       // REQUIRED - ID of root node
  nodes: ComponentNode[]; // Array of all nodes in page tree
  type?: 'page' | 'component';
}
```
**Common Error**: Using `components` property instead of `nodes` in PageMeta.

### 4. BaseElement Pattern
All custom elements extend `BaseElement` from `src/core/BaseElement.ts`:
- **Shadow DOM**: Automatically attached, provides style encapsulation
- **Reactive State**: Use `setState(newState)` to trigger re-renders
- **Lifecycle**: Override `connectedCallback()`, `disconnectedCallback()`
- **Styles**: Return CSS string from static `styles` property

### 5. API Client Pattern
Use `ApiClient` from `src/core/api-client.ts` for all HTTP requests:
```typescript
import { apiClient } from './api-client';

// GET request
const data = await apiClient.get<MyType>('/api/resource');

// POST with body
const result = await apiClient.post('/api/resource', { name: 'value' });
```
Backend API runs on port 8080, Vite proxies requests in dev mode.

## Build & Run Commands

### Backend (Java)
```bash
# Build shaded JAR with UI assets
mvn clean package

# Run backend server (port 8080)
java -jar app-bana-service/target/app-bana-service-1.0-SNAPSHOT.jar
```

### Frontend (TypeScript/Vite)
```bash
cd app-bana-ui

# Development server (port 5173)
npm run dev

# Production build → src/main/resources/ui/dist/
npm run build

# Run tests
npm test
```

### Full Stack
```bash
# Start UI dev server (root of project)
./run-ui.sh dev     # or ./start-dev.sh

# Backend + Frontend together
# Terminal 1: java -jar app-bana-service/target/*.jar
# Terminal 2: cd app-bana-ui && npm run dev
```

## Studio Builder Architecture
The visual page builder (`src/builder/components/`) has 3-panel layout:
- **Left**: Component library (component-gallery.ts)
- **Center**: Canvas with drag-drop (BuilderCanvas.ts)
- **Right**: Properties inspector (property-panel.ts)

Key state management:
- `TreeStore`: Component tree structure
- `AppStore`: Global app state (pages, routing)
- `PageManager`: Page creation/template wizard

## Key Files Reference
- **Core Framework**: `src/core/BaseElement.ts`, `src/core/registry.ts`
- **Metadata Types**: `src/models/metadata.ts`
- **API Client**: `src/core/api-client.ts`
- **Studio Builder**: `src/builder/components/PageManager.ts`, `BuilderCanvas.ts`
- **Architecture Docs**: `docs/01-ARCHITECTURE.md`, `docs/02-DEVELOPMENT_GUIDE.md`
- **Roadmap**: `docs/03-ROADMAP.md`

## Common Pitfalls
1. **Forgetting `rootId`**: PageMeta requires `rootId` field pointing to root node
2. **Wrong property names**: Use `nodes` not `components` in PageMeta
3. **Missing registration**: Components must be registered in registry.ts
4. **CSS imports**: Always use `?inline` suffix for CSS imports
5. **Async registry**: Call `ensureCoreRegistered()` before accessing components
6. **Port conflicts**: Backend uses 8080, frontend dev server uses 5173

## Testing Conventions
- Unit tests: `*.test.ts` files colocated with source
- Run with: `npm test` (uses Vitest + jsdom)
- Test component behavior, not implementation details
- Mock API calls using Vitest's `vi.mock()`

## Code Style
- **TypeScript**: Strict mode enabled, prefer interfaces over types
- **Naming**: PascalCase for classes/components, camelCase for variables/functions
- **Imports**: Group by: external libs → core → components → styles
- **Comments**: Document complex logic, avoid obvious comments

## Integration Points
- **Metadata → Backend**: POST to `/api/schema` to create entities
- **Backend → Frontend**: GET from `/api/{entity}` for CRUD operations
- **Studio → Runtime**: PageMeta serialized to JSON, loaded by Renderer
- **Registry → Components**: Dynamic imports ensure lazy-loading
