# UI Development Plan — Custom UI Core ("Studio")

<!-- Updated 2025-10-26: Component architecture update - 3-file structure -->
This document outlines the development plan for AppBana's custom UI framework, codenamed "Studio". This plan supersedes all previous plans, including the one based on Angular (legacy references retained elsewhere for anchor compatibility only).

## 1. Vision & Core Principles

The goal is to create a powerful, lightweight, and maintainable no-code/low-code UI development platform that is perfectly tailored to the AppBana backend.

- **Component-Based:** The architecture is built on Lit Web Components for maximum interoperability and longevity.
- **Metadata-Driven:** All UIs are rendered from a JSON metadata definition. This separates the "what" from the "how".
- **TypeScript First:** The entire frontend codebase will be in TypeScript for robustness, maintainability, and excellent tooling.
- **Angular-like Structure:** Each component follows a 3-file pattern (`.ts`, `.css`, `.html`) for better organization and maintainability.
- **Minimal Dependencies:** We use Lit for Web Components, Vite for build tooling, and avoid large opinionated frameworks.
- **Extensible:** The framework will be designed from the ground up to support plugins for custom components, data connectors, and actions.

## 2. Core Components of "Studio"

"Studio" consists of two cooperating surfaces:
1. **The Builder:** Visual canvas + inspectors → produces page/theme/navigation metadata.
2. **The Runtime:** Deterministic renderer that turns metadata into an interactive app.

## 3. Technology Stack

- **Language:** TypeScript
- **Component Library:** Lit (Web Components)
- **Build/Dev:** Vite
- **Component Structure:** 3-file pattern per component:
  - `.ts` - Component logic, state management, and Lit templates
  - `.css` - Component styles (imported as inline strings via Vite)
  - `.html` - Template reference/documentation
- **Type Safety:** TypeScript declarations for CSS imports in `vite-env.d.ts`

## 3.1 Component Architecture

Each component follows a clean separation of concerns:

```
ComponentName/
├── ComponentName.ts    # Logic + rendering
├── ComponentName.css   # Styles
└── ComponentName.html  # Reference documentation
```

**Example Component Structure:**
```typescript
// ComponentName.ts
import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import styles from './ComponentName.css?inline';

@customElement('component-name')
export class ComponentName extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;
  
  @state() private data = '';

  render() {
    return html`<div class="container">${this.data}</div>`;
  }
}
```

**CSS Import:**
```typescript
import styles from './Component.css?inline';
```

**Type Declaration (vite-env.d.ts):**
```typescript
declare module '*.css?inline' {
  const content: string;
  export default content;
}
```

## 4. Phased Development Plan (Versioned)

Phases are incremental; each ends with a demonstrable success criterion.

| Phase | Name | Primary Outcomes | Success Gate |
|-------|------|------------------|--------------|
| A | Foundation | Metadata interfaces, registry, minimal renderer (Container/Text/Button), BaseElement v1 | Render static demo page JSON in browser |
| B | Builder MVP | Canvas add/remove/reorder, property inspector, local draft persistence, import/export | Modify page structure and re-render without reload |
| C | Runtime MVP | Load page JSON from backend endpoints, basic bindings (static + form), simple actions (navigate/setState) | Interactive form with state mutation |
| D | Enhancements | Expression engine (sandbox), validation, theme token application, navigation model | Themed navigation + dynamic expression-driven field |
| E | Advanced Platform | Versioning/publish pipeline, plugin system, realtime channel hooks, workflow action hooks | Publish + rollback version, load external plugin component |
| F (Stretch) | Distribution | Offline/PWA caching, multi-actor workflow UI, marketplace surfacing | First installable PWA w/ cached page + queued write replay |

## 5. Current Implementation Status (Snapshot)

| Area | Status | Notes |
|------|--------|-------|
| Component structure | ✅ Done | 3-file pattern implemented across all components |
| CSS separation | ✅ Done | All styles moved to separate .css files with ?inline imports |
| Type declarations | ✅ Done | vite-env.d.ts provides CSS import types |
| BuilderCanvas | ✅ Done | Tree editor with drag-drop, palette, inline editing |
| BuilderInspector | ✅ Done | Property editor for selected nodes |
| BuilderShell | ✅ Done | Layout shell combining canvas and inspector |
| TokenPanel | ✅ Done | Design token editor with undo/redo, categories |
| AppSidebar | ✅ Done | Navigation with routing |
| ComponentGallery | ✅ Done | Component showcase |
| EntityExplorer | ✅ Done | Full CRUD interface with filters, pagination, batch ops |
| Component registry | ✅ Done | Dynamic import ensures lazy core component load |
| Runtime renderer | ☐ Pending | `app-renderer.ts` stub; recursive walker not implemented |
| Metadata model | ✅ Initial subset | `PageMeta`, `ComponentNode` minimal; extended workflow/theme/nav still doc-only |
| Page persistence | Not Implemented | Deferred to Phase C |
| Binding system | Not Implemented | Phase C/D |
| Theme tokens | Not Implemented | Phase D |
| Expression sandbox | Not Implemented | Phase D (security gating) |
| Plugin boundary | Planned | Phase E (global registration hook draft) |
| Tests (UI) | ☐ Pending | Vitest configured; first renderer test missing |
| Page versioning/publish | Planned | Phase E |
| Offline/PWA | Planned | Phase F (Nov scope) |

