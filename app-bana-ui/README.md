# AppBana Studio UI Module

This module contains the evolving "Studio" frontend: a metadata‑driven runtime + (future) visual builder.

## Component Architecture (Angular-like Structure)

Each component follows a clean 3-file structure similar to Angular:
- **`.ts`** - TypeScript component logic and state management
- **`.css`** - Component styles (imported as inline strings)
- **`.html`** - Template reference/documentation

Components use Lit's `html` tagged template literals for reactive rendering, with styles imported from separate CSS files using Vite's `?inline` query parameter.

## Contents
- `src/models/metadata.ts` – core metadata shapes (PageMeta, ComponentNode, etc.)
- `src/runtime/renderer/` – minimal runtime walker (`renderPage`) + tests
- `src/components/` – Web Components built with Lit (container, text, button, unknown placeholder)
- `src/builder/components/` – Builder UI components (BuilderCanvas, BuilderInspector, TokenPanel, etc.)
- `src/demo/demo-page.json` – sample metadata page rendered at runtime for `/ui/studio`
- `src/core/registry.ts` – component registry & bootstrap helper
- `src/vite-env.d.ts` – TypeScript declarations for CSS/HTML imports

## Scripts
```bash
npm run dev      # Vite dev server (localhost:5173)
npm run build    # Type-check + production bundle -> src/main/resources/ui/dist
npm run preview  # Preview the production bundle
npm run test     # Vitest suite (jsdom)
npm run verify   # Build + basic artifact assertion (index.html + demo string)
```

`npm run verify` is used in CI / local automation to ensure a build happened and the demo content is embedded in the bundle.

## Development Flow
1. Edit / add components under `src/components/` or `src/builder/components/`.
2. Each component should have 3 files: `.ts`, `.css`, and `.html` (reference).
3. Add (or adjust) metadata in `src/demo/demo-page.json` to exercise new components.
4. Run `npm run dev` and open `http://localhost:5173/studio` (dev path) OR run the backend and open `http://localhost:8080/ui/studio` (served from built assets after a build).
5. Write/update tests in `src/runtime/renderer/*.test.ts`.
6. Run `npm run test` (fast) or `npm run verify` (ensures bundle exists).

## Adding a Component

### 1. Create the TypeScript file (`YourComponent.ts`)
```typescript
import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import styles from './YourComponent.css?inline';

@customElement('your-component')
export class YourComponent extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;
  
  @state() private yourState = '';

  render() {
    return html`
      <div class="container">
        <p>${this.yourState}</p>
      </div>
    `;
  }
}
```

### 2. Create the CSS file (`YourComponent.css`)
```css
:host {
  display: block;
}

.container {
  padding: 16px;
}
```

### 3. Create the HTML reference file (`YourComponent.html`)
```html
<!-- Template reference for YourComponent -->
<div class="container">
  <p data-slot="content"></p>
</div>
```

### 4. Register the component
Add your component to the appropriate registry or import it where needed.

### 5. Add to demo metadata
Add a node in `demo-page.json` with `"type": "yourComponent"` so the renderer can show it.

### 6. Write tests
Add / update a test to assert expected DOM output.

## Component Examples

### Builder Components
- **BuilderCanvas** - Tree editor with drag-drop, keyboard shortcuts, command palette
- **BuilderInspector** - Property editor for selected components
- **BuilderShell** - Main layout shell combining canvas and inspector
- **TokenPanel** - Design token editor with undo/redo, import/export

### UI Components
- **AppSidebar** - Navigation sidebar with routing
- **ComponentGallery** - Component showcase grid
- **EntityExplorer** - Full CRUD interface with filtering, pagination, batch operations

## Testing Guidelines
- Renderer tests: target DOM structure + fallback handling (unknown component case).
- Avoid snapshot tests until the structure stabilizes.
- Keep each test self-contained (register only the components it needs).

## Runtime Demo (`/ui/studio`)
When the Java backend is running and this module has been built (`npm run build`), visiting `http://localhost:8080/ui/studio` loads the bundled index which mounts `<app-root>` and renders the demo metadata page.

## Maven Packaging Note
The current Maven build does **not** automatically run `npm run build`. You must run it manually (or add a pre-package step) so that `src/main/resources/ui/dist` exists when the UI module JAR is packaged.

Future improvement: Add a Maven `frontend-maven-plugin` or a simple exec binding to automate the build in the `prepare-package` phase.

## CSS Import Configuration

CSS files are imported using Vite's `?inline` query parameter to load them as strings:
```typescript
import styles from './Component.css?inline';
```

TypeScript declarations for these imports are defined in `src/vite-env.d.ts`:
```typescript
declare module '*.css?inline' {
  const content: string;
  export default content;
}
```

## Troubleshooting
| Symptom | Cause | Fix |
|---------|-------|-----|
| 404 at /ui/studio | Bundle missing | Run `npm run build` then rebuild the backend JAR |
| CSS not loading | Missing ?inline suffix | Import as `from './File.css?inline'` |
| TypeScript errors on CSS imports | Missing type declarations | Ensure `src/vite-env.d.ts` is included in tsconfig |
| Tests fail in CI but pass locally | Missing build step for verify | Ensure `npm ci` + `npm run verify` executed |

## Roadmap (Phase A → B)
- Phase A (current): static metadata demo + tests.
- Phase B: in-memory builder canvas, property inspector, undo/redo.

---
MIT Licensed (pending formal LICENSE file in repo root – placeholder).
