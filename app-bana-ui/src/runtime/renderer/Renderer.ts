import { PageMeta, ComponentNode } from '../../models/metadata';
import { getComponent, getComponentTagName } from '../../core/registry';
import { html, TemplateResult } from 'lit';
import { html as staticHtml, unsafeStatic } from 'lit/static-html.js';
import { ifDefined } from 'lit/directives/if-defined.js';
import './StudioTableLive';
import '../../components/GridElement';
import { renderTablePreview } from './TablePreview';
import { createRow, apiClient } from '../../core/api-client';
import { handleAction, interpolate } from '../core/ActionHandler';

const SAVE_TIMEOUT_MS = 30000;
const TOAST_DURATION_MS = 3000;
const NAVIGATION_DELAY_MS = 500;
const ERROR_TOAST_DURATION_MS = 5000;


/**
 * Returns a Lit html template for a PageMeta tree using the component registry.
 * Used for Lit-based reactive rendering.
 */
export function renderPageTemplate(page: PageMeta, context: any = {}): TemplateResult {
  const nodeMap = new Map(page.nodes.map(n => [n.id, n]));
  const root = nodeMap.get(page.rootId);
  if (!root) return html`< div class="error" > Root node not found: ${page.rootId} </div>`;
  return renderNodeTemplate(root, nodeMap, context, page.id);
}

function renderNodeTemplate(node: ComponentNode, nodeMap: Map<string, ComponentNode>, context: any, pageId = ''): TemplateResult {
  // Interpolate props
  const props = { ...node.props };
  for (const [key, value] of Object.entries(props)) {
    if (typeof value === 'string') {
      props[key] = interpolate(value, context);
    }
  }

  // Handle styles including new marginBottom property
  let style = props?.style || '';
  if (props?.marginBottom) {
    style += `; margin-bottom: ${props.marginBottom}`;
  }
  if (props?.padding) {
    style += `; padding: ${props.padding}`;
  }
  if (props?.gap) {
    style += `; gap: ${props.gap}`;
  }
  if (props?.minHeight) {
    style += `; min-height: ${props.minHeight}`;
  }
  if (props?.layout) {
    style += `; flex-direction: ${props.layout}`;
  }
  if (props?.backgroundColor) {
    style += `; background-color: ${props.backgroundColor}`;
  }
  const className = props?.className || '';

  // Compose children recursively
  const children = (node.children ?? []).map(childId => {
    const child = nodeMap.get(childId);
    return child ? renderNodeTemplate(child, nodeMap, context, pageId) : null;
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
          type="${props?.type || props?.inputType || 'text'}"
          placeholder="${props?.placeholder || ''}"
          value="${props?.value || ''}"
          name="${props?.name || ''}"
          entity="${props?.entity || ''}"
          field="${props?.field || ''}"
          ?required=${props?.required}
          data-appbana-node="${node.id}"
          data-appbana-page="${pageId}"
          data-appbana-entity="${props?.entity || ''}"
          data-appbana-field="${props?.field || props?.name || ''}"
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
          data-appbana-node="${node.id}"
          data-appbana-page="${pageId}"
          data-appbana-entity="${props?.entity || ''}"
          data-appbana-field="${props?.field || props?.name || ''}"
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
          data-appbana-node="${node.id}"
          data-appbana-page="${pageId}"
          data-appbana-entity="${props?.entity || ''}"
          data-appbana-field="${props?.field || props?.name || ''}"
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
          data-appbana-node="${node.id}"
          data-appbana-page="${pageId}"
          data-appbana-entity="${props?.entity || ''}"
        ></appbana-table-live>
      `;

    case 'app-grid':
      return html`
        <app-grid
          class="${className}"
          style="${style}"
          cols="${props?.cols || 2}"
          rows="${props?.rows || 2}"
          gap="${props?.gap ?? '16px'}"
          .minCellHeight="${props?.minCellHeight ?? 'auto'}"
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

    case 'iframe':
      return html`
        <iframe
          src="${props?.src || ''}"
          title="${props?.title || 'Embedded content'}"
          style="border:none;width:100%;height:calc(100vh - 60px);display:block;${style}"
          class="${className}"
          allowfullscreen
        ></iframe>
      `;

    case 'container':
    case 'section':
    case 'div':
    default:
      // For containers and unknown types, just render a div with children
      // CRITICAL: Include slot attribute for app-grid cell assignment
      const slot = props?.slot || '';
      return html`
        <div
          id="${node.id}"
          class="${className}"
          style="${style}"
          slot="${slot}"
          data-appbana-node="${node.id}"
          data-appbana-page="${pageId}"
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
