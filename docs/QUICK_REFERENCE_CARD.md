# 🚀 Quick Reference Card - Next Copilot Session

**Date:** October 30, 2025  
**Version:** 1.0

---

## ⚡ TL;DR - What You Need to Know

### Project Status
**✅ PRODUCTION READY** - Studio Builder is fully functional with drag-drop, grid system, resizing, and page templates.

### Start Here
1. Read **`docs/COPILOT_SESSION_SUMMARY.md`** - Complete overview
2. Check **`docs/DOCUMENTATION_INDEX.md`** - All documentation links
3. Review **`docs/FIXES_INDEX.md`** - What was fixed

---

## 🎯 Current State

### What Works ✅
- Drag-drop components from library to canvas
- Grid system with 2×3 default (6 visible cells)
- Component resizing (width/height controls)
- Delete components (keyboard + UI button)
- Page templates (Nav/Sidenav/Main/Footer)
- Properties inspector (right panel)
- Auto-sync between store and UI

### Known Issues ❌
- Grid dimensions can't be changed after creation
- Undo/Redo buttons not connected
- No visual resize handles
- Cell IDs may conflict with multiple grids
- No padding/margin controls

---

## 🗂️ File Map

### Core Files
```
src/builder/
├── store/
│   ├── TreeStore.ts         ← Component tree state (THE SOURCE OF TRUTH)
│   └── AppStore.ts          ← App/page management
├── components/
│   ├── LivePreview.ts       ← Canvas (drag-drop target)
│   ├── ComponentLibrary.ts  ← Component palette (drag source)
│   ├── BuilderInspector.ts  ← Properties panel
│   ├── PageManager.ts       ← Page tabs + template wizard
│   └── BuilderShell.ts      ← Main container
```

### Documentation
```
docs/
├── COPILOT_SESSION_SUMMARY.md    ⭐ START HERE
├── DOCUMENTATION_INDEX.md        → All docs indexed
├── FIXES_INDEX.md               → Bug fixes log
└── [feature-specific docs]
```

---

## 💻 Code Snippets

### Access Current Store
```typescript
import { currentStore } from '../store/TreeStore';

if (!currentStore) return; // Always check!

// Subscribe to changes
currentStore.onChange(() => {
  this.page = currentStore.getPage();
  this.requestUpdate();
});

// Add component
currentStore.addNode(parentId, newNode);

// Add component with children (e.g., grid)
currentStore.addNodeTree(parentId, nodeWithChildren);

// Update properties
currentStore.updateProps(nodeId, { style: '...' });

// Remove component
currentStore.removeNode(nodeId);
```

### Drag & Drop Pattern
```typescript
// Source (ComponentLibrary)
handleDragStart(e: DragEvent, template) {
  const data = { action: 'add-component', template };
  e.dataTransfer.setData('application/json', JSON.stringify(data));
  window.__dragData = data; // Shadow DOM fallback
}

// Target (LivePreview)
handleDrop(e: DragEvent, targetNodeId: string) {
  let data = null;
  try {
    data = JSON.parse(e.dataTransfer.getData('application/json'));
  } catch {
    data = window.__dragData; // Fallback
  }
  
  if (data?.action === 'add-component') {
    currentStore.addNodeTree(targetNodeId, data.template);
  }
}
```

### Generate Unique IDs
```typescript
// For components
const id = `${type}-${Date.now()}`;

// For grid cells
const cellId = `cell-${pageId}-${Date.now()}-${index}`;
```

---

## 🐛 Common Pitfalls

### 1. Store Not Initialized
```typescript
// ❌ Bad
currentStore.addNode(...);

// ✅ Good
if (currentStore) {
  currentStore.addNode(...);
}
```

### 2. Subscription Memory Leaks
```typescript
// ❌ Bad
connectedCallback() {
  currentStore.onChange(() => { ... });
}

// ✅ Good
connectedCallback() {
  this.unsubscribe = currentStore.onChange(() => { ... });
}
disconnectedCallback() {
  if (this.unsubscribe) this.unsubscribe();
}
```

