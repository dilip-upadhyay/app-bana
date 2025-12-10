import type { PageMeta, ComponentNode } from '../../models/metadata';

export interface TreeStoreOptions {
  persist?: boolean;
  keyPrefix?: string;
  historyLimit?: number;
  skipDraft?: boolean; // Skip loading draft from localStorage (useful for new pages)
}

interface Operation { desc: string; undo: () => void; redo: () => void; }

export class TreeStore {
  private page: PageMeta;
  private nodes = new Map<string, ComponentNode>();
  private selection: string | null = null;
  private history: Operation[] = [];
  private future: Operation[] = [];
  private historyLimit: number;
  private persist: boolean;
  private key: string;
  private listeners = new Set<() => void>();
  private clipboard: ComponentNode | null = null;

  static from(page: PageMeta, opts: TreeStoreOptions = {}) { return new TreeStore(page, opts); }

  private constructor(page: PageMeta, opts: TreeStoreOptions) {
    this.page = structuredClone(page);
    for (const n of this.page.nodes) this.nodes.set(n.id, structuredClone(n));
    this.persist = opts.persist ?? true;
    this.key = (opts.keyPrefix ?? 'studio.draft.') + this.page.id;
    this.historyLimit = opts.historyLimit ?? 100;

    // Only load draft if not skipped and persist is enabled
    if (this.persist && !opts.skipDraft) {
      this.loadDraft();
    } else if (opts.skipDraft) {
    }
  }

  onChange(fn: () => void) { this.listeners.add(fn); return () => this.listeners.delete(fn); }
  private notify() {
    for (const fn of this.listeners) fn();
  }

  getPage(): PageMeta { return { ...this.page, nodes: Array.from(this.nodes.values()) }; }
  getRoot(): ComponentNode { return this.require(this.page.rootId); }
  getSelection(): ComponentNode | null {
    const sel = this.selection ? this.nodes.get(this.selection)! : null;
    return sel;
  }
  getNode(id: string): ComponentNode | undefined { return this.nodes.get(id); }
  listChildren(id: string): ComponentNode[] { const n = this.require(id); return (n.children || []).map(cid => this.require(cid)); }

  select(id: string | null) {
    if (id && !this.nodes.has(id)) return;
    this.selection = id;
    this.save();
    this.notify();
  }

  addNode(parentId: string, node: ComponentNode, index?: number) {

    if (this.nodes.has(node.id)) {
      console.error('[TreeStore] Duplicate node id:', node.id);
      throw new Error('duplicate node id: ' + node.id);
    }

    const parent = this.require(parentId);

    const op: Operation = {
      desc: `add:${node.id}`,
      undo: () => {
        parent.children = (parent.children || []).filter(c => c !== node.id);
        this.nodes.delete(node.id);
      },
      redo: () => {
        this.nodes.set(node.id, node);
        parent.children = parent.children || [];
        if (index === undefined || index < 0 || index > parent.children.length) {
          parent.children.push(node.id);
        } else {
          parent.children.splice(index, 0, node.id);
        }
        console.log('[TreeStore] Node added, parent now has children:', parent.children);
      }
    };
    op.redo();
    this.pushHistory(op);
    this.select(node.id);
    console.log('[TreeStore] addNode complete, total nodes:', this.nodes.size);
  }

  // Add a node tree (node with pre-existing children nodes)
  addNodeTree(parentId: string, nodeTree: any, index?: number): string {
    console.log('[TreeStore] addNodeTree called:', { parentId, nodeId: nodeTree.id, nodeType: nodeTree.type, hasChildren: !!nodeTree.children });

    // First, recursively process all children to get their IDs
    const childIds: string[] = [];
    if (nodeTree.children && Array.isArray(nodeTree.children) && nodeTree.children.length > 0) {
      // Check if children are objects (nodes to add) or just IDs
      const firstChild = nodeTree.children[0];
      if (typeof firstChild === 'object' && firstChild !== null && firstChild.id) {
        console.log('[TreeStore] Processing child nodes recursively');
        // Children are node objects, add them recursively
        for (const childTree of nodeTree.children) {
          // Add each child tree, with nodeTree.id as parent (but we'll add the node itself later)
          // For now, just collect and add them to the map
          const childId = this.addChildNodeTree(childTree);
          childIds.push(childId);
        }
      } else {
        // Children are already IDs
        childIds.push(...nodeTree.children);
      }
    }

    // Now create the node with child IDs
    const node: ComponentNode = {
      id: nodeTree.id,
      type: nodeTree.type || 'container',
      props: nodeTree.props || {},
      children: childIds
    };

    // Add this node using the regular addNode method
    this.addNode(parentId, node, index);

    return node.id;
  }