## 6. Component Development Guidelines

### 6.1 Creating New Components

1. **Create the TypeScript file** with Lit decorators and state management
2. **Create the CSS file** with scoped styles for the component
3. **Create the HTML file** as reference documentation
4. **Import CSS** using `?inline` suffix
5. **Apply styles** using `css\`${unsafeCSS(styles)}\``
6. **Write tests** for the component behavior

### 6.2 Best Practices

- Keep components focused on a single responsibility
- Use `@state()` for reactive internal state
- Use `@property()` for public component API
- Leverage Shadow DOM for style encapsulation
- Document component props and events in the .html reference file
- Write comprehensive tests for complex interactions

### 6.3 Builder Component Examples

**BuilderCanvas** - 3 files totaling ~600 lines:
- Tree editor with keyboard navigation
- Drag-drop reordering
- Command palette (Cmd/Ctrl+P)
- Inline editing (Enter on text nodes)
- Local storage for expanded state

**TokenPanel** - 3 files totaling ~400 lines:
- Category-based token organization
- Undo/redo with keyboard shortcuts
- Import/export JSON snapshots
- Revision timeline
- Highlighted recent changes with diffs

## 7. Gaps & Risks

| Gap | Risk | Mitigation |
|-----|------|-----------|
| No test harness for renderer | Silent regressions | Introduce Vitest + DOM tests (Phase A exit) |
| No expression sandbox | Security holes if rushed | Defer until sandboxed evaluator implemented (Phase D) |
| No plugin isolation contract | Plugin lock-in | Define lightweight module spec + capability map early in Phase E |
| Missing page persistence endpoints | Builder/runtime divergence | Implement design-time + runtime endpoints before Phase C work starts |
| Lack of undo/redo model | Builder UX friction | Maintain operation log + state snapshots (Phase B) |
| No theming runtime | Inconsistent styling later | Lock token schema before Phase D |

## 8. Immediate Next Actions (Phase A Completion) — Updated

The following concrete tasks complete Phase A and unblock Phase B:
1. Implement recursive renderer in `app-bana-ui/src/app-renderer.ts`:
   - Build node index (id → node)
   - Create DOM elements via registry; apply shallow props (e.g., text → attribute or inner text for text component)
   - Append children depth-first; insert unknown placeholder when type not registered
2. Add `studio.html` (or `index-studio.html`) + bootstrap script loading demo JSON and invoking renderer (exposed at `/ui/studio`).
3. Adjust UI build/publish step to copy `studio.html`, `demo-page.json`, and bundled assets into service JAR (parallel to existing legacy UIs).
4. Write first Vitest test (`Renderer.demo.test.ts`):
   - Import demo metadata
   - Invoke renderer into a jsdom container
   - Assert: container element count, expected text content, presence of unknown placeholder element (`studio-unknown`).
5. Document component contribution pattern (README + Copilot Guide finalization) after renderer stabilization (prop passing detail).
6. Update `TODO.md` & `.github/COPILOT_GUIDE.md` to mark renderer & test as DONE once complete.

_Exit Gate Reminder:_ Do **not** begin canvas (selection / undo / drag) until renderer + test + packaging are green.

## 9. Builder MVP (Phase B) Detailed Scope

Features:
- In-memory tree store (normalized by id). ✅ DONE
- Selection model + keyboard shortcuts (delete, duplicate, move up/down). ✅ DONE
- Property inspector auto-generates form from component schema (field metadata stored per component definition). ✅ DONE
- Local draft persistence (localStorage `studio.draft.<pageId>`). ✅ DONE
- Import/export (JSON download/upload) with validation. ✅ PARTIAL (export done)
- Operation log (append-only) powering undo/redo stack (max depth 100). ✅ DONE
- Component separation (3-file structure). ✅ DONE

