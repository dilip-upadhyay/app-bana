# ✅ FIX: Infinite Retry Loop in LivePreview

## Problem
When an app has no pages, the `LivePreview` component would enter an **infinite retry loop**, continuously flooding the console with error messages:

```
[LivePreview] currentStore is null! Will retry...
[LivePreview] currentStore is null! Will retry...
[LivePreview] currentStore is null! Will retry...
... (repeated infinitely, hundreds of times)
```

This happened because:
1. App has zero pages
2. `PageManager.loadPages()` doesn't initialize a store when no pages exist
3. `currentStore` remains `null`
4. `LivePreview.updateFromStore()` retries forever with no maximum limit

## Impact
- **Console spam**: Hundreds/thousands of error messages
- **Performance**: Continuous setTimeout calls consuming resources
- **Developer experience**: Hard to debug other issues
- **User confusion**: Looks like something is broken

## Root Cause

### In PageManager.ts:
```typescript
if (this.currentPageId) {
  this.switchToPage(this.currentPageId);
}
// ❌ No else block! Store never initialized when no pages exist
```

### In LivePreview.ts:
```typescript
private updateFromStore() {
  if (currentStore) {
    // ... setup store ...
  } else {
    console.error('[LivePreview] currentStore is null! Will retry...');
    setTimeout(() => this.updateFromStore(), 100); // ❌ Infinite recursion!
  }
}
```

## Solution

### Fix 1: Initialize Store When No Pages Exist (PageManager.ts)

```typescript
if (this.currentPageId) {
  this.switchToPage(this.currentPageId);
} else {
  // ✅ NEW: Initialize empty store when no pages
  this.clearStore();
  console.log('[PageManager] No pages available - cleared store');
}
```

This ensures `currentStore` is always initialized, even with an empty page.

### Fix 2: Add Maximum Retry Limit (LivePreview.ts)

```typescript
private retryCount = 0;
private maxRetries = 10; // Maximum 10 retries (1 second total)

private updateFromStore() {
  if (currentStore) {
    this.retryCount = 0; // ✅ Reset on success
    // ... setup store ...
  } else {
    if (this.retryCount < this.maxRetries) {
      this.retryCount++;
      console.warn(`[LivePreview] currentStore is null! Retry ${this.retryCount}/${this.maxRetries}...`);
      setTimeout(() => this.updateFromStore(), 100);
    } else {
      // ✅ Give up after max retries
      console.error('[LivePreview] currentStore is still null after max retries. Giving up.');
      console.error('[LivePreview] This usually means no pages exist. Create a page to start building.');
    }
  }
}
```

## Before vs After

### Before (Console Output):
```
[LivePreview] currentStore is null! Will retry...
[LivePreview] currentStore is null! Will retry...
[LivePreview] currentStore is null! Will retry...
... (repeated 500+ times)
```

### After (Console Output):
```
[PageManager] Loading pages for app: Dashboard App Pages: []
[PageManager] Loaded 0 pages: []
[PageManager] Switching to new page: null
[PageManager] No pages available - cleared store
[PageManager] Clearing current store
[LivePreview] connectedCallback - currentStore: TreeStore {...}
[LivePreview] Initial page loaded: {id: 'empty', name: 'Empty', ...}
```

## Benefits

1. ✅ **No infinite loops** - Maximum 10 retries (1 second)
2. ✅ **Clean console** - Clear, helpful error messages
3. ✅ **Better performance** - No endless setTimeout calls
4. ✅ **Store always exists** - Even when no pages are present
5. ✅ **Graceful degradation** - Clear error message guides user
6. ✅ **Developer-friendly** - Easy to debug actual issues

## Testing

### Steps to Reproduce Original Issue:
1. Create a new app
2. Don't add any pages
3. Open the app in studio
4. Open browser console
5. **Before fix**: See hundreds of retry messages
6. **After fix**: See max 10 retries, then clear error message

### Expected Behavior After Fix:
```
[LivePreview] currentStore is null! Retry 1/10...
[LivePreview] currentStore is null! Retry 2/10...
... (up to 10 times max)
[LivePreview] currentStore is still null after max retries. Giving up.
[LivePreview] This usually means no pages exist. Create a page to start building.
```

OR (if store initializes properly):
```
[LivePreview] connectedCallback - currentStore: TreeStore {...}
[LivePreview] Initial page loaded: {id: 'empty', ...}
```

## Files Changed
1. `PageManager.ts` - Call `clearStore()` when no pages available
2. `LivePreview.ts` - Add retry limit with helpful error message + auto-resubscription

## Additional Fixes Applied
After fixing the infinite retry loop, we discovered that even with the store initialized, components weren't receiving updates because they were subscribed to old store instances. See **FIX_LIVEPREVIEW_LOADING_FOREVER.md** for details on the auto-resubscription mechanism.

## Related Documentation
- **FIX_CANVAS_NOT_CLEARING.md** - Canvas clearing fix
- **FIX_LIVEPREVIEW_LOADING_FOREVER.md** - Component auto-sync fix
- **COMPLETE_FIX_SUMMARY.md** - All fixes summary
- **STORE_DRAFT_FIX.md** - Overall store management

---
**Date:** October 28, 2025  
**Status:** ✅ FIXED  
**Related:** FIX_CANVAS_NOT_CLEARING.md, FIX_LIVEPREVIEW_LOADING_FOREVER.md

