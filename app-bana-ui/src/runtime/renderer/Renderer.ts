import { PageMeta, ComponentNode } from '../../models/metadata';
import { getComponent, getComponentTagName } from '../../core/registry';
import { html, TemplateResult } from 'lit';
import { html as staticHtml, unsafeStatic } from 'lit/static-html.js';
import { ifDefined } from 'lit/directives/if-defined.js';
import './StudioTableLive';
import '../../components/GridElement';
import { renderTablePreview } from './TablePreview';
import { createRow, apiClient } from '../../core/api-client';

// FIX MINOR #11: Constants for timeouts and delays
const SAVE_TIMEOUT_MS = 30000; // 30 seconds
const TOAST_DURATION_MS = 3000; // 3 seconds
const NAVIGATION_DELAY_MS = 500; // 0.5 seconds
const ERROR_TOAST_DURATION_MS = 5000; // 5 seconds (longer for errors)

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
  if (!root) return html`< div class="error" > Root node not found: ${page.rootId} </div>`;
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

// UX Enhancement #2: Progress toast helpers for multi-entity saves
function createProgressToast(totalEntities: number): HTMLElement {
  const toast = document.createElement('div');
  toast.className = 'save-progress-toast';
  toast.innerHTML = `
    <div class="toast-content">
      <div class="toast-icon">📤</div>
      <div class="toast-text">
        <div class="toast-title">Saving entities...</div>
        <div class="toast-progress">
          <div class="progress-bar">
            <div class="progress-fill" style="width: 0%"></div>
          </div>
          <div class="progress-text">0/${totalEntities} saved</div>
        </div>
      </div>
    </div>
  `;

  toast.style.cssText = `
    position: fixed; bottom: 20px; right: 20px;
    background: white; color: #374151;
    padding: 16px 20px; border-radius: 8px;
    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    border-left: 4px solid #3b82f6;
    z-index: 9999;
    min-width: 280px;
    animation: slideIn 0.3s ease;
  `;

  return toast;
}

function updateProgressToast(toast: HTMLElement, saved: number, total: number): void {
  const progressFill = toast.querySelector('.progress-fill') as HTMLElement;
  const progressText = toast.querySelector('.progress-text') as HTMLElement;

  if (progressFill && progressText) {
    const percentage = (saved / total) * 100;
    progressFill.style.width = `${percentage}%`;
    progressFill.style.transition = 'width 0.3s ease';
    progressText.textContent = `${saved}/${total} saved`;
  }
}

function showSuccessToast(toast: HTMLElement, count: number): void {
  const icon = toast.querySelector('.toast-icon') as HTMLElement;
  const title = toast.querySelector('.toast-title') as HTMLElement;
  const progress = toast.querySelector('.toast-progress') as HTMLElement;

  if (icon) icon.textContent = '✅';
  if (title) title.textContent = `All ${count} ${count === 1 ? 'entity' : 'entities'} saved!`;
  if (progress) progress.style.display = 'none';

  toast.style.borderLeftColor = '#10b981';
}

function showErrorToast(toast: HTMLElement, saved: number, total: number, message: string): void {
  const icon = toast.querySelector('.toast-icon') as HTMLElement;
  const title = toast.querySelector('.toast-title') as HTMLElement;
  const progressDiv = toast.querySelector('.toast-progress') as HTMLElement;

  if (icon) icon.textContent = '❌';
  if (title) title.textContent = `Error saving entity ${saved + 1}/${total}`;
  if (progressDiv) {
    // CRITICAL FIX #1: Use textContent to prevent XSS (not innerHTML)
    const errorDiv = document.createElement('div');
    errorDiv.textContent = message; // Auto-escapes HTML entities
    errorDiv.style.cssText = 'font-size: 12px; color: #ef4444; margin-top: 4px;';
    progressDiv.innerHTML = ''; // Clear existing content
    progressDiv.appendChild(errorDiv);
  }

  toast.style.borderLeftColor = '#ef4444';
}

