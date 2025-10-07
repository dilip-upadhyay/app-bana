import { describe, it, expect, beforeEach } from 'vitest';
import { initTokens, getTokens, updateToken, exportTokenSnapshot, importTokenSnapshot, getCategories, getRevisions } from './TokenStore';

describe('TokenStore snapshot & categories', () => {
  beforeEach(()=>{ localStorage.clear(); initTokens(); });

  it('exports and imports snapshot replacing values', () => {
    updateToken('color-brand', '#ffffff');
    const snap = exportTokenSnapshot('test');
    updateToken('color-brand', '#000000');
    expect(getTokens()['color-brand']).toBe('#000000');
    importTokenSnapshot(snap); // replace
    expect(getTokens()['color-brand']).toBe('#ffffff');
  });

  it('merge import updates only provided keys', () => {
    const snap = { meta:{}, tokens: { 'color-brand':'#123123', 'new-token-x':'42' } };
    importTokenSnapshot(snap, { merge: true });
    expect(getTokens()['color-brand']).toBe('#123123');
    expect(getTokens()['new-token-x']).toBe('42');
  });

  it('categories include Colors group', () => {
    const cats = getCategories();
    expect(Object.keys(cats)).toContain('Colors');
    expect(cats['Colors']).toContain('color-brand');
  });

  it('revisions capture updates and imports', () => {
    const before = getRevisions().length;
    updateToken('color-border', '#aaa');
    const snap = exportTokenSnapshot('rev');
    importTokenSnapshot(snap); // import triggers revision
    expect(getRevisions().length).toBeGreaterThan(before);
  });
});