Non-Goals in Phase B:
- Server persistence.
- Complex layout engines (grid designer) — use simple column/row container approach.

## 9. Runtime MVP (Phase C) Detailed Scope

Add backend endpoints (admin unless noted):
- `POST /design/page` create/update page metadata
- `GET /design/app/{id}/pages` list
- `GET /design/page/{id}` fetch one
- `GET /runtime/app/{code}/page/{path}` runtime fetch (public/read)
- `GET /runtime/app/{code}/manifest` list pages + theme id

Runtime Additions:
- Binding resolution (static literal, form state fields, page params subset)
- Simple actions: navigate (client-side), setState (local page store)

## 10. Expressions & Validation (Phase D)

- Introduce sandboxed evaluator (no `eval`); whitelisted functions only.
- Expression binding form in inspector with live validation.
- UI-level field validation (required, pattern, custom expression returning boolean).
- Theme token pipeline: build CSS variables from theme JSON and scope via root host attribute.

## 11. Advanced Platform (Phase E)

- Versioning: semantic version + diff preview + rollback.
- Publish pipeline: mark a version active for runtime fetch.
- Plugin system: dynamic module registration (`window.AppBanaStudio.registerComponent(...)`).
- Realtime channels: subscription binding → component refresh on message.
- Workflow hooks: design-time action mapping to workflow transitions.

## 12. Distribution & Resilience (Phase F)

- PWA: service worker pre-cache static + last N pages.
- Background sync queue for failed mutations (retry policy exponential backoff).
- Marketplace manifest consumption (signed JSON with component descriptors).

## 13. Theming Strategy (Preview)

Theme JSON → token map → compiled into a `<style>` block (scoped) + dynamic class injection.
Token categories: colors, typography, spacing scale, radii, shadows, zIndex. Future extension: motion (durations, easing).

## 14. Non-Relational & External Datasources

Frontend will model `modelKind != relational` (document/apiResource) but backend ignores until feature flag enabled. Adapter interface defined in Copilot Guide; UI gating by disabling unsupported kinds with explanatory tooltip.

## 15. Testing Strategy

| Layer | Tool | Initial Tests |
|-------|------|---------------|
| Metadata validation | Vitest | Parse demo page JSON (schema compliance) |
| Renderer | Vitest + jsdom | Container/Text/Button mount order |
| Registry | Vitest | Unknown component fallback |
| Actions (later) | Vitest | setState + navigate behavior |
| Expression engine | Vitest | Safe evaluation & error isolation |

## 16. Contribution Workflow (UI Module)

1. Create component under `src/components/` (export class extends BaseElement).
2. Provide static `definition` object: `{ type, propsSchema, defaultProps }`.
3. Register in `registry.ts` on bootstrap.
4. Add metadata sample to `demo-page.json` to exercise component.
5. Add/extend tests.

## 17. Alignment With Higher-Level Docs

- **COPILOT_GUIDE.md:** Now references explicit gaps and phased plan (see sections 10 & 17 there). This file is the authoritative *depth* source; Copilot Guide is a *snapshot*.
- **README.md:** Links to this plan; only summarizes current Studio phase and how to experiment with demo page.
- **TODO.md:** Must track box-level tasks mapped to phases; update when Phase A exit achieved.

## 18. Open Questions

| Topic | Question | Target Phase |
|-------|----------|--------------|
| Security | CSP + iframe sandbox for plugin isolation? | E |
| Expressions | Deterministic time/locale injection? | D |
| Undo/Redo | Persist operation log across reload? | B (maybe B2) |
| Theming | Multi-theme hot swap vs full page reload? | D |
| Realtime | Backoff strategy standardization? | E |

## 19. Success Metrics

| Metric | 30-Day Target | 90-Day Target |
|--------|---------------|---------------|
| Page render latency (cold) | < 120ms | < 80ms |
| Bundle (runtime core) | < 40KB gzip | < 55KB with tokens & expressions |
| Unit test coverage (UI module) | 25% | 65% |
| Mean time to add new component | < 15 min | < 8 min |

## 20. Changelog (Plan Updates)

- 2025-10-26: Updated component architecture details (3-file structure) and revised Builder MVP scope.
- 2025-10-01: Added progress matrix (Section 5) and revised Immediate Next Actions (Section 7) to reflect partial Phase A completion.
- 2025-09-29: Major expansion — added phases table, status matrix, immediate actions, risk table, and alignment sections.

---

Previous minimal draft retained below for historical trace:

<!-- legacy excerpt intentionally kept for reference -->
<!-- (original short phased outline removed from active guidance; see sections above) -->
