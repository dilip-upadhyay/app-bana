/**
 * ChildTable.test.tsx — H2 hardening coverage.
 *
 * Two behaviours matter:
 *   1. `renderChildTablesFromPage` pulls only child_table nodes out of a
 *      page — nothing else.
 *   2. When a child_table renders inside a RecordContextProvider, the
 *      Renderer wrapper injects the ambient `recordId` as the parentId.
 *
 * We keep the runtime's no-jsdom rule and use react-dom/server for #1,
 * plus a pure consumer for #2 that reads the same context.
 */
import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import type { PageMeta } from '@appbana/shared';
import { renderChildTablesFromPage } from './Renderer';
import { RecordContextProvider, useRecordScope } from './RecordContext';

function pageWith(nodes: PageMeta['nodes']): PageMeta {
  return { id: 'p', name: 'Customer Detail', rootId: 'root', nodes } as PageMeta;
}

describe('renderChildTablesFromPage', () => {
  it('returns [] for a page with no child_table nodes', () => {
    const p = pageWith([
      { id: 'root', type: 'div', props: {} },
      { id: 'h', type: 'h1', props: { text: 'Hello' } },
    ]);
    expect(renderChildTablesFromPage(p)).toEqual([]);
  });

  it('returns one element per child_table node, ignoring other types', () => {
    const p = pageWith([
      { id: 'root', type: 'div', props: {} },
      { id: 'ct1', type: 'child_table', props: { entityName: 'Order', fkField: 'customer_id' } },
      { id: 'x',   type: 'div', props: {} },
      { id: 'ct2', type: 'child_table', props: { entityName: 'Invoice', fkField: 'customer_id' } },
    ]);
    const out = renderChildTablesFromPage(p);
    expect(out).toHaveLength(2);
  });
});

/** Consumer that just prints whatever recordId is in scope. */
function ScopeProbe() {
  const scope = useRecordScope();
  return <span data-testid="probe">{scope?.recordId ?? 'none'}</span>;
}

describe('RecordContext', () => {
  it('exposes null when no provider wraps the consumer', () => {
    const markup = renderToStaticMarkup(<ScopeProbe />);
    expect(markup).toContain('>none<');
  });

  it('exposes the recordId when wrapped in a provider', () => {
    const markup = renderToStaticMarkup(
      <RecordContextProvider value={{ recordId: '42', entityKey: 'default_app_Customer' }}>
        <ScopeProbe />
      </RecordContextProvider>
    );
    expect(markup).toContain('>42<');
  });
});
