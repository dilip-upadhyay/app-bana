/**
 * UserMenu.test.tsx — Sprint 2 Task 2.7.
 *
 * Covers the two purely-functional pieces (initials derivation + closed-panel
 * markup) via react-dom/server. Interactive behaviour (outside-click, Escape,
 * sign-out click) is verified manually and by e2e; unit tests keep the
 * no-jsdom rule this repo has held.
 */
import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { UserMenu, initialsFor } from './UserMenu';

describe('initialsFor', () => {
  it('returns two letters from first + last name', () => {
    expect(initialsFor('Ada Lovelace')).toBe('AL');
  });

  it('handles single-word names by taking the first two characters', () => {
    expect(initialsFor('Cher')).toBe('CH');
  });

  it('splits on dots / underscores / dashes', () => {
    expect(initialsFor('ada.lovelace')).toBe('AL');
    expect(initialsFor('ada_lovelace')).toBe('AL');
    expect(initialsFor('ada-lovelace')).toBe('AL');
  });

  it('falls back to the email local-part when no name is provided', () => {
    expect(initialsFor('', 'grace.hopper@example.com')).toBe('GH');
    expect(initialsFor(null, 'root@example.com')).toBe('RO');
  });

  it('returns a "?" when neither name nor email is usable', () => {
    expect(initialsFor('', '')).toBe('?');
    expect(initialsFor(null, null)).toBe('?');
    expect(initialsFor('   ', '@')).toBe('?');
  });

  it('uppercases mixed-case input', () => {
    expect(initialsFor('grace hopper')).toBe('GH');
  });
});

describe('UserMenu (closed state)', () => {
  it('renders an avatar with initials and does not show the panel', () => {
    const html = renderToStaticMarkup(
      <UserMenu
        tenantId="default"
        tenantDisplayName="Acme Spices"
        user={{ id: '1', name: 'Ada Lovelace', email: 'ada@example.com', tenantId: 'default' }}
      />,
    );
    expect(html).toContain('appbana-user-menu-trigger');
    expect(html).toContain('aria-haspopup="menu"');
    expect(html).toContain('aria-expanded="false"');
    // Panel is hidden until clicked.
    expect(html).not.toContain('appbana-user-menu-panel');
    // Avatar bubble shows initials.
    expect(html).toContain('>AL<');
  });

  it('uses the email local-part when no name is present', () => {
    const html = renderToStaticMarkup(
      <UserMenu
        tenantId="default"
        user={{ id: '1', email: 'grace.hopper@example.com', tenantId: 'default' }}
      />,
    );
    expect(html).toContain('>GH<');
  });

  it('labels the trigger with the primary identity for screen readers', () => {
    const html = renderToStaticMarkup(
      <UserMenu
        tenantId="default"
        user={{ id: '1', name: 'Ada Lovelace', email: 'ada@example.com', tenantId: 'default' }}
      />,
    );
    expect(html).toContain('aria-label="Account menu for Ada Lovelace"');
  });

  it('falls back to "Signed in" when the user object is empty', () => {
    const html = renderToStaticMarkup(
      <UserMenu tenantId="default" user={null} />,
    );
    expect(html).toContain('aria-label="Account menu for Signed in"');
    expect(html).toContain('>?<');
  });
});
