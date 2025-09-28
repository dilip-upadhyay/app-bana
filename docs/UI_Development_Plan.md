# UI Development Plan — Custom UI Core ("Studio")

This document outlines the development plan for AppBana's custom UI framework, codenamed "Studio". This plan supersedes all previous plans, including the one based on Angular.

## 1. Vision & Core Principles

The goal is to create a powerful, lightweight, and maintainable no-code/low-code UI development platform that is perfectly tailored to the AppBana backend.

- **Component-Based:** The architecture is built on standard Web Components for maximum interoperability and longevity.
- **Metadata-Driven:** All UIs are rendered from a JSON metadata definition. This separates the "what" from the "how".
- **TypeScript First:** The entire frontend codebase will be in TypeScript for robustness, maintainability, and excellent tooling.
- **Minimal Dependencies:** We will avoid large, opinionated frameworks. Dependencies will be chosen carefully for specific tasks (e.g., `vite` for the build tool).
- **Extensible:** The framework will be designed from the ground up to support plugins for custom components, data connectors, and actions.

## 2. Core Components of "Studio"

"Studio" consists of two cooperating surfaces:
1. **The Builder:** Visual canvas + inspectors → produces page/theme/navigation metadata.
2. **The Runtime:** Deterministic renderer that turns metadata into an interactive app.

## 3. Technology Stack

- **Language:** TypeScript
- **Primitive Rendering:** Native Web Components (Custom Elements / Shadow DOM)
- **Build/Dev:** Vite
- **Bootstrap Helper (temporary):** `lit` only for a few legacy pieces; all new work uses `BaseElement` abstractions.

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
| BaseElement core | Seeded | Lacks diffing / declarative state binding layer |
| Component registry | Seeded | No plugin discovery or lazy loading yet |
| Runtime renderer | Seeded (Phase A skeleton) | Only core demo components registered |
| Metadata model | Partial | Schemas exist; page/theme/navigation/workflow interfaces not yet codified in TS module |
| Page persistence | Not Implemented | No /runtime/app/... endpoints yet |
| Builder canvas | Not Implemented | No drag/drop, inspector, selection, undo/redo |
| Binding system | Not Implemented | Only placeholder ideas in roadmap |
| Theme tokens | Not Implemented | Style strategy doc only; no runtime application pipeline |
| Expression sandbox | Not Implemented | Security considerations listed in guide; engine absent |
| Plugin boundary | Planned | Registry prepared conceptually; contract TBD in Phase E |
| Non-relational adapters | Planned | Design (doc) only; backend accepts relational models |
| Tests (UI) | Missing | No Vitest/JSDom harness yet for metadata → DOM assertions |
| Page versioning/publish | Planned | Roadmap Phase E |
| Offline/PWA | Planned (Nov) | No service worker scaffolding yet |

## 6. Gaps & Risks

| Gap | Risk | Mitigation |
|-----|------|-----------|
| No test harness for renderer | Silent regressions | Introduce Vitest + DOM tests (Phase A exit) |
| No expression sandbox | Security holes if rushed | Defer until sandboxed evaluator implemented (Phase D) |
| No plugin isolation contract | Plugin lock-in | Define lightweight module spec + capability map early in Phase E |
| Missing page persistence endpoints | Builder/runtime divergence | Implement design-time + runtime endpoints before Phase C work starts |
| Lack of undo/redo model | Builder UX friction | Maintain operation log + state snapshots (Phase B) |
| No theming runtime | Inconsistent styling later | Lock token schema before Phase D |

## 7. Immediate Next Actions (Phase A Completion)

The following concrete tasks complete Phase A and unblock Phase B:
1. Add `models/metadata.ts` with: PageMeta, ComponentNode (discriminated union), ThemeMeta, NavigationMeta, Binding, Action types.
2. Implement simple registry bootstrap (`registry.ts`) auto-registering built-ins.
3. Add `demo-page.json` under `app-bana-ui/src/demo/` and load it in `app-renderer.ts`.
4. Expand `Renderer` to recursively mount children + basic prop mapping.
5. Introduce Vitest + single test: load demo metadata → assert DOM structure.
6. Add error placeholder component for unknown type (helps early plugin dev).
7. Update backend packaging to copy `demo-page.json` so `/ui/studio` can load in fat JAR.
8. Document contribution pattern in `README` + `COPILOT_GUIDE` (how to add a component).

Exit Criteria Phase A: `npm test` passes with renderer test and `/ui/studio` renders demo page from JSON.

## 8. Builder MVP (Phase B) Detailed Scope

Features:
- In-memory tree store (normalized by id).
- Selection model + keyboard shortcuts (delete, duplicate, move up/down).
- Property inspector auto-generates form from component schema (field metadata stored per component definition).
- Local draft persistence (localStorage `studio.draft.<pageId>`).
- Import/export (JSON download/upload) with validation.
- Operation log (append-only) powering undo/redo stack (max depth 100).

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

- 2025-09-29: Major expansion — added phases table, status matrix, immediate actions, risk table, and alignment sections.

---

Previous minimal draft retained below for historical trace:

<!-- legacy excerpt intentionally kept for reference -->
<!-- (original short phased outline removed from active guidance; see sections above) -->
