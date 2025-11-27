/**
 * Condition Builder Component
 * Visual editor for building conditional expressions
 */

import { LitElement, html, css } from 'lit';
import { customElement, state, property } from 'lit/decorators.js';
import type { TransitionCondition, ConditionOperator, FieldType } from '../../models/workflow';

@customElement('condition-builder')
export class ConditionBuilder extends LitElement {

    @property({ type: Object }) condition?: TransitionCondition;
    @property({ type: Array }) availableFields: Array<{ name: string; type: FieldType; label: string }> = [];
    @property({ type: Boolean }) compact = false;

    @state() private selectedField = '';
    @state() private selectedOperator: ConditionOperator = 'equals';
    @state() private conditionValue: any = '';
    @state() private showNaturalLanguage = false;

    static styles = css`
    :host {
      display: block;
    }

    .condition-builder {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      padding: 1rem;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
    }

    .condition-builder.compact {
      padding: 0.5rem;
      gap: 0.5rem;
    }

    .builder-row {
      display: flex;
      gap: 0.5rem;
      align-items: center;
      flex-wrap: wrap;
    }

    .field-label {
      font-size: 0.75rem;
      font-weight: 500;
      color: #64748b;
      min-width: 60px;
    }

    select, input {
      flex: 1;
      min-width: 120px;
      padding: 0.5rem;
      border: 1px solid #cbd5e0;
      border-radius: 4px;
      font-size: 0.875rem;
      background: white;
    }

    select:focus, input:focus {
      outline: none;
      border-color: #3b82f6;
      box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
    }

    .operator-select {
      min-width: 150px;
    }

    .natural-language {
      padding: 0.75rem;
      background: #eff6ff;
      border: 1px solid #bfdbfe;
      border-radius: 4px;
      font-size: 0.875rem;
      color: #1e40af;
      font-style: italic;
    }

    .expression-preview {
      padding: 0.75rem;
      background: #f1f5f9;
      border: 1px solid #cbd5e0;
      border-radius: 4px;
      font-family: 'Monaco', 'Courier New', monospace;
      font-size: 0.8rem;
      color: #334155;
    }

    .toggle-btn {
      padding: 0.25rem 0.5rem;
      background: transparent;
      border: 1px solid #cbd5e0;
      border-radius: 4px;
      cursor: pointer;
      font-size: 0.75rem;
      color: #64748b;
    }

    .toggle-btn:hover {
      background: #f1f5f9;
      border-color: #94a3b8;
    }

    .error-message {
      color: #dc2626;
      font-size: 0.75rem;
      padding: 0.5rem;
      background: #fef2f2;
      border-radius: 4px;
    }

    .hint {
      font-size: 0.7rem;
      color: #94a3b8;
      margin-top: -0.5rem;
    }
  `;

    connectedCallback() {
        super.connectedCallback();
        if (this.condition) {
            this.selectedField = this.condition.field || '';
            this.selectedOperator = this.condition.operator || 'equals';
            this.conditionValue = this.condition.value || '';
        }
    }

    private getOperators(): Array<{ value: ConditionOperator; label: string }> {
        const fieldType = this.availableFields.find(f => f.name === this.selectedField)?.type || 'string';

        const allOperators: Array<{ value: ConditionOperator; label: string; types: FieldType[] }> = [
            { value: 'equals', label: 'equals (=)', types: ['string', 'number', 'boolean', 'date'] },
            { value: 'notEquals', label: 'not equals (≠)', types: ['string', 'number', 'boolean', 'date'] },
            { value: 'greaterThan', label: 'greater than (>)', types: ['number', 'date'] },
            { value: 'lessThan', label: 'less than (<)', types: ['number', 'date'] },
            { value: 'greaterThanOrEqual', label: 'greater or equal (≥)', types: ['number', 'date'] },
            { value: 'lessThanOrEqual', label: 'less or equal (≤)', types: ['number', 'date'] },
            { value: 'contains', label: 'contains', types: ['string', 'array'] },
            { value: 'notContains', label: 'does not contain', types: ['string', 'array'] },
            { value: 'startsWith', label: 'starts with', types: ['string'] },
            { value: 'endsWith', label: 'ends with', types: ['string'] },
            { value: 'isEmpty', label: 'is empty', types: ['string', 'array'] },
            { value: 'isNotEmpty', label: 'is not empty', types: ['string', 'array'] },
            { value: 'in', label: 'in list', types: ['string', 'number'] },
            { value: 'notIn', label: 'not in list', types: ['string', 'number'] },
        ];

        return allOperators
            .filter(op => op.types.includes(fieldType))
            .map(op => ({ value: op.value, label: op.label }));
    }

