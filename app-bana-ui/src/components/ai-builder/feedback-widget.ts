import { LitElement, html, css } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { AiChatService } from '../../services/ai-chat-service.ts';

@customElement('feedback-widget')
export class FeedbackWidget extends LitElement {
    static styles = css`
    :host {
      display: inline-flex;
      gap: 8px;
      align-items: center;
    }

    button {
      background: none;
      border: 1px solid #e0e0e0;
      border-radius: 50%;
      width: 32px;
      height: 32px;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: all 0.2s;
      font-size: 16px;
    }

    button:hover:not(:disabled) {
      background: #f5f5f5;
      transform: scale(1.1);
    }

    button:disabled {
      opacity: 0.3;
      cursor: not-allowed;
    }

    button.active {
      background: #667eea;
      color: white;
      border-color: #667eea;
    }

    button.active.positive {
      background: #10b981;
      border-color: #10b981;
    }

    button.active.negative {
      background: #ef4444;
      border-color: #ef4444;
    }

    .feedback-text {
      font-size: 11px;
      color: #999;
      margin-left: 4px;
    }
  `;

    @property() conversationId = '';
    @property() userId = 'demo-user';

    @state() private rating: number | null = null;
    @state() private isSubmitting = false;

    private chatService = new AiChatService();

    render() {
        return html`
      <button
        class="${this.rating === 1 ? 'active positive' : ''}"
        @click=${() => this.submitFeedback(1)}
        ?disabled=${this.isSubmitting}
        title="Helpful">
        👍
      </button>
      <button
        class="${this.rating === -1 ? 'active negative' : ''}"
        @click=${() => this.submitFeedback(-1)}
        ?disabled=${this.isSubmitting}
        title="Not helpful">
        👎
      </button>
      ${this.rating !== null ? html`
        <span class="feedback-text">Thanks for your feedback!</span>
      ` : ''}
    `;
    }

    private async submitFeedback(rating: number) {
        if (this.isSubmitting || !this.conversationId) return;

        this.rating = rating;
        this.isSubmitting = true;

        try {
            await this.chatService.submitFeedback(
                this.conversationId,
                this.userId,
                rating
            );

            // Dispatch event for parent components
            this.dispatchEvent(new CustomEvent('feedback-submitted', {
                detail: { rating, conversationId: this.conversationId },
                bubbles: true,
                composed: true
            }));
        } catch (error) {
            console.error('Failed to submit feedback:', error);
            this.rating = null; // Reset on error
        } finally {
            this.isSubmitting = false;
        }
    }
}
