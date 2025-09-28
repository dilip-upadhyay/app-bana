# AppBana UI Styling Guide (Long-term Policy)

Status: Adopted (2025-09-21)
Scope: Angular 21 workspace (studio/designer/runtime) to be added per UI_Development_Plan.md

Goals
- Fast iteration in the studio/designer app without large CSS dependencies.
- Predictable, token-driven theming in the runtime renderer.
- Avoid reliance on build-time purge/safelist of dynamic utility classes.

Principles
- Single source of truth: theme tokens defined as CSS variables.
- Angular Material for interactive components and accessibility; custom utilities only for layout/spacing where helpful.
- Runtime styling is deterministic and token-driven; no arbitrary classes from user JSON.

1) Design tokens (CSS variables)
- Define a small, extensible set of CSS variables for colors, spacing, radii, and typography.
- Support dark mode via a `.dark` class on the html/body root.

Example (tokens.css)
```css
:root {
  /* Colors */
  --color-bg: #ffffff;
  --color-text: #111111;
  --color-primary: #1e88e5; /* match Material primary */
  --color-primary-contrast: #ffffff;
  --color-surface: #f7f7f8;
  /* Spacing scale (4px grid) */
  --space-0: 0px;
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 20px;
  --space-6: 24px;
  --space-7: 28px;
  --space-8: 32px;
  /* Radii */
  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;
  /* Typography */
  --font-sans: ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Arial, Noto Sans, "Apple Color Emoji", "Segoe UI Emoji";
  --text-sm: 0.875rem;
  --text-md: 1rem;
  --text-lg: 1.125rem;
}

.dark {
  --color-bg: #0b0c0f;
  --color-text: #e7e9ee;
  --color-primary: #90caf9;
  --color-primary-contrast: #0b0c0f;
  --color-surface: #16181d;
}
```

2) Angular Material alignment
- Use Angular Material theming as the base; align our CSS variables to the chosen Material palettes.
- Strategy:
  - Define Material theme (SCSS) per Angular Material docs.
  - Ensure component styles read CSS variables for colors/spacing where practical (host styles), so toggling `.dark` updates look.
  - Use Material typography configs; map to CSS variables for consistent use in custom components.

3) Tiny local utility layer (studio only)
- In apps/studio (designer shell), add a minimal set of utilities for layout/spacing. Keep it small, readable, and token-backed.

Example (utilities.css)
```css
/* Layout */
.u-flex { display: flex; }
.u-col { flex-direction: column; }
.u-center { align-items: center; justify-content: center; }
.u-grid { display: grid; }

/* Spacing (margin/padding using the token scale) */
.u-m-0 { margin: var(--space-0); }
.u-m-1 { margin: var(--space-1); }
.u-m-2 { margin: var(--space-2); }
/* ...extend through .u-m-8 as needed */
.u-p-0 { padding: var(--space-0); }
.u-p-1 { padding: var(--space-1); }
.u-p-2 { padding: var(--space-2); }
/* ...extend through .u-p-8 as needed */

/* Surfaces */
.u-card { background: var(--color-surface); border-radius: var(--radius-md); }
```
Notes
- Prefer semantic Angular components + CSS variables over piling on utilities.
- Utilities are for the studio app developer experience; avoid sprinkling them in runtime components.

4) Runtime renderer policy
- Do not accept arbitrary class names from design JSON that rely on build-time utility frameworks.
- Map design properties to styles or to a fixed, documented set of classes.
- If a small set of classes is needed, expose a "safe list" (e.g., text sizes: sm/md/lg; spacing: 0..4; alignment enums) and document it in the DSL.

5) Theming and dark mode
- Toggle dark mode by applying `.dark` on html/body (or a top-level container) in studio/runtime.
- Ensure Material theme and CSS variables are consistent across modes.

6) Performance and bundle size
- Keep the utility layer tiny; rely on CSS variables and Material for the rest.
- Avoid large CSS frameworks in the runtime; keep CSS critical-path minimal.

7) Implementation plan (when Angular workspace is added)
- Create `apps/studio/src/styles/tokens.css` and `apps/studio/src/styles/utilities.css`; import both in `styles.scss`.
- Create `libs/runtime/src/lib/styles/tokens.css`; import in the runtime lib root; avoid utilities here.
- Document the safe class set (if any) in the DSL docs and enforce via schema validation.

References
- UI_Development_Plan.md — Styling policy section
- Angular Material Theming — official docs for MDC theming

