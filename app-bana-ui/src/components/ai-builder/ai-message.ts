import { LitElement, html, css } from 'lit';
import { customElement, property } from 'lit/decorators.js';

@customElement('ai-message')
export class AiMessage extends LitElement {
  static styles = css`
    :host {
      display: block;
      margin: 12px 0;
    }

    .message {
      display: flex;
      gap: 12px;
      align-items: flex-start;
    }

    .message.user {
      flex-direction: row-reverse;
    }

    .avatar {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 16px;
      flex-shrink: 0;
    }

    .avatar.user {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
    }

    .avatar.assistant {
      background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
      color: white;
    }

    .content {
      max-width: 70%;
      padding: 12px 16px;
      border-radius: 12px;
      line-height: 1.5;
    }

    .content.user {
      background: #667eea;
      color: white;
      border-bottom-right-radius: 4px;
    }

    .content.assistant {
      background: #f5f5f5;
      color: #333;
      border-bottom-left-radius: 4px;
    }

    .timestamp {
      font-size: 11px;
      color: #999;
      margin-top: 4px;
    }

    .action-buttons {
      display: flex;
      gap: 8px;
      margin-top: 12px;
      flex-wrap: wrap;
    }

    .action-button {
      padding: 8px 16px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border: none;
      border-radius: 20px;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      transition: transform 0.2s, box-shadow 0.2s;
    }

    .action-button:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
    }

    .action-button.secondary {
      background: white;
      color: #667eea;
      border: 1px solid #667eea;
    }

    .action-button.secondary:hover {
      background: #f5f7ff;
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
    }
  `;

  @property() role: 'user' | 'assistant' = 'user';
  @property() content = '';
  @property() timestamp?: Date;

  private parseActions(content: string): { text: string; actions: string[] } {
    // Look for [ACTIONS: action1 | action2 | action3] pattern
    const actionsMatch = content.match(/\[ACTIONS:\s*([^\]]+)\]/);
    if (!actionsMatch) {
      return { text: content, actions: [] };
    }

    // Extract actions and clean up the text
    const actionsText = actionsMatch[1];
    const actions = actionsText.split('|').map(a => a.trim()).filter(a => a);
    const cleanText = content.replace(actionsMatch[0], '').trim();

    return { text: cleanText, actions };
  }

  private handleActionClick(action: string) {
    // Dispatch event that parent can listen to
    this.dispatchEvent(new CustomEvent('action-click', {
      detail: { action },
      bubbles: true,
      composed: true
    }));
  }

  render() {
    const { text, actions } = this.parseActions(this.content);

    return html`
      <div class="message ${this.role}">
        <div class="avatar ${this.role}">
          ${this.role === 'user' ? '👤' : '🤖'}
        </div>
        <div>
          <div class="content ${this.role}">
            ${text}
          </div>
          ${actions.length > 0 && this.role === 'assistant' ? html`
            <div class="action-buttons">
              ${actions.map((action, index) => html`
                <button 
                  class="action-button ${index > 0 ? 'secondary' : ''}"
                  @click=${() => this.handleActionClick(action)}
                >
                  ${action}
                </button>
              `)}
            </div>
          ` : ''}
          ${this.timestamp ? html`
            <div class="timestamp">
              ${this.formatTime(this.timestamp)}
            </div>
          ` : ''}
        </div>
      </div>
    `;
  }

  private formatTime(date: Date): string {
    return date.toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit'
    });
  }
}