// CRITICAL FIX #2: Inject progress toast CSS styles (including slideIn animation)
(function injectProgressToastStyles() {
  if (document.getElementById('progress-toast-styles')) return; // Already injected

  const style = document.createElement('style');
  style.id = 'progress-toast-styles';
  style.textContent = `
    @keyframes slideIn {
      from {
        transform: translateX(400px);
        opacity: 0;
      }
      to {
        transform: translateX(0);
        opacity: 1;
      }
    }
    
    .progress-bar {
      width: 100%;
      height: 6px;
      background: #e5e7eb;
      border-radius: 3px;
      overflow: hidden;
      margin: 8px 0 4px 0;
    }
    
    .progress-fill {
      height: 100%;
      background: linear-gradient(90deg, #3b82f6, #2563eb);
      border-radius: 3px;
      transition: width 0.3s ease;
    }
    
    .progress-text {
      font-size: 11px;
      color: #6b7280;
      text-align: right;
    }
    
    .toast-content {
      display: flex;
      gap: 12px;
      align-items: flex-start;
    }
    
    .toast-icon {
      font-size: 20px;
      line-height: 1;
    }
    
    .toast-text {
      flex: 1;
    }
    
    .toast-title {
      font-weight: 600;
      font-size: 14px;
      margin-bottom: 4px;
      color: #374151;
    }
    
    /* NICE-TO-HAVE: Dark mode support */
    @media (prefers-color-scheme: dark) {
      .save-progress-toast {
        background: #1f2937 !important;
        color: #f3f4f6 !important;
        box-shadow: 0 4px 12px rgba(0,0,0,0.5) !important;
      }
      
      .toast-title {
        color: #f3f4f6;
      }
      
      .progress-text {
        color: #9ca3af;
      }
      
      .progress-bar {
        background: #374151;
      }
    }
  `;

  document.head.appendChild(style);
})();

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
    // FIX: Support Shadow DOM by searching within the component's root
    const root = button.getRootNode() as ParentNode;
    const container = button.closest('studio-form, form, .form-container, app-grid') || root;
    const allInputs = container.querySelectorAll('[entity][field]');

    console.log(`[Renderer] Found ${allInputs.length} inputs with entity/field bindings`);

    // Group data by entity
    const entityData = new Map<string, Record<string, any>>();
    const validationErrors: string[] = [];

    allInputs.forEach((input: any) => {
      const entity = input.getAttribute('entity');
      const field = input.getAttribute('field');

      if (!entity || !field) {
        console.warn('[Renderer] Input missing entity or field:', input);
        return;
      }

      // FIX IMPORTANT #8: Case-insensitive entity name matching
      if (!buttonEntities.map(e => e.toLowerCase()).includes(entity.toLowerCase())) {
        console.log(`[Renderer] Skipping ${entity}.${field} (not in button entities: ${buttonEntities.join(', ')})`);
        return;
      }

      // Collect value based on input type
      let value;
      const tagName = input.tagName.toLowerCase();
      const isRequired = input.hasAttribute('required') || input.required;
      const label = input.getAttribute('label') || field;

      if (tagName.includes('checkbox')) {
        value = input.checked;
      } else {
        value = input.value;

        // FIX CRITICAL #2: Skip empty/whitespace-only values for optional fields
        if (typeof value === 'string' && value.trim() === '') {
          if (isRequired) {
            // FIX CRITICAL #3: Validate required fields
            validationErrors.push(`${entity}.${label} is required`);
          }
          return; // Don't include empty optional fields
        }
      }

      // FIX CRITICAL #3: Validate required checkboxes
      if (isRequired && tagName.includes('checkbox') && !value) {
        validationErrors.push(`${entity}.${label} must be checked`);
      }

      // Initialize entity data if not exists
      if (!entityData.has(entity)) {
        entityData.set(entity, {});
      }

      entityData.get(entity)![field] = value;
      console.log(`[Renderer] Collected ${entity}.${field} = ${value}`);
    });

    // FIX CRITICAL #3: Check for validation errors
    if (validationErrors.length > 0) {
      alert(`Please fill in required fields:\n\n• ${validationErrors.join('\n• ')}`);
      console.error('[Renderer] Validation errors:', validationErrors);
      return;
    }

    // Validate we have data
    if (entityData.size === 0) {
      alert(`Error: No inputs found for entities: ${buttonEntities.join(', ')}\n\nMake sure your inputs have entity and field properties set.`);
      console.error('[Renderer] No data collected. Button entities:', buttonEntities);
      return;
    }

    console.log('[Renderer] Data grouped by entity:', Object.fromEntries(entityData));


    // Show loading state
    const originalLabel = button.getAttribute('label');

    // FIX IMPORTANT #6: Timeout protection - failsafe reset after 30s
    const resetButton = () => {
      button.setAttribute('label', originalLabel || 'Save');
      button.removeAttribute('disabled');
    };

    const timeoutId = setTimeout(() => {
      resetButton();
      console.warn('[Renderer] Save operation timed out');
      alert('⚠️ Save operation timed out. Please check your connection and try again.');
    }, SAVE_TIMEOUT_MS);

    // UX Enhancement #2: Create progress toast immediately
    const progressToast = createProgressToast(entityData.size);
    document.body.appendChild(progressToast);

    let savedCount = 0; // Track count for error handling (and progress)

    try {
      button.setAttribute('label', 'Saving...');
      button.setAttribute('disabled', 'true');

      // Save each entity sequentially with progress updates
      const results = [];

      for (const [entity, data] of entityData) {
        console.log(`[Renderer] → Saving ${entity}:`, data);
        const result = await createRow(entity, data);
        results.push({ entity, result });
        savedCount++;

        // Update progress after each save
        updateProgressToast(progressToast, savedCount, entityData.size);

        console.log(`[Renderer] ✓ Saved ${entity}:`, result);
      }

      // Clear timeout on success
      clearTimeout(timeoutId);

      // Success
      button.setAttribute('label', originalLabel || 'Save');
      button.removeAttribute('disabled');

      // Transform toast to success state
      setTimeout(() => {
        showSuccessToast(progressToast, results.length);
        setTimeout(() => progressToast.remove(), TOAST_DURATION_MS);
      }, 500); // Brief pause to show 100%

      // Handle onSuccess action
      const onSuccess = node.props?.onSuccess;
      if (onSuccess === 'navigate' && node.props?.navigateUrl) {
        setTimeout(() => {
          window.location.href = node.props!.navigateUrl;
        }, NAVIGATION_DELAY_MS);
      } else if (onSuccess === 'refresh') {
        setTimeout(() => {
          window.location.reload();
        }, NAVIGATION_DELAY_MS);
      }

    } catch (error: any) {
      clearTimeout(timeoutId);
      button.setAttribute('label', originalLabel || 'Save');
      button.removeAttribute('disabled');

      // Update progress toast to show error
      const progressToast = document.querySelector('.save-progress-toast') as HTMLElement;
      if (progressToast) {
        // CRITICAL FIX #4: Use savedCount from outer scope, not error object
        showErrorToast(progressToast, savedCount, entityData.size, error.message || 'Failed to save data');
        setTimeout(() => progressToast.remove(), ERROR_TOAST_DURATION_MS);
      } else {
        console.error('[Renderer] Save failed:', error);
        alert(`❌ Error: ${error.message || 'Failed to save data'}`);
      }
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
