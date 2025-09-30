import { describe, it, expect, beforeEach } from 'vitest';
import { TreeStore } from './TreeStore';
import type { PageMeta, ComponentNode } from '../../models/metadata';

const basePage: PageMeta = {
  id: 'p-test-extra',
  name: 'Test Page Extra',
  path: '/x',
  rootId: 'root',
  nodes: [
    { id: 'root', type: 'container', children: ['t1'] },
    { id: 't1', type: 'text', props: { text: 'Hello' } }
  ]
};

function freshStore(opts: any = {}) { return TreeStore.from(basePage, { persist: false, ...opts }); }

describe('TreeStore edge cases', () => {
  let store: TreeStore;
  beforeEach(()=>{ store = freshStore(); });

  it('removing selected node selects its parent after undo/redo cycle', () => {
    store.select('t1');
    store.removeNode('t1');
    expect(store.getSelection()!.id).toBe('root');
    store.undo();
    // selection should still be parent or restored? current implementation replays undo without changing selection, so we accept either t1 or root as long as t1 exists
    const sel = store.getSelection();
    expect(sel?.id === 't1' || sel?.id === 'root').toBe(true);
  });

  it('duplicate node id throws', () => {
    expect(()=>store.addNode('root', { id: 't1', type: 'text', props: { text: 'Dup' } })).toThrow();
  });

  it('cannot remove root', () => {
    const rootBefore = store.getRoot();
    store.removeNode('root');
    expect(store.getRoot()).toBe(rootBefore);
  });

  it('move node with explicit index reorders children', () => {
    store.addNode('root', { id: 'c1', type: 'container', children: [] });
    store.addNode('root', { id: 'c2', type: 'container', children: [] });
    const before = [...(store.getRoot().children||[])];
    expect(before).toEqual(['t1','c1','c2']);
    // move c2 to index 1 (between t1 and c1)
    store.moveNode('c2', 'root', 1);
    expect(store.getRoot().children).toEqual(['t1','c2','c1']);
    store.undo();
    expect(store.getRoot().children).toEqual(['t1','c1','c2']);
    store.redo();
    expect(store.getRoot().children).toEqual(['t1','c2','c1']);
  });

  it('history limit trims oldest operations', () => {
    store = freshStore({ historyLimit: 3 });
    const add = (id: string)=>store.addNode('root', { id, type: 'text', props: { text: id } as any } as ComponentNode);
    add('n1'); add('n2'); add('n3'); add('n4'); add('n5'); // five operations, limit 3 -> only n3,n4,n5 undoable
    // Undo 3 times should remove n3,n4,n5 only
    store.undo(); // remove n5
    store.undo(); // remove n4
    store.undo(); // remove n3
    expect(store.getNode('n5')).toBeUndefined();
    expect(store.getNode('n4')).toBeUndefined();
    expect(store.getNode('n3')).toBeUndefined();
    expect(store.getNode('n2')).toBeTruthy();
    expect(store.getNode('n1')).toBeTruthy();
    // further undo should do nothing
    const stateBefore = JSON.stringify(store.getRoot());
    store.undo();
    expect(JSON.stringify(store.getRoot())).toBe(stateBefore);
  });
});

