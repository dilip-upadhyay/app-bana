# AppBana Studio UI Module

This module contains the evolving "Studio" frontend: a metadata‑driven runtime + (future) visual builder.

## Contents
- `src/models/metadata.ts` – core metadata shapes (PageMeta, ComponentNode, etc.)
- `src/runtime/renderer/` – minimal runtime walker (`renderPage`) + tests
- `src/components/` – Web Components built on `BaseElement` (container, text, button, unknown placeholder)
- `src/demo/demo-page.json` – sample metadata page rendered at runtime for `/ui/studio`
- `src/core/registry.ts` – component registry & bootstrap helper

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
1. Edit / add components under `src/components/`.
2. Add (or adjust) metadata in `src/demo/demo-page.json` to exercise new components.
3. Run `npm run dev` and open `http://localhost:5173/studio` (dev path) OR run the backend and open `http://localhost:8080/ui/studio` (served from built assets after a build).
4. Write/update tests in `src/runtime/renderer/*.test.ts`.
5. Run `npm run test` (fast) or `npm run verify` (ensures bundle exists).

## Adding a Component
1. Create `YourThingElement.ts` extending `BaseElement`.
2. Define static `observedAttributes` if you want attribute → state reflection.
3. Register the component in runtime before rendering metadata (for now manually in `index.ts`). Future phases will have automatic discovery.
4. Add a node in `demo-page.json` with `"type": "yourThing"` so the renderer can show it.
5. Add / update a test to assert expected DOM output.

## Testing Guidelines
- Renderer tests: target DOM structure + fallback handling (unknown component case).
- Avoid snapshot tests until the structure stabilizes.
- Keep each test self-contained (register only the components it needs).

## Runtime Demo (`/ui/studio`)
When the Java backend is running and this module has been built (`npm run build`), visiting `http://localhost:8080/ui/studio` loads the bundled index which mounts `<app-root>` and renders the demo metadata page.

## Maven Packaging Note
The current Maven build does **not** automatically run `npm run build`. You must run it manually (or add a pre-package step) so that `src/main/resources/ui/dist` exists when the UI module JAR is packaged.

Future improvement: Add a Maven `frontend-maven-plugin` or a simple exec binding to automate the build in the `prepare-package` phase.

## Troubleshooting
| Symptom | Cause | Fix |
|---------|-------|-----|
| 404 at /ui/studio | Bundle missing | Run `npm run build` then rebuild the backend JAR |
| Unknown component placeholder appears | Type not registered | Register it before rendering or implement component |
| Tests fail in CI but pass locally | Missing build step for verify | Ensure `npm ci` + `npm run verify` executed |

## Roadmap (Phase A → B)
- Phase A (current): static metadata demo + tests.
- Phase B: in-memory builder canvas, property inspector, undo/redo.

---
MIT Licensed (pending formal LICENSE file in repo root – placeholder).

