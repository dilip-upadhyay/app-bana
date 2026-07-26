/**
 * Button.test.tsx — Sprint 3 post-review coverage for the shared
 * button primitive. Uses `renderToStaticMarkup` so we stay in Node without
 * pulling in jsdom (matches the rest of the runtime test suite).
 *
 * Covers:
 *   - variant → correct utility class combination
 *   - size    → correct size utility class
 *   - loading → aria-busy + disabled + spinner + hides icon
 *   - default type is 'button' (so accidental clicks don't submit forms)
 *   - className is composed, not replaced
 *   - icon renders when not loading
 */
import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { createElement } from 'react';
import { Button } from './Button';

function render(el: ReturnType<typeof createElement>): string {
  return renderToStaticMarkup(el);
}

describe('Button', () => {
  it('renders primary variant by default with medium size', () => {
    const html = render(createElement(Button, {}, 'Save'));
    expect(html).toContain('appbana-btn-primary');
    expect(html).toContain('appbana-btn-md');
    expect(html).toContain('type="button"');
    expect(html).toContain('Save');
  });

  it.each(['primary', 'secondary', 'tertiary', 'danger', 'ghost'] as const)(
    'applies %s variant class',
    (variant) => {
      const html = render(createElement(Button, { variant }, 'X'));
      expect(html).toContain(`appbana-btn-${variant}`);
    },
  );

  it.each(['sm', 'md', 'lg'] as const)('applies %s size class', (size) => {
    const html = render(createElement(Button, { size }, 'X'));
    expect(html).toContain(`appbana-btn-${size}`);
  });

  it('loading state disables + sets aria-busy + hides icon', () => {
    const icon = createElement('svg', { 'data-testid': 'user-icon' });
    const html = render(createElement(Button, { loading: true, icon }, 'Loading'));
    expect(html).toContain('aria-busy="true"');
    expect(html).toContain('disabled=""');
    // spinner emits its own <svg class="animate-spin"> — user-supplied icon
    // must NOT appear while loading
    expect(html).not.toContain('data-testid="user-icon"');
    expect(html).toContain('animate-spin');
  });

  it('renders user-supplied icon when not loading', () => {
    const icon = createElement('svg', { 'data-testid': 'plus-icon' });
    const html = render(createElement(Button, { icon }, 'New'));
    expect(html).toContain('data-testid="plus-icon"');
    expect(html).not.toContain('animate-spin');
  });

  it('respects explicit disabled prop even when not loading', () => {
    const html = render(createElement(Button, { disabled: true }, 'X'));
    expect(html).toContain('disabled=""');
    expect(html).not.toContain('aria-busy');
  });

  it('composes user className with the variant/size classes', () => {
    const html = render(createElement(Button, { className: 'my-custom' }, 'X'));
    expect(html).toContain('appbana-btn-primary');
    expect(html).toContain('appbana-btn-md');
    expect(html).toContain('my-custom');
  });

  it('respects type=submit when passed explicitly', () => {
    const html = render(createElement(Button, { type: 'submit' }, 'Submit'));
    expect(html).toContain('type="submit"');
  });

  it('renders no <span> when children are absent', () => {
    // Icon-only buttons should not emit an empty <span> that adds whitespace.
    const icon = createElement('svg', { 'data-testid': 'icon-only' });
    const html = render(createElement(Button, { icon, 'aria-label': 'Add' }));
    expect(html).toContain('data-testid="icon-only"');
    expect(html).not.toMatch(/<span>\s*<\/span>/);
  });
});