  // Helper to recursively add child nodes without parent linkage first
  private addChildNodeTree(nodeTree: any): string {
    console.log('[TreeStore] addChildNodeTree called:', nodeTree.id);

    // Recursively process grandchildren
    const grandchildIds: string[] = [];
    if (nodeTree.children && Array.isArray(nodeTree.children) && nodeTree.children.length > 0) {
      const firstGrandchild = nodeTree.children[0];
      if (typeof firstGrandchild === 'object' && firstGrandchild !== null && firstGrandchild.id) {
        for (const grandchildTree of nodeTree.children) {
          const grandchildId = this.addChildNodeTree(grandchildTree);
          grandchildIds.push(grandchildId);
        }
      } else {
        grandchildIds.push(...nodeTree.children);
      }
    }

    // Create the node
    const node: ComponentNode = {
      id: nodeTree.id,
      type: nodeTree.type || 'container',
      props: nodeTree.props || {},
      children: grandchildIds
    };

    // Add directly to nodes map (no parent linkage yet)
    this.nodes.set(node.id, node);
    console.log('[TreeStore] Child node added to map:', node.id);

    return node.id;
  }

  updateProps(id: string, patch: Record<string, any>) {
    const node = this.require(id);
    const prev = structuredClone(node.props || {});
    const next = { ...(node.props || {}), ...patch };
    const op: Operation = {
      desc: `update:${id}`,
      undo: () => { node.props = structuredClone(prev); },
      redo: () => { node.props = structuredClone(next); }
    };
    op.redo();
    this.pushHistory(op);
  }

  removeNode(id: string) {
    if (id === this.page.rootId) return; // cannot remove root
    const node = this.require(id);
    // collect subtree
    const subtreeIds: string[] = [];
    const collect = (n: ComponentNode) => { subtreeIds.push(n.id); (n.children || []).forEach(cid => collect(this.require(cid))); };
    collect(node);
    // parent ref removal
    const parent = this.findParent(id);
    if (!parent) return;
    const parentChildrenPrev = [...(parent.children || [])];
    const removedNodes = subtreeIds.map(i => [i, this.require(i)] as const);
    const op: Operation = {
      desc: `remove:${id}`,
      undo: () => { parent.children = [...parentChildrenPrev]; for (const [nid, nv] of removedNodes) this.nodes.set(nid, structuredClone(nv)); },
      redo: () => { parent.children = (parent.children || []).filter(c => c !== id); for (const nid of subtreeIds) this.nodes.delete(nid); if (this.selection && subtreeIds.includes(this.selection)) this.selection = parent.id; }
    };
    op.redo();
    this.pushHistory(op);
  }

  moveNode(id: string, newParentId: string, newIndex?: number) {
    const oldParent = this.findParent(id);
    if (!oldParent) return;
    const newParent = this.require(newParentId);
    if (this.isAncestor(id, newParentId)) return; // prevent cycles
    const oldParentChildrenPrev = [...(oldParent.children || [])];
    const newParentChildrenPrev = [...(newParent.children || [])];
    const op: Operation = {
      desc: `move:${id}`,
      undo: () => {
        oldParent.children = [...oldParentChildrenPrev];
        newParent.children = [...newParentChildrenPrev];
      },
      redo: () => {
        oldParent.children = (oldParent.children || []).filter(c => c !== id);
        newParent.children = newParent.children || [];
        if (newIndex === undefined || newIndex < 0 || newIndex > newParent.children.length) newParent.children.push(id); else newParent.children.splice(newIndex, 0, id);
      }
    };
    op.redo();
    this.pushHistory(op);
  }

  copy(id: string) {
    const node = this.nodes.get(id);
    if (!node || node.id === this.page.rootId) return;

    // Deep clone the node and its children for the clipboard
    const cloneNode = (n: ComponentNode): ComponentNode => {
      const cloned = structuredClone(n);
      cloned.children = (n.children || []).map(cid => {
        const child = this.nodes.get(cid);
        return child ? cloneNode(child) : null;
      }).filter((c): c is ComponentNode => c !== null) as any; // Store full objects in clipboard for simplicity
      return cloned;
    };

    this.clipboard = cloneNode(node);
    console.log('[TreeStore] Copied to clipboard:', this.clipboard);
  }

  cut(id: string) {
    this.copy(id);
    this.removeNode(id);
  }

  paste(targetId: string) {
    if (!this.clipboard) return;
    const target = this.nodes.get(targetId);
    if (!target) return;

    // Determine parent and index
    let parentId = targetId;
    let index: number | undefined = undefined;

    // If target is not a container, paste after it
    const isContainer = target.type === 'container' || target.type === 'section' || target.type === 'div';
    if (!isContainer && targetId !== this.page.rootId) {
      const parent = this.findParent(targetId);
      if (parent) {
        parentId = parent.id;
        index = (parent.children || []).indexOf(targetId) + 1;
      }
    }

    // Re-generate IDs for the pasted tree
    const regenerateIds = (n: ComponentNode): ComponentNode => {
      const newNode = structuredClone(n);
      newNode.id = n.type + '-' + Math.random().toString(36).slice(2, 7);

      // If children are stored as objects in clipboard (from copy), process them
      if (Array.isArray(newNode.children)) {
        const childrenObjects = newNode.children as any as ComponentNode[];
        newNode.children = childrenObjects.map(c => regenerateIds(c)) as any;
      }
      return newNode;
    };

    const pastedTree = regenerateIds(this.clipboard);
    this.addNodeTree(parentId, pastedTree, index);
  }

