import { describe, it, expect, beforeEach } from 'vitest';
import { TreeStore } from './TreeStore';
import type { PageMeta } from '../../models/metadata';

const basePage: PageMeta = {
  id: 'p-test',
  name: 'Test Page',
  path: '/x',
  rootId: 'root',
  nodes: [
    { id: 'root', type: 'container', children: ['t1'] },
    { id: 't1', type: 'text', props: { text: 'Hello' } }
  ]
};

function freshStore() { return TreeStore.from(basePage, { persist: false }); }

describe('TreeStore', () => {
  let store: TreeStore;
  beforeEach(()=>{ store = freshStore(); });

  it('selects node', () => {
    store.select('t1');
    expect(store.getSelection()!.id).toBe('t1');
  });

  it('adds node and undo/redo works', () => {
    const rootBefore = store.getRoot().children?.length || 0;
    store.addNode('root', { id: 'n1', type: 'text', props: { text: 'New' } });
    expect(store.getRoot().children).toContain('n1');
    store.undo();
    expect(store.getRoot().children?.length).toBe(rootBefore);
    store.redo();
    expect(store.getRoot().children).toContain('n1');
  });

  it('updates props and undo restores previous', () => {
    store.updateProps('t1', { text: 'Changed' });
    expect(store.getNode('t1')!.props!.text).toBe('Changed');
    store.undo();
    expect(store.getNode('t1')!.props!.text).toBe('Hello');
  });

  it('removes subtree and undo restores', () => {
    store.addNode('root', { id: 'c1', type: 'container', children: [] });
    store.addNode('c1', { id: 'leaf', type: 'text', props: { text: 'Leaf' } });
    expect(store.getNode('leaf')).toBeTruthy();
    store.removeNode('c1');
    expect(store.getNode('c1')).toBeUndefined();
    expect(store.getNode('leaf')).toBeUndefined();
    store.undo();
    expect(store.getNode('c1')).toBeTruthy();
    expect(store.getNode('leaf')).toBeTruthy();
  });

  it('move node prevents cycles', () => {
    store.addNode('root', { id: 'c1', type: 'container', children: [] });
    store.addNode('c1', { id: 'c2', type: 'container', children: [] });
    // attempt to move root under c2 should be ignored (cycle)
    const before = JSON.stringify(store.getRoot());
    store.moveNode('root', 'c2');
    expect(JSON.stringify(store.getRoot())).toBe(before);
  });
});

