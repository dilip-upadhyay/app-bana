import { ComponentNode } from '../../models/metadata';
import { createRow } from '../../core/api-client';

const SAVE_TIMEOUT_MS = 30000;
const NAVIGATION_DELAY_MS = 500;

// Helper for simple handle-bars style interpolation
export function interpolate(text: string, context: any): string {
    if (!text || typeof text !== 'string') return text;
    return text.replace(/\{\{([^}]+)\}\}/g, (_, path) => {
        const keys = path.trim().split('.');
        let value = context;
        for (const key of keys) {
            if (value === undefined || value === null) return '';
            value = value[key];
        }
        return value !== undefined ? String(value) : '';
    });
}

// Success action helpers (to avoid depending on DOM elements if not needed)
function triggerNavigation(url: string) {
    setTimeout(() => {
        window.location.href = url;
    }, NAVIGATION_DELAY_MS);
}

export async function handleAction(node: ComponentNode, event: Event, context: any = {}) {
    const actionType = node.props?.actionType;
    console.log('[ActionHandler] Handling action:', actionType, node.props);

    if (!actionType) return;

    const button = event.target as HTMLElement;

    if (actionType === 'save-entity' || actionType === 'save') {
        const buttonEntities: string[] = node.props?.entities || [];

        if (buttonEntities.length === 0) {
            alert('Error: This button has no entities configured.');
            return;
        }

        // Collect data from the DOM (scoped to the closest form/grid)
        const root = button.getRootNode() as ParentNode;
        const container = button.closest('studio-form, form, .form-container, app-grid') || root;
        const allInputs = container.querySelectorAll('[entity][field]');

        const entityData = new Map<string, Record<string, any>>();
        const validationErrors: string[] = [];

        // 1. Collect from Inputs
        allInputs.forEach((input: any) => {
            const entity = input.getAttribute('entity');
            const field = input.getAttribute('field');
            if (!entity || !field) return;

            if (!buttonEntities.map(e => e.toLowerCase()).includes(entity.toLowerCase())) return;

            let value;
            const tagName = input.tagName.toLowerCase();
            const isRequired = input.hasAttribute('required') || input.required;
            const label = input.getAttribute('label') || field;

            if (tagName.includes('checkbox')) {
                value = input.checked;
            } else {
                value = input.value;
                if (typeof value === 'string' && value.trim() === '') {
                    if (isRequired) validationErrors.push(`${entity}.${label} is required`);
                    return;
                }
            }

            if (!entityData.has(entity)) entityData.set(entity, {});
            entityData.get(entity)![field] = value;
        });

        // 2. Merge Fixed Fields (Generic Feature)
        const fixedFields = node.props?.fixedFields;
        if (fixedFields && typeof fixedFields === 'object') {
            for (const [entity, fields] of Object.entries(fixedFields)) {
                if (typeof fields === 'object') {
                    if (!entityData.has(entity)) entityData.set(entity, {});
                    const target = entityData.get(entity)!;
                    for (const [key, rawValue] of Object.entries(fields as object)) {
                        const value = typeof rawValue === 'string' ? interpolate(rawValue, context) : rawValue;
                        target[key] = value;
                    }
                }
            }
        }

        if (validationErrors.length > 0) {
            alert(`Please fill in required fields:\n\n• ${validationErrors.join('\n• ')}`);
            return;
        }

        try {
            const originalLabel = button.getAttribute('label');
            button.setAttribute('label', 'Saving...');
            button.setAttribute('disabled', 'true');

            for (const [entity, data] of entityData) {
                await createRow(entity, data);
            }

            button.setAttribute('label', originalLabel || 'Save');
            button.removeAttribute('disabled');

            // Handle onSuccess
            const onSuccess = node.props?.onSuccess;
            if (onSuccess === 'navigate' && node.props?.navigateUrl) {
                triggerNavigation(interpolate(node.props.navigateUrl, context));
            } else if (onSuccess === 'refresh') {
                window.location.reload();
            }

        } catch (error: any) {
            button.setAttribute('label', 'Error');
            button.removeAttribute('disabled');
            alert(`❌ Error: ${error.message || 'Failed to save data'}`);
        }

    } else if (actionType === 'navigate') {
        if (node.props?.navigateUrl) {
            window.location.href = interpolate(node.props.navigateUrl, context);
        }
    }
}
