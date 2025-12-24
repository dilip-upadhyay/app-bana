import { LitElement, html, css } from 'lit';
import { property } from 'lit/decorators.js';

export class DynamicRenderer extends LitElement {
    @property({ type: String }) tagName = '';
    @property({ type: Object }) node: any = {};

    // Disable Shadow DOM to let children and styles penetrate easily (optional but simplifies layout)
    override createRenderRoot() {
        return this;
    }

    protected override render() {
        if (!this.tagName) return html``;

        // Lit doesn't support <${this.tagName}> directly. 
        // But we can use unsafeStatic if we import it.
        // However, to keep it simple and portable without messing with imports in Renderer.ts:
        // We render a wrapper that manually constructs the child?
        // OR we just use the unsafeStatic trick HERE in this isolated file.

        // Actually, since we are in a lit environment, let's use the static-html approach here.
        // It is safer to manage imports here than in the main Renderer.

        return html`<div style="display:contents" id="dynamic-wrapper-${this.node.id}"></div>`;
    }

    protected override updated() {
        // Manual DOM manipulation to append the custom element
        if (!this.tagName) return;
        const wrapper = this.querySelector(`#dynamic-wrapper-${this.node.id}`);
        if (wrapper && wrapper.children.length === 0) {
            try {
                const el = document.createElement(this.tagName);
                (el as any).node = this.node;
                // Move children?
                // Rendering children inside this dynamic element is tricky because of Lit's slotting.
                // But valid for now.
                wrapper.appendChild(el);

                // Move light DOM children into the new element?
                // Since Renderer.ts passes ${children} to <studio-dynamic-renderer>, they land in THIS element's innerHTML.
                // We need to move them.
                while (this.childNodes.length > 0) {
                    const child = this.childNodes[0];
                    if (child !== wrapper) {
                        el.appendChild(child);
                    } else {
                        break;
                    }
                }
            } catch (e) {
                wrapper.textContent = `Error creating <${this.tagName}>`;
            }
        }
    }
}

customElements.define('studio-dynamic-renderer', DynamicRenderer);