    private buildExpression(): string {
        if (!this.selectedField) return '';

        const operatorMap: Record<ConditionOperator, string> = {
            equals: '==',
            notEquals: '!=',
            greaterThan: '>',
            lessThan: '<',
            greaterThanOrEqual: '>=',
            lessThanOrEqual: '<=',
            contains: '.includes',
            notContains: '!.includes',
            startsWith: '.startsWith',
            endsWith: '.endsWith',
            isEmpty: '== ""',
            isNotEmpty: '!= ""',
            in: 'in',
            notIn: 'not in',
        };

        const operator = operatorMap[this.selectedOperator];
        let value = this.conditionValue;

        // Quote strings
        const fieldType = this.availableFields.find(f => f.name === this.selectedField)?.type;
        if (fieldType === 'string' && !['isEmpty', 'isNotEmpty'].includes(this.selectedOperator)) {
            value = `"${value}"`;
        }

        // Handle special operators
        if (this.selectedOperator === 'contains' || this.selectedOperator === 'notContains') {
            return `${this.selectedField}${operator}(${value})`;
        }

        if (this.selectedOperator === 'startsWith' || this.selectedOperator === 'endsWith') {
            return `${this.selectedField}${operator}(${value})`;
        }

        if (this.selectedOperator === 'isEmpty' || this.selectedOperator === 'isNotEmpty') {
            return `${this.selectedField} ${operator}`;
        }

        return `${this.selectedField} ${operator} ${value}`;
    }

    private generateNaturalLanguage(): string {
        if (!this.selectedField) return 'No condition set';

        const fieldLabel = this.availableFields.find(f => f.name === this.selectedField)?.label || this.selectedField;

        const nlMap: Record<ConditionOperator, string> = {
            equals: 'is',
            notEquals: 'is not',
            greaterThan: 'is greater than',
            lessThan: 'is less than',
            greaterThanOrEqual: 'is at least',
            lessThanOrEqual: 'is at most',
            contains: 'contains',
            notContains: 'does not contain',
            startsWith: 'starts with',
            endsWith: 'ends with',
            isEmpty: 'is empty',
            isNotEmpty: 'has a value',
            in: 'is one of',
            notIn: 'is not one of',
        };

        const operatorText = nlMap[this.selectedOperator];

        if (this.selectedOperator === 'isEmpty' || this.selectedOperator === 'isNotEmpty') {
            return `When ${fieldLabel} ${operatorText}`;
        }

        return `When ${fieldLabel} ${operatorText} ${this.conditionValue}`;
    }

    private handleFieldChange(e: Event) {
        this.selectedField = (e.target as HTMLSelectElement).value;
        this.emitChange();
    }

    private handleOperatorChange(e: Event) {
        this.selectedOperator = (e.target as HTMLSelectElement).value as ConditionOperator;
        this.emitChange();
    }

    private handleValueChange(e: Event) {
        this.conditionValue = (e.target as HTMLInputElement).value;
        this.emitChange();
    }

    private emitChange() {
        const condition: TransitionCondition = {
            expression: this.buildExpression(),
            field: this.selectedField,
            operator: this.selectedOperator,
            value: this.conditionValue,
            naturalLanguage: this.generateNaturalLanguage(),
            fields: [this.selectedField]
        };

        this.dispatchEvent(new CustomEvent('condition-changed', {
            detail: condition,
            bubbles: true,
            composed: true
        }));
    }

    render() {
        const operators = this.getOperators();
        const expression = this.buildExpression();
        const naturalLanguage = this.generateNaturalLanguage();
        const needsValue = !['isEmpty', 'isNotEmpty'].includes(this.selectedOperator);

        return html`
      <div class="condition-builder ${this.compact ? 'compact' : ''}">
        <!-- Field Selection -->
        <div class="builder-row">
          <span class="field-label">Field</span>
          <select
            .value=${this.selectedField}
            @change=${this.handleFieldChange}
          >
            <option value="">Select a field...</option>
            ${this.availableFields.map(field => html`
              <option value=${field.name}>${field.label}</option>
            `)}
          </select>
        </div>

        ${this.selectedField ? html`
          <!-- Operator Selection -->
          <div class="builder-row">
            <span class="field-label">Operator</span>
            <select
              class="operator-select"
              .value=${this.selectedOperator}
              @change=${this.handleOperatorChange}
            >
              ${operators.map(op => html`
                <option value=${op.value}>${op.label}</option>
              `)}
            </select>
          </div>

          <!-- Value Input -->
          ${needsValue ? html`
            <div class="builder-row">
              <span class="field-label">Value</span>
              <input
                type="text"
                .value=${this.conditionValue}
                @input=${this.handleValueChange}
                placeholder="Enter value..."
              />
            </div>
          ` : ''}

          <!-- Natural Language Preview -->
          ${!this.compact && naturalLanguage ? html`
            <div class="natural-language">
              💡 ${naturalLanguage}
            </div>
          ` : ''}

          <!-- Expression Preview -->
          ${!this.compact && expression ? html`
            <div class="expression-preview">
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span style="font-weight: 600; font-size: 0.7rem; color: #64748b;">EXPRESSION:</span>
                <button 
                  class="toggle-btn"
                  @click=${() => this.showNaturalLanguage = !this.showNaturalLanguage}
                >
                  ${this.showNaturalLanguage ? 'Show Code' : 'Show Plain English'}
                </button>
              </div>
              <div style="margin-top: 0.5rem;">
                ${this.showNaturalLanguage ? naturalLanguage : expression}
              </div>
            </div>
          ` : ''}

          <!-- Hint -->
          ${!this.compact ? html`
            <div class="hint">
              💡 This condition will be evaluated when the workflow runs
            </div>
          ` : ''}
        ` : ''}
      </div>
    `;
    }
}
