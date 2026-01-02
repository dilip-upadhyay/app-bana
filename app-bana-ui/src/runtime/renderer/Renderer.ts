import { PageMeta, ComponentNode } from '../../models/metadata';
import { getComponent, getComponentTagName } from '../../core/registry';
import { html, TemplateResult } from 'lit';
import { html as staticHtml, unsafeStatic } from 'lit/static-html.js';
import { ifDefined } from 'lit/directives/if-defined.js';
import './StudioTableLive';
import '../../components/GridElement';
import { renderTablePreview } from './TablePreview';
import { createRow, apiClient } from '../../core/api-client';

// Helper for simple handle-bars style interpolation
// e.g. interpolate("Hello {{user.name}}", { user: { name: "World" } }) -> "Hello World"
function interpolate(text: string, context: any): string {
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

/**
 * Returns a Lit html template for a PageMeta tree using the component registry.
 * Used for Lit-based reactive rendering.
 */
export function renderPageTemplate(page: PageMeta, context: any = {}): TemplateResult {
  const nodeMap = new Map(page.nodes.map(n => [n.id, n]));
  const root = nodeMap.get(page.rootId);
  if (!root) return html`<div class="error">Root node not found: ${page.rootId}</div>`;
  return renderNodeTemplate(root, nodeMap, context);
}

function renderNodeTemplate(node: ComponentNode, nodeMap: Map<string, ComponentNode>, context: any): TemplateResult {
  // Interpolate props
  const props = { ...node.props };
  for (const [key, value] of Object.entries(props)) {
    if (typeof value === 'string') {
      props[key] = interpolate(value, context);
    }
  }

  const style = props?.style || '';
  const className = props?.className || '';

  // Compose children recursively
  const children = (node.children ?? []).map(childId => {
    const child = nodeMap.get(childId);
    return child ? renderNodeTemplate(child, nodeMap, context) : null;
  });

  // Handle each component type with proper attribute binding (like LivePreview)
  switch (node.type) {
    case 'text':
      return html`
        <appbana-text
          class="${className}"
          style="${style}"
          content="${props?.content || props?.text || ''}"
          variant="${props?.variant || 'body'}"
          align="${props?.align || 'left'}"
          color="${props?.color || ''}"
          tag="${props?.tag || 'p'}"
        ></appbana-text>
      `;

    case 'button':
      return html`
        <appbana-button
          class="${className}"
          style="${style}"
          label="${props?.label || props?.text || 'Button'}"
          type="${props?.type || 'button'}"
          variant="${props?.variant || 'primary'}"
          ?disabled=${props?.disabled}
          @click=${(e: Event) => handleAction(node, e)}
        ></appbana-button>
      `;

    case 'input':
      return html`
        <appbana-input
          class="${className}"
          style="${style}"
          label="${props?.label || ''}"
          type="${props?.type || 'text'}"
          placeholder="${props?.placeholder || ''}"
          value="${props?.value || ''}"
          name="${props?.name || ''}"
          entity="${props?.entity || ''}"
          field="${props?.field || ''}"
          ?required=${props?.required}
        ></appbana-input>
      `;

    case 'select':
      return html`
        <appbana-select
          class="${className}"
          style="${style}"
          label="${props?.label || ''}"
          name="${props?.name || ''}"
          value="${props?.value || ''}"
          entity="${props?.entity || ''}"
          field="${props?.field || ''}"
          options="${typeof props?.options === 'string' ? props.options : JSON.stringify(props?.options || [])}"
          placeholder="${props?.placeholder || ''}"
          ?required=${props?.required}
        ></appbana-select>
      `;

    case 'textarea':
      return html`
        <appbana-textarea
          class="${className}"
          style="${style}"
          label="${props?.label || ''}"
          placeholder="${props?.placeholder || ''}"
          value="${props?.value || ''}"
          name="${props?.name || ''}"
          entity="${props?.entity || ''}"
          field="${props?.field || ''}"
          rows="${props?.rows || 4}"
          ?required=${props?.required}
        ></appbana-textarea>
      `;

    case 'checkbox':
      return html`
        <appbana-checkbox
          class="${className}"
          style="${style}"
          label="${props?.label || ''}"
          name="${props?.name || ''}"
          value="${props?.value || 'on'}"
          entity="${props?.entity || ''}"
          field="${props?.field || ''}"
          ?checked=${props?.checked}
          ?disabled=${props?.disabled}
        ></appbana-checkbox>
      `;

    case 'radio-group':
      return html`
        <appbana-radio-group
          class="${className}"
          style="${style}"
          label="${props?.label || ''}"
          name="${props?.name || ''}"
          value="${props?.value || ''}"
          options="${typeof props?.options === 'string' ? props.options : JSON.stringify(props?.options || [])}"
          layout="${props?.layout || 'vertical'}"
          ?required=${props?.required}
          ?disabled=${props?.disabled}
        ></appbana-radio-group>
      `;

    case 'table':
    case 'grid':
    case 'appbana-table-live':
      return html`
        <appbana-table-live
          class="${className}"
          style="${style}"
          .node=${node}
          entity="${props?.entity || ''}"
          view-mode="${props?.viewMode || 'dynamic'}"
          theme="${props?.theme || 'default'}"
        ></appbana-table-live>
      `;

    case 'app-grid':
      return html`
        <app-grid
          class="${className}"
          style="${style}"
          cols="${props?.cols || 2}"
          rows="${props?.rows || 2}"
          gap="${props?.gap || '16px'}"
          .minCellHeight="${props?.minCellHeight || 'auto'}"
        >
          ${children}
        </app-grid>
      `;

    case 'form':
    case 'studio-form':
      return html`
        <studio-form
          class="${className}"
          style="${style}"
          entity="${props?.entity || ''}"
          record-id="${props?.['record-id'] || ''}"
        >
          ${children}
        </studio-form>
      `;

    case 'img':
      return html`
        <img
          src="${props?.src || ''}"
          alt="${props?.alt || ''}"
          class="${className}"
          style="${style}"
        />
      `;

    case 'a':
      return html`
        <a
          href="${props?.href || '#'}"
          class="${className}"
          style="${style}"
        >
          ${props?.text || 'Link'}
        </a>
      `;

    case 'container':
    case 'section':
    case 'div':
    default:
      // For containers and unknown types, just render a div with children
      return html`
        <div
          id="${node.id}"
          class="${className}"
          style="${style}"
        >
          ${children}
        </div>
      `;
  }
}

// Helper wrapper to avoid needing unsafeStatic in the main file if imports are tricky?
// No, simpler to just fix imports.

// ES module import workaround for browser
function requireTablePreview(node: ComponentNode): HTMLElement {
  return renderTablePreview(node);
}

// Action Handler implementation for Runtime
async function handleAction(node: ComponentNode, event: Event) {
  const actionType = node.props?.actionType;
  console.log('[Renderer] Handling action:', actionType, node.props);

  if (!actionType) return;

  const button = event.target as HTMLElement;

  if (actionType === 'save-entity') {
    const buttonEntities: string[] = node.props?.entities || [];

    // Validate button configuration
    if (buttonEntities.length === 0) {
      alert('Error: This button has no entities configured.');
      console.error('[Renderer] Button missing entities configuration:', node);
      return;
    }

    // Collect all inputs with entity bindings
    const container = button.closest('studio-form, form, .form-container, app-grid') || document.body;
    const allInputs = container.querySelectorAll('[entity][field]');

    console.log(`[Renderer] Found ${allInputs.length} inputs with entity/field bindings`);

    // Group data by entity
    const entityData = new Map<string, Record<string, any>>();

    allInputs.forEach((input: any) => {
      const entity = input.getAttribute('entity');
      const field = input.getAttribute('field');

      if (!entity || !field) {
        console.warn('[Renderer] Input missing entity or field:', input);
        return;
      }

      // ✅ KEY FILTER: Only collect data for entities specified in button
      if (!buttonEntities.includes(entity)) {
        console.log(`[Renderer] Skipping ${entity}.${field} (not in button entities: ${buttonEntities.join(', ')})`);
        return;
      }

      // Initialize entity data if not exists
      if (!entityData.has(entity)) {
        entityData.set(entity, {});
      }

      // Collect value based on input type
      let value;
      const tagName = input.tagName.toLowerCase();
      if (tagName.includes('checkbox')) {
        value = input.checked;
      } else {
        value = input.value;
      }

      entityData.get(entity)![field] = value;
      console.log(`[Renderer] Collected ${entity}.${field} = ${value}`);
    });

    // Validate we have data
    if (entityData.size === 0) {
      alert(`Error: No inputs found for entities: ${buttonEntities.join(', ')}\n\nMake sure your inputs have entity and field properties set.`);
      console.error('[Renderer] No data collected. Button entities:', buttonEntities);
      return;
    }

    console.log('[Renderer] Data grouped by entity:', Object.fromEntries(entityData));


    // Show loading state
    const originalLabel = button.getAttribute('label');

    try {
      button.setAttribute('label', 'Saving...');
      button.setAttribute('disabled', 'true');

      // Save each entity sequentially
      const results = [];
      for (const [entity, data] of entityData) {
        console.log(`[Renderer] → Saving ${entity}:`, data);
        const result = await createRow(entity, data);
        results.push({ entity, result });
        console.log(`[Renderer] ✓ Saved ${entity}:`, result);
      }

      // Success
      button.setAttribute('label', originalLabel || 'Save');
      button.removeAttribute('disabled');

      // Show success toast
      const toast = document.createElement('div');
      toast.textContent = `✅ Saved ${results.length} ${results.length === 1 ? 'entity' : 'entities'} successfully!`;
      toast.style.cssText = `
        position: fixed; bottom: 20px; right: 20px; 
        background: #10b981; color: white; padding: 12px 24px; 
        border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); 
        z-index: 9999; animation: slideIn 0.3s ease;
      `;
      document.body.appendChild(toast);
      setTimeout(() => toast.remove(), 3000);

      // Handle onSuccess action
      const onSuccess = node.props?.onSuccess;
      if (onSuccess === 'navigate' && node.props?.navigateUrl) {
        setTimeout(() => {
          window.location.href = node.props!.navigateUrl;
        }, 500);
      } else if (onSuccess === 'refresh') {
        setTimeout(() => {
          window.location.reload();
        }, 500);
      }

    } catch (error: any) {
      button.setAttribute('label', originalLabel || 'Save');
      button.removeAttribute('disabled');
      console.error('[Renderer] Save failed:', error);
      alert(`❌ Error: ${error.message || 'Failed to save data'}`);
    }


  } else if (actionType === 'navigate') {
    if (node.props?.navigateUrl) {
      window.location.href = node.props.navigateUrl;
    }
  } else if (actionType === 'api') {
    // Generic API call implementation
    try {
      const endpoint = node.props?.apiEndpoint;
      const method = node.props?.apiMethod || 'POST';
      if (!endpoint) return;

      await apiClient.request(endpoint, { method, body: {} }); // Simplify for now
      alert('API call successful');
    } catch (e) {
      alert('API call failed');
    }
  }
}
