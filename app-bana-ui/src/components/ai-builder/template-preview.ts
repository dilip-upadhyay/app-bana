import { LitElement, html, css } from 'lit';
import { customElement, property } from 'lit/decorators.js';
import { AppPattern } from '../../services/ai-chat-service.ts';

@customElement('template-preview')
export class TemplatePreview extends LitElement {
    static styles = css`
    :host {
      display: block;
    }

    .card {
      border: 1px solid #e0e0e0;
      border-radius: 12px;
      padding: 16px;
      background: white;
      transition: all 0.2s;
      cursor: pointer;
    }

    .card:hover {
      box-shadow: 0 4px 12px rgba(0,0,0,0.1);
      transform: translateY(-2px);
      border-color: #667eea;
    }

    .header {
      display: flex;
      justify-content: space-between;
      align-items: start;
      margin-bottom: 12px;
    }

    .title {
      font-weight: 600;
      font-size: 16px;
      color: #333;
      margin: 0 0 4px 0;
    }

    .type {
      font-size: 12px;
      color: #999;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .badge {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 11px;
      font-weight: 600;
    }

    .stats {
      display: flex;
      gap: 16px;
      margin-bottom: 12px;
      font-size: 13px;
      color: #666;
    }

    .stat {
      display: flex;
      align-items: center;
      gap: 4px;
    }

    .section {
      margin-top: 12px;
      padding-top: 12px;
      border-top: 1px solid #f0f0f0;
    }

    .section-title {
      font-size: 12px;
      font-weight: 600;
      color: #666;
      margin-bottom: 8px;
    }

    .items {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }

    .item {
      background: #f5f5f5;
      padding: 4px 10px;
      border-radius: 6px;
      font-size: 12px;
      color: #555;
    }

    button {
      width: 100%;
      margin-top: 12px;
      padding: 10px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border: none;
      border-radius: 8px;
      font-weight: 600;
      cursor: pointer;
      transition: transform 0.2s;
    }

    button:hover {
      transform: translateY(-2px);
    }
  `;

    @property({ type: Object }) pattern?: AppPattern;

    render() {
        if (!this.pattern) return html``;

        const successPercent = Math.round(this.pattern.successRate * 100);

        return html`
      <div class="card" @click=${this.handleUseTemplate}>
        <div class="header">
          <div>
            <h3 class="title">${this.pattern.patternName}</h3>
            <div class="type">${this.pattern.appType}</div>
          </div>
          <div class="badge">${successPercent}% success</div>
        </div>

        <div class="stats">
          <div class="stat">
            <span>📊</span>
            <span>${this.pattern.entities.length} entities</span>
          </div>
          <div class="stat">
            <span>📄</span>
            <span>${this.pattern.pages.length} pages</span>
          </div>
          <div class="stat">
            <span>🔄</span>
            <span>${this.pattern.usageCount} uses</span>
          </div>
        </div>

        ${this.pattern.entities.length > 0 ? html`
          <div class="section">
            <div class="section-title">Entities</div>
            <div class="items">
              ${this.pattern.entities.slice(0, 5).map((entity: any) => html`
                <div class="item">${entity.name || entity}</div>
              `)}
              ${this.pattern.entities.length > 5 ? html`
                <div class="item">+${this.pattern.entities.length - 5} more</div>
              ` : ''}
            </div>
          </div>
        ` : ''}

        ${this.pattern.pages.length > 0 ? html`
          <div class="section">
            <div class="section-title">Pages</div>
            <div class="items">
              ${this.pattern.pages.slice(0, 5).map((page: any) => html`
                <div class="item">${page.name || page}</div>
              `)}
              ${this.pattern.pages.length > 5 ? html`
                <div class="item">+${this.pattern.pages.length - 5} more</div>
              ` : ''}
            </div>
          </div>
        ` : ''}

        <button @click=${this.handleUseTemplate}>
          Use This Template
        </button>
      </div>
    `;
    }

    private handleUseTemplate(e: Event) {
        e.stopPropagation();
        this.dispatchEvent(new CustomEvent('use-template', {
            detail: { pattern: this.pattern },
            bubbles: true,
            composed: true
        }));
    }
}
