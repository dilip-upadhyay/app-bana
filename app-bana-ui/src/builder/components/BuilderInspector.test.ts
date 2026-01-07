import { describe, it, expect, beforeEach, vi } from 'vitest';
import './BuilderInspector';
import { currentStore } from '../store/TreeStore';
import { registerComponentDefinition, PropType } from '../../core/component-metadata';

// Mock the store
const onChangeMock = vi.fn();
const updatePropsMock = vi.fn();
const getSelectionMock = vi.fn();

vi.mock('../store/TreeStore', () => ({
    currentStore: {
        getSelection: (...args: any[]) => getSelectionMock(...args),
        onChange: (...args: any[]) => onChangeMock(...args),
        updateProps: (...args: any[]) => updatePropsMock(...args),
    }
}));

function nextTick() { return new Promise(r => setTimeout(r, 0)); }

describe('BuilderInspector', () => {
    let element: any; // Cast to any to access private properties if needed
    let changeCallback: () => void;

    beforeEach(async () => {
        vi.clearAllMocks();
        document.body.innerHTML = '';

        // Capture the callback
        onChangeMock.mockImplementation((cb) => {
            changeCallback = cb;
            return () => { };
        });

        element = document.createElement('studio-builder-inspector');
        document.body.appendChild(element);
        await nextTick();
    });

    it('renders empty state when no node selected', () => {
        expect(element.shadowRoot?.textContent).toContain('No component selected');
    });

    it('renders dynamic form based on metadata', async () => {
        // Register a test component
        registerComponentDefinition({
            type: 'test-comp',
            label: 'Test Component',
            props: [
                { name: 'testProp', label: 'Test Prop', type: PropType.Text, group: 'content' }
            ]
        });

        // Mock selection
        getSelectionMock.mockReturnValue({
            id: 'node-1',
            type: 'test-comp',
            props: { testProp: 'initial value' }
        });

        // Trigger update via callback
        if (changeCallback) changeCallback();
        await element.updateComplete;

        const input = element.shadowRoot?.querySelector('input');
        expect(input).toBeTruthy();
        expect(input?.value).toBe('initial value');
    });

    it('updates props on input', async () => {
        // Register a test component
        registerComponentDefinition({
            type: 'test-comp-2',
            label: 'Test Component 2',
            props: [
                { name: 'myProp', label: 'My Prop', type: PropType.Text }
            ]
        });

        // Mock selection
        getSelectionMock.mockReturnValue({
            id: 'node-2',
            type: 'test-comp-2',
            props: { myProp: '' }
        });

        // Trigger update via callback
        if (changeCallback) changeCallback();
        await element.updateComplete;

        const input = element.shadowRoot?.querySelector('input');
        expect(input).toBeTruthy();

        if (input) {
            input.value = 'new value';
            input.dispatchEvent(new Event('input'));
        }

        expect(updatePropsMock).toHaveBeenCalledWith('node-2', { myProp: 'new value' });
    });
});