  duplicate(id: string) {
    if (!this.nodes.has(id)) return;
    if (id === this.page.rootId) return; // do not duplicate root for now
    const original = this.require(id);
    const parent = this.findParent(id);
    if (!parent) return;
    // Collect original subtree
    const originals: ComponentNode[] = [];
    const collect = (n: ComponentNode) => { originals.push(n); (n.children || []).forEach(cid => collect(this.require(cid))); };
    collect(original);
    // Build id map + cloned nodes
    const idMap = new Map<string, string>();
    const genId = (base: string) => {
      let candidate: string; let attempt = 0;
      do { candidate = base + '-copy' + (attempt ? '-' + attempt : ''); attempt++; } while (this.nodes.has(candidate));
      return candidate;
    };
    for (const n of originals) idMap.set(n.id, genId(n.id));
    const clonedNodes: ComponentNode[] = originals.map(n => ({
      ...structuredClone(n),
      id: idMap.get(n.id)!,
      children: n.children ? n.children.map(cid => idMap.get(cid)!) : n.children
    }));
    const newRootId = idMap.get(id)!;
    const insertIndex = parent.children ? parent.children.indexOf(id) + 1 : 0;
    const op: Operation = {
      desc: `duplicate:${id}`,
      undo: () => {
        parent.children = parent.children?.filter(cid => !clonedNodes.some(cn => cn.id === cid));
        for (const cn of clonedNodes) this.nodes.delete(cn.id);
      },
      redo: () => {
        for (const cn of clonedNodes) this.nodes.set(cn.id, structuredClone(cn));
        parent.children = parent.children || [];
        // insert new root id + ensure children order for subtree root only
        if (!parent.children.includes(newRootId)) {
          if (insertIndex < 0 || insertIndex > parent.children.length) parent.children.push(newRootId);
          else parent.children.splice(insertIndex, 0, newRootId);
        }
      }
    };
    op.redo();
    this.pushHistory(op);
    this.select(newRootId);
  }

  undo() { const op = this.history.pop(); if (!op) return; op.undo(); this.future.push(op); this.save(false); this.notify(); }
  redo() { const op = this.future.pop(); if (!op) return; op.redo(); this.history.push(op); this.save(false); this.notify(); }

  serialize(): PageMeta { return this.getPage(); }

  private pushHistory(op: Operation) {
    this.history.push(op); if (this.history.length > this.historyLimit) this.history.shift();
    this.future.length = 0; // clear redo stack on new action
    this.save();
    this.notify();
  }

  private require(id: string): ComponentNode { const n = this.nodes.get(id); if (!n) throw new Error('node not found: ' + id); return n; }
  public findParent(id: string): ComponentNode | null {
    for (const n of this.nodes.values()) if (n.children?.includes(id)) return n; return null;
  }
  private isAncestor(ancestorId: string, maybeDesc: string): boolean {
    // Correct semantics: return true if ancestorId is (strict or same) ancestor of maybeDesc.
    if (ancestorId === maybeDesc) return true;
    const ancestor = this.nodes.get(ancestorId);
    if (!ancestor) return false;
    for (const cid of ancestor.children || []) {
      if (cid === maybeDesc) return true;
      if (this.isAncestor(cid, maybeDesc)) return true;
    }
    return false;
  }

  private save(persist: boolean = true) {
    if (this.persist && persist) {
      console.log('[TreeStore] Saving to localStorage, key:', this.key, 'nodes:', this.nodes.size);
      localStorage.setItem(this.key, JSON.stringify(this.serialize()));
    }
  }

  private loadDraft() {
    try {
      const raw = localStorage.getItem(this.key);
      if (raw) {
        console.log('[TreeStore] Found draft in localStorage for key:', this.key);
        const data = JSON.parse(raw) as PageMeta;
        console.log('[TreeStore] Draft data has', data.nodes.length, 'nodes, rootId:', data.rootId);
        this.nodes.clear();
        for (const n of data.nodes) this.nodes.set(n.id, n);
        this.page.rootId = data.rootId;
        console.log('[TreeStore] Draft loaded successfully');
      } else {
        console.log('[TreeStore] No draft found in localStorage for key:', this.key);
      }
    } catch (err) {
      console.error('[TreeStore] Error loading draft:', err);
    }
  }
}

// Singleton draft store helper for current demo page
export let currentStore: TreeStore | null = null;

export function initStore(page: PageMeta, opts: TreeStoreOptions = {}) {
  console.log('[initStore] Initializing store for page:', page.id, 'with options:', opts);
  currentStore = TreeStore.from(page, opts);
  return currentStore;
}

export function initNewPageStore(page: PageMeta) {
  console.log('[initNewPageStore] Initializing store for NEW page:', page.id, '- skipping draft load');
  return initStore(page, { skipDraft: true });
}
