/**
 * EntityQuery.test.tsx — Task C3.9.
 *
 * Pins the wire format of list filters.
 *
 * The bug these tests exist for was invisible: bare `?field=value` params were
 * dropped by the backend's param allowlist, the response came back 200, and the
 * list simply showed more rows than the user asked for. Nothing threw, nothing
 * logged, and no test could see it because no test looked at the query string.
 */
import { describe, it, expect } from 'vitest';
import { toEntityQueryParams, exact } from './entity-query';
import { buildApprovalSystemViews, APPROVAL_VIEW_PREFIX } from './approval-views';

describe('toEntityQueryParams', () => {
  it('folds field filters into a single filter= clause, not bare params', () => {
    const { params } = toEntityQueryParams({ status: 'open', owner: 'alice' });
    expect(params.status).toBeUndefined();
    expect(params.owner).toBeUndefined();
    expect(params.filter).toBe('status:open,owner:alice');
  });

  it('leaves the dedicated approval param bare — the backend reads it by name', () => {
    const { params } = toEntityQueryParams({ _approvalStatus: 'DRAFT' });
    expect(params._approvalStatus).toBe('DRAFT');
    expect(params.filter).toBeUndefined();
  });

  it('mixes a dedicated param with a field filter without conflating them', () => {
    const { params } = toEntityQueryParams({ _approvalStatus: 'REJECTED', submitted_by: 'u1' });
    expect(params._approvalStatus).toBe('REJECTED');
    expect(params.filter).toBe('submitted_by:u1');
  });

  it('drops empty values so an unset filter never narrows to the empty string', () => {
    const { params } = toEntityQueryParams({ status: '', owner: null, age: 0 });
    expect(params.filter).toBe('age:0');
  });

  it('preserves an explicit filter clause and appends to it', () => {
    const { params } = toEntityQueryParams({ filter: 'a:1', b: '2' });
    expect(params.filter).toBe('a:1,b:2');
  });

  it('refuses a value containing a comma rather than truncating it silently', () => {
    // parseFilters splits on ',' before ':', so "Smith, John" would apply as
    // "Smith" and quietly match the wrong rows.
    const { params, rejected } = toEntityQueryParams({ name: 'Smith, John', status: 'open' });
    expect(rejected).toEqual(['name']);
    expect(params.filter).toBe('status:open');
  });

  it('marks an exact filter with a leading = so it does not substring-match', () => {
    const { params } = toEntityQueryParams({ submitted_by: exact('bob') });
    // Without this, scoping to "bob" would also return "bobby"'s records.
    expect(params.filter).toBe('submitted_by:=bob');
  });

  it('passes reserved paging/search params straight through', () => {
    const { params } = toEntityQueryParams({ q: 'invoice', sort: 'name:asc', limit: 25 });
    expect(params).toEqual({ q: 'invoice', sort: 'name:asc', limit: 25 });
  });
});

describe('approval system views on the wire', () => {
  it('scopes "Needs rework" to the caller through filter=, which the backend reads', () => {
    const rework = buildApprovalSystemViews('alice')
      .find((v) => v.viewId === `${APPROVAL_VIEW_PREFIX}rework`);
    expect(rework).toBeDefined();

    const { params } = toEntityQueryParams(rework!.view.filters ?? {});
    expect(params._approvalStatus).toBe('REJECTED');
    // The regression: this used to leave as a bare `submitted_by` param, which
    // the handler never reads, so every maker saw every other maker's
    // rejected records under a heading that said they were their own.
    expect(params.filter).toBe('submitted_by:=alice');
  });

  it('offers an "All" view so a zero-row filter is escapable', () => {
    const views = buildApprovalSystemViews('alice');
    expect(views[0].viewId).toBe(`${APPROVAL_VIEW_PREFIX}all`);
    expect(views[0].name).toBe('All');
    expect(toEntityQueryParams(views[0].view.filters ?? {}).params).toEqual({});
  });

  it('omits "Needs rework" when there is no user, rather than showing everyone\'s', () => {
    const views = buildApprovalSystemViews(null);
    expect(views.some((v) => v.viewId.endsWith('rework'))).toBe(false);
  });
});
