# Grid Layout & Runtime Polish Walkthrough

## Goal
The objective was to achieve a tight, gap-free grid layout and ensure the "Runtime" (Production) view was perfectly clean, free of "Builder hints" like dotted lines and labels.

## Changes

### 1. Removing Phantom Gaps (The "Tight Layout" Fix)
We extracted 16px+ of unwanted whitespace that was preventing fields from touching.

- **Global Input Margins**: Found and removed a `margin: 4px 0` rule in `LivePreview.css` that was forcing gaps on every input.
- **Container Padding**: Identified a hardcoded `padding: 0.5rem` and `border: 1px dashed` in the `ContainerElement` component. Removed these defaults.
- **Data Sanitization**: Implemented a "Hotfix Sanitizer" in both `LivePreview.ts` (Builder) and `Renderer.ts` (Runtime) to intercept legacy grid cells and strip their old padding/gap styles on the fly.

### 2. Cleaning up the Runtime (WYSIWYG)
We ensured that "Builder Guides" (dotted lines, labels) only appear when you are editing, not when you are using the app.

- **CSS Variable Strategy**:
  - `GridElement.ts` now uses CSS variables for borders and labels:
    ```css
    border: 2px dashed var(--grid-outline-color, transparent);
    display: var(--grid-key-display, none);
    ```
  - **Builder (`LivePreview.css`)**: Defines these variables to `block` and `#e5e7eb` (Visible).
  - **Runtime**: Does not define them, so they default to `transparent` and `none` (Invisible).

### 3. Visual Polish
- **Fixed Hover Flash**: Removed `transition: all` from grid cells to prevent border interpolation glitches. Switched from `border` to `outline` to prevent 1px layout shifts on hover.

## Verification Results

### Builder Canvas
- [x] Grid cells touch perfectly when Gap is 0.
- [x] Dotted lines show "Drop Targets".
- [x] Labels (R1C1) help navigation.

### Runtime Preview
- [x] **Clean**: No dotted lines.
- [x] **Clean**: No R1C1 labels.
- [x] **Stable**: No flashing/jumping on hover.
- [x] **Layout**: Matches Builder (tight spacing).

## Files Modified
- `src/components/GridElement.ts` (Grid logic & styles)
- `src/components/ContainerElement.ts` (Removed default borders)
- `src/builder/components/LivePreview.css` (Builder-only variables)
- `src/builder/components/LivePreview.ts` (Builder rendering fix)
- `src/runtime/renderer/Renderer.ts` (Runtime rendering fix)
- `src/builder/components/ComponentLibrary.ts` (New component logic)
