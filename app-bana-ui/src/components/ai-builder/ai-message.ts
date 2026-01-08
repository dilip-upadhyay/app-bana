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
  `;

    @property() role: 'user' | 'assistant' = 'user';
    @property() content = '';
    @property() timestamp?: Date;

    render() {
        return html`
      <div class="message ${this.role}">
        <div class="avatar ${this.role}">
          ${this.role === 'user' ? '👤' : '🤖'}
        </div>
        <div>
          <div class="content ${this.role}">
            ${this.content}
          </div>
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