### 3. Modal State Not Cleared
```typescript
// ❌ Bad
this.showModal = false; // Only one modal

// ✅ Good
this.showCreateModal = false;
this.showTemplateModal = false; // Close all modals
```

### 4. Children Not Added to Store
```typescript
// ❌ Bad (children as objects, not in store)
currentStore.addNode(parentId, {
  id: 'grid',
  children: [{ id: 'cell-0', ... }] // Not in store!
});

// ✅ Good (use addNodeTree)
currentStore.addNodeTree(parentId, {
  id: 'grid',
  children: [{ id: 'cell-0', ... }] // Will be added recursively
});
```

---

## 🎯 Priority Tasks

### Immediate (High Impact)
1. **Connect Undo/Redo** - Wire up existing history to UI buttons
2. **Fix Grid Cell IDs** - Make unique with timestamp
3. **Add Padding/Margin** - Common styling controls

### Next (Quality of Life)
4. **Grid Reconfiguration** - Edit rows/cols after creation
5. **Visual Resize Handles** - Drag corners to resize
6. **Component Copy/Paste** - Duplicate components

### Future (Advanced)
7. **Responsive Preview** - Mobile/tablet views
8. **Export HTML/CSS** - Generate code
9. **Component Library** - Save/reuse groups

---

## 📝 Quick Checks

### Before Starting Work
- [ ] Read COPILOT_SESSION_SUMMARY.md
- [ ] Check current state section
- [ ] Review known issues
- [ ] Understand TreeStore pattern

### Before Committing
- [ ] No TypeScript errors
- [ ] Build succeeds (`npm run build`)
- [ ] Can create page with template
- [ ] Can drag grid onto canvas
- [ ] Can drag components into cells
- [ ] Can resize components
- [ ] Can delete components
- [ ] Modals close properly

### After Major Change
- [ ] Update COPILOT_SESSION_SUMMARY.md
- [ ] Add to DOCUMENTATION_INDEX.md
- [ ] Create feature-specific doc if needed
- [ ] Update this quick reference if patterns change

---

## 🔑 Key Concepts

### TreeStore
- Single source of truth for page structure
- Manages component tree as Map<id, node>
- Provides undo/redo history
- Notifies listeners on change
- Persists to localStorage

### Component Node
```typescript
interface ComponentNode {
  id: string;              // Unique identifier
  type: string;            // 'container', 'button', 'input', etc.
  props?: {                // Component properties
    style?: string;        // Inline CSS
    className?: string;    // CSS classes
    [key: string]: any;   // Other props
  };
  children?: string[];     // Child node IDs
}
```

### Page Meta
```typescript
interface PageMeta {
  metaVersion: number;
  id: string;
  name: string;
  path: string;
  rootId: string;          // ID of root node
  nodes: ComponentNode[];  // Flat array of all nodes
}
```

---

## 💡 Tips

### Debugging
- Check browser console for `[Component]` logs
- Inspect `localStorage` for draft keys
- Use `currentStore.getPage()` to see tree
- Check `window.__dragData` for drag payload

### Performance
- Avoid re-subscribing in loops
- Use `requestUpdate()` for Lit components
- Batch updates when possible
- Clean up subscriptions

### Best Practices
- Always validate `currentStore` exists
- Generate unique IDs with timestamps
- Log important actions
- Handle errors gracefully
- Clear modal state completely

---

## 📞 Getting Help

### Documentation
- **Overview:** COPILOT_SESSION_SUMMARY.md
- **Specific Feature:** Check DOCUMENTATION_INDEX.md
- **Bug Fixes:** FIXES_INDEX.md

### Code Examples
- Look in existing components for patterns
- Check LivePreview.ts for drag-drop
- Check BuilderInspector.ts for properties
- Check PageManager.ts for modals

---

## 🎉 You're Ready!

**Remember:** The code is stable, documented, and working. Focus on:
1. Understanding TreeStore pattern
2. Following existing patterns
3. Testing thoroughly
4. Updating documentation

**Good luck with your next session!** 🚀

---

**Last Updated:** October 30, 2025  
**For full details:** See `docs/COPILOT_SESSION_SUMMARY.md`

