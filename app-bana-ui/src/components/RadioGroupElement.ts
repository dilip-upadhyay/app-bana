import { FormElement } from './FormElement';
import { registerComponent } from '../core/registry';

export class RadioGroupElement extends FormElement {
  static get observedAttributes() {
    return ['label', 'value', 'options', 'name', 'required', 'disabled', 'direction', 'entity'];
  }

  attributeChangedCallback() {
    this.requestRender();
  }

  async connectedCallback() {
    await this.loadFieldPermissionsFromAttribute();
  }

  private parseOptions(): Array<{ value: string; label: string }> {
    const optionsAttr = this.getAttribute('options') || '';
    try {
      const parsed = JSON.parse(optionsAttr);
      if (Array.isArray(parsed)) {
        return parsed.map(opt => typeof opt === 'string' ? { value: opt, label: opt } : opt);
      }
    } catch { }
    if (optionsAttr) {
      return optionsAttr.split(',').map(opt => {
        const t = opt.trim();
        return { value: t, label: t };
      });
    }
    return [];
  }

  protected render(): string {
    const fieldName = this.getAttribute('name') || '';
    if (this.isFieldHidden(fieldName)) return this.renderHiddenField();

    const label = this.getAttribute('label') || '';
    const value = this.getAttribute('value') || '';
    const name = this.getAttribute('name') || `radio-${Math.random().toString(36).slice(2)}`;
    const required = this.hasAttribute('required');
    const disabled = this.hasAttribute('disabled');
    const direction = this.getAttribute('direction') || 'vertical';
    const options = this.parseOptions();

    const flsDisabled = this.isFieldDisabled(fieldName);
    const isDisabled = disabled || flsDisabled;
    const lockIcon = flsDisabled ? this.getLockIcon() : '';
    const title = flsDisabled ? this.getDisabledTooltip() : '';

    const optionsHtml = options.map(opt => `
      <label class="radio-option">
        <input type="radio" 
          name="${name}" 
          value="${opt.value}" 
          ${value === opt.value ? 'checked' : ''}
          ${isDisabled ? 'disabled' : ''}
          ${required ? 'required' : ''}
        >
        <span>${opt.label}</span>
      </label>
    `).join('');

    return `
      <div class="radio-group" title="${title}">
        ${label ? `<div class="group-label">${label}${lockIcon}${required ? '<span class="required">*</span>' : ''}</div>` : ''}
        <div class="options ${direction}">
          ${optionsHtml}
        </div>
      </div>
    `;
  }

  protected styles(): string {
    return `
      :host { display: block; font-family: var(--font-sans, system-ui); }
      .group-label { font-size: 0.875rem; font-weight: 500; color: #374151; margin-bottom: 0.5rem; }
      .required { color: #ef4444; margin-left: 0.25rem; }
      .options { display: flex; gap: 0.75rem; }
      .options.vertical { flex-direction: column; gap: 0.5rem; }
      .radio-option { display: flex; align-items: center; gap: 0.5rem; cursor: pointer; font-size: 0.875rem; color: #374151; }
      input[type="radio"] { cursor: pointer; }
      input:disabled { cursor: not-allowed; opacity: 0.6; }
      input:disabled + span { opacity: 0.6; }
    `;
  }
}

if (!customElements.get('studio-radio-group')) {
  customElements.define('studio-radio-group', RadioGroupElement);
}
registerComponent('radio-group', RadioGroupElement);
