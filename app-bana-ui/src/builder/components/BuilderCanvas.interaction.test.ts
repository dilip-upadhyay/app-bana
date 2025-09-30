import { describe, it, expect, beforeEach } from 'vitest';
import './BuilderCanvas';
import { currentStore } from '../store/TreeStore';

function nextTick() { return new Promise(r=>setTimeout(r,0)); }

// Helper to dispatch keyboard events to element
function key(el: HTMLElement, key: string, opts: any = {}) {
  const evt = new KeyboardEvent('keydown', { key, bubbles:true, cancelable:true, ...opts });
  el.dispatchEvent(evt);
}

describe('BuilderCanvas interactions', () => {
  let canvas: HTMLElement;

  beforeEach(async () => {
    // Clear DOM & localStorage between runs
    document.body.innerHTML='';
    localStorage.clear();
    canvas = document.createElement('studio-builder-canvas');
    document.body.appendChild(canvas);
    await nextTick(); // allow initial render
  });

  it('inline edit text node via Enter and commit', async () => {
    currentStore!.select('text-1');
    key(canvas, 'Enter'); // start editing
    await nextTick();
    const input = (canvas.shadowRoot!.querySelector('.inline-edit') as HTMLInputElement);
    expect(input).toBeTruthy();
    input.value = 'Edited Value';
    input.dispatchEvent(new Event('input', { bubbles:true }));
    input.blur(); // commit via blur
    await nextTick();
    expect(currentStore!.getNode('text-1')!.props!.text).toBe('Edited Value');
  });

  it('opens palette with Ctrl+P and filters results', async () => {
    key(canvas, 'p', { ctrlKey:true });
    await nextTick();
    const backdrop = canvas.shadowRoot!.querySelector('.palette-backdrop');
    expect(backdrop).toBeTruthy();
    const input = canvas.shadowRoot!.querySelector('.palette input') as HTMLInputElement;
    input.value = 'button-1';
    input.dispatchEvent(new Event('input', { bubbles:true }));
    await nextTick();
    const lis = Array.from(canvas.shadowRoot!.querySelectorAll('.palette li')).filter(li=>!li.textContent?.includes('No matches'));
    expect(lis.length).toBe(1);
    expect(lis[0].textContent).toMatch(/button-1/);
  });

  it('delete key removes selected leaf without confirm', async () => {
    currentStore!.select('button-1');
    key(canvas, 'Delete');
    await nextTick();
    expect(currentStore!.getNode('button-1')).toBeUndefined();
  });

  it('delete key with confirm removes subtree when confirmed', async () => {
    // create a container with a child under root
    currentStore!.addNode('root-container', { id: 'c-sub', type: 'container', children: [] } as any);
    currentStore!.addNode('c-sub', { id: 'c-leaf', type: 'text', props: { text: 'leaf' } } as any);
    currentStore!.select('c-sub');
    let confirmCalled = 0;
    const origConfirm = window.confirm;
    (window as any).confirm = (msg: string)=>{ confirmCalled++; return true; };
    key(canvas, 'Delete');
    await nextTick();
    expect(confirmCalled).toBe(1);
    expect(currentStore!.getNode('c-sub')).toBeUndefined();
    expect(currentStore!.getNode('c-leaf')).toBeUndefined();
    window.confirm = origConfirm;
  });

  it('delete key with confirm does not remove subtree when cancelled', async () => {
    currentStore!.addNode('root-container', { id: 'c-sub2', type: 'container', children: [] } as any);
    currentStore!.addNode('c-sub2', { id: 'c-leaf2', type: 'text', props: { text: 'leaf2' } } as any);
    currentStore!.select('c-sub2');
    const origConfirm = window.confirm;
    (window as any).confirm = ()=> false;
    key(canvas, 'Delete');
    await nextTick();
    expect(currentStore!.getNode('c-sub2')).toBeTruthy();
    expect(currentStore!.getNode('c-leaf2')).toBeTruthy();
    window.confirm = origConfirm;
  });

  it('expanded state persists across re-instantiation', async () => {
    // Expand root already expanded; add child container to test toggle persistence
    // Duplicate existing text node to create more nodes and toggle collapse
    currentStore!.duplicate('text-1');
    await nextTick();
    const rootToggle = canvas.shadowRoot!.querySelector('.expand-btn') as HTMLButtonElement;
    // Collapse root
    rootToggle.click();
    await nextTick();
    document.body.removeChild(canvas);
    // Recreate canvas
    canvas = document.createElement('studio-builder-canvas');
    document.body.appendChild(canvas);
    await nextTick();
    // Root should reflect persisted collapsed state (children container not rendered)
    const childrenSection = canvas.shadowRoot!.querySelector('.children');
    expect(childrenSection).toBeNull();
  });
});
