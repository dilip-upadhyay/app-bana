import { describe, it, expect, beforeEach } from 'vitest';
import { initTokens, getTokens, updateToken, undoTokenChange, redoTokenChange, resetTokens, getRecentlyEdited, canUndo, canRedo } from './TokenStore';

// jsdom provides localStorage

describe('TokenStore', () => {
  beforeEach(()=>{ localStorage.clear(); initTokens(); });

  it('initializes with defaults and persists updates', () => {
    const t1 = getTokens();
    expect(t1['color-brand']).toBeTruthy();
    updateToken('color-brand', '#123456');
    const t2 = getTokens();
    expect(t2['color-brand']).toBe('#123456');
    // simulate reload
    initTokens();
    const t3 = getTokens();
    expect(t3['color-brand']).toBe('#123456');
  });

  it('undo/redo restores previous token value', () => {
    const before = getTokens()['color-brand'];
    updateToken('color-brand', '#000000');
    expect(getTokens()['color-brand']).toBe('#000000');
    expect(canUndo()).toBe(true);
    undoTokenChange();
    expect(getTokens()['color-brand']).toBe(before);
    expect(canRedo()).toBe(true);
    redoTokenChange();
    expect(getTokens()['color-brand']).toBe('#000000');
  });

  it('reset is undoable', () => {
    updateToken('color-brand', '#111111');
    resetTokens();
    const afterReset = getTokens()['color-brand'];
    undoTokenChange(); // undo reset
    expect(getTokens()['color-brand']).toBe('#111111');
    redoTokenChange(); // redo reset
    expect(getTokens()['color-brand']).toBe(afterReset);
  });

  it('recently edited tracks most recent keys', () => {
    updateToken('color-brand', '#222222');
    updateToken('color-border', '#333333');
    const recent = getRecentlyEdited();
    expect(recent[0]).toBe('color-border');
    expect(recent).toContain('color-brand');
  });
});
