import { ContainerElement } from './ContainerElement';
import { registerComponent } from '../core/registry';

/**
 * Basic Elements for layout primitives.
 * These map to generic container-like behavior but preserve their semantic type
 * for potential future specialization (e.g. Card could have a shadow/border by default).
 */

export class ListElement extends ContainerElement { }
export class CardElement extends ContainerElement { }
export class DetailElement extends ContainerElement { }
export class DashboardElement extends ContainerElement { }

// Register as Custom Elements
const els = {
    'studio-list': ListElement,
    'studio-card': CardElement,
    'studio-detail': DetailElement,
    'studio-dashboard': DashboardElement
};

for (const [tag, ctor] of Object.entries(els)) {
    if (!customElements.get(tag)) {
        customElements.define(tag, ctor);
    }
}

// Register in AppBana Registry
// Note: We will update registerComponent to accept tagName in next step.
// For now, we import this file in registry.ts to trigger execution.
