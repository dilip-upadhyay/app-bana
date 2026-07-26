/**
 * StatusPill.test.tsx — Sprint 2 Task 2.6.
 *
 * We render StatusPill with react-dom/server and assert on the emitted
 * class names + label. No DOM shim required.
 */
import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StatusPill } from './StatusPill';

describe('StatusPill', () => {
  it('renders a pill with the info tone for New', () => {
    const html = renderToStaticMarkup(<StatusPill value="New" />);
    expect(html).toContain('appbana-status-pill');
    expect(html).toContain('status-info');
    expect(html).toContain('New');
  });

  it('renders the warning tone for In Progress (amber)', () => {
    const html = renderToStaticMarkup(<StatusPill value="In Progress" />);
    expect(html).toContain('status-warning');
    expect(html).toContain('In Progress');
  });

  it('renders the success tone for Completed (green)', () => {
    const html = renderToStaticMarkup(<StatusPill value="Completed" />);
    expect(html).toContain('status-success');
  });

  it('renders the danger tone for Blocked and Cancelled (red)', () => {
    expect(renderToStaticMarkup(<StatusPill value="Blocked" />)).toContain('status-danger');
    expect(renderToStaticMarkup(<StatusPill value="Cancelled" />)).toContain('status-danger');
  });

  it('falls back to the neutral tone for unknown values', () => {
    const html = renderToStaticMarkup(<StatusPill value="Whatever" />);
    expect(html).toContain('status-neutral');
  });

  it('honours an explicit tone override', () => {
    const html = renderToStaticMarkup(<StatusPill value="Completed" tone="danger" />);
    expect(html).toContain('status-danger');
    expect(html).not.toContain('status-success');
  });

  it('renders an em-dash placeholder for empty values by default', () => {
    const html = renderToStaticMarkup(<StatusPill value="" />);
    expect(html).toContain('—');
    expect(html).not.toContain('appbana-status-pill');
  });

  it('renders null for empty values when emptyMode="hide"', () => {
    const html = renderToStaticMarkup(<StatusPill value={null} emptyMode="hide" />);
    expect(html).toBe('');
  });

  it('trims whitespace on the label', () => {
    const html = renderToStaticMarkup(<StatusPill value="  Approved  " />);
    expect(html).toContain('>Approved<');
    expect(html).toContain('status-success');
  });

  it('appends caller className to the pill root', () => {
    const html = renderToStaticMarkup(<StatusPill value="New" className="ml-2" />);
    expect(html).toContain('appbana-status-pill status-info ml-2');
  });
});
