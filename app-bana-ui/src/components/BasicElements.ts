import { ContainerElement } from './ContainerElement';


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
    'appbana-list': ListElement,
    'appbana-card': CardElement,
    'appbana-detail': DetailElement,
    'appbana-dashboard': DashboardElement
};

for (const [tag, ctor] of Object.entries(els)) {
    if (!customElements.get(tag)) {
        customElements.define(tag, ctor);
    }
}

// Register in AppBana Registry
// Explicitly pass tagName now
import { registerComponent } from '../core/registry';
registerComponent('list', ListElement, 'appbana-list');
registerComponent('card', CardElement, 'appbana-card');
registerComponent('detail', DetailElement, 'appbana-detail');
registerComponent('dashboard', DashboardElement, 'appbana-dashboard');
