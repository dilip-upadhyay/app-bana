import { describe, it, expect, beforeEach } from 'vitest';
import { TreeStore } from './TreeStore';
import type { PageMeta } from '../../models/metadata';

const page: PageMeta = {
  id: 'p-dupe',
  name: 'Dup Page',
  path: '/d',
  rootId: 'root',
  nodes: [
    { id: 'root', type: 'container', children: ['c1'] },
    { id: 'c1', type: 'container', children: ['t1','t2'] },
    { id: 't1', type: 'text', props: { text: 'A' } },
    { id: 't2', type: 'text', props: { text: 'B' } }
  ]
};

function store() { return TreeStore.from(page, { persist: false }); }

describe('TreeStore duplicate', () => {
  let s: TreeStore;
  beforeEach(()=>{ s = store(); });

  it('duplicates subtree sibling with new ids and selects new root', () => {
    s.select('c1');
    s.duplicate('c1');
    const root = s.getRoot();
    expect(root.children?.length).toBe(2);
    const dupeId = root.children![1];
    expect(dupeId).toMatch(/^c1-copy/);
    expect(s.getSelection()!.id).toBe(dupeId);
    // duplicated subtree should have copies of t1 and t2
    const dupeNode = s.getNode(dupeId)!;
    expect(dupeNode.children?.length).toBe(2);
    for (const childId of dupeNode.children!) {
      expect(childId).toMatch(/^t[12]-copy/);
      expect(s.getNode(childId)).toBeTruthy();
    }
  });

  it('undo/redo duplicate restores state', () => {
    const beforeChildren = [...(s.getRoot().children||[])];
    s.duplicate('c1');
    const afterDuplicate = [...(s.getRoot().children||[])];
    s.undo();
    expect(s.getRoot().children).toEqual(beforeChildren);
    s.redo();
    expect(s.getRoot().children).toEqual(afterDuplicate);
  });

  it('does not duplicate root', () => {
    const before = s.getRoot().children?.length;
    s.duplicate('root');
    expect(s.getRoot().children?.length).toBe(before);
  });

  it('makes unique ids for multiple duplicates', () => {
    s.duplicate('c1');
    s.duplicate('c1'); // duplicate original again
    const root = s.getRoot();
    const dupes = root.children!.filter(id=>id.startsWith('c1-copy'));
    expect(dupes.length).toBe(2);
    expect(new Set(dupes).size).toBe(2);
  });
});

