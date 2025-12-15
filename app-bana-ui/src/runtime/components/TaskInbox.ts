import { LitElement, html, css } from 'lit';
import { customElement, state, property } from 'lit/decorators.js';
import { apiClient } from '../../core/api-client';

@customElement('task-inbox')
export class TaskInbox extends LitElement {
    static styles = css`
    :host {
      display: block;
      padding: 24px;
      height: 100%;
      background: var(--color-bg, #f8fafc);
    }

    .inbox-container {
      max-width: 1000px;
      margin: 0 auto;
      background: var(--color-surface, white);
      border-radius: 12px;
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
      overflow: hidden;
      display: flex;
      flex-direction: column;
      height: 100%;
      max-height: 80vh;
    }

    .header {
      padding: 20px 24px;
      border-bottom: 1px solid var(--color-border, #e2e8f0);
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: var(--tbl-header-bg, #f8fafc);
    }

    .header h2 {
      margin: 0;
      font-size: 1.25rem;
      color: var(--color-text, #1e293b);
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .badge {
      background: var(--color-primary, #3b82f6);
      color: white;
      font-size: 0.75rem;
      padding: 2px 8px;
      border-radius: 12px;
    }

    .task-list {
      flex: 1;
      overflow-y: auto;
    }

    .task-item {
      padding: 16px 24px;
      border-bottom: 1px solid var(--color-border, #e2e8f0);
      display: grid;
      grid-template-columns: 1fr auto;
      gap: 16px;
      transition: background-color 0.2s;
    }

    .task-item:hover {
      background-color: var(--color-hover, #f1f5f9);
    }

    .task-info h3 {
      margin: 0 0 4px 0;
      font-size: 1rem;
      color: var(--color-text, #1e293b);
    }

    .task-meta {
      font-size: 0.875rem;
      color: var(--color-text-secondary, #64748b);
      display: flex;
      gap: 12px;
    }

    .actions {
      display: flex;
      gap: 8px;
      align-items: center;
    }

    button {
      padding: 8px 16px;
      border-radius: 6px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s;
      border: 1px solid transparent;
    }

    .btn-approve {
      background-color: #dcfce7;
      color: #166534;
    }
    .btn-approve:hover {
      background-color: #bbf7d0;
    }

    .btn-reject {
      background-color: #fee2e2;
      color: #991b1b;
    }
    .btn-reject:hover {
      background-color: #fecaca;
    }

    .btn-view {
      background-color: transparent;
      border-color: #cbd5e1;
      color: #475569;
    }
    .btn-view:hover {
      background-color: #f1f5f9;
    }

    .empty-state {
      padding: 48px;
      text-align: center;
      color: var(--color-text-secondary, #64748b);
    }

    .loading {
      padding: 40px;
      text-align: center;
      color: var(--color-text-secondary, #64748b);
    }

    .details-modal {
      position: fixed;
      inset: 0;
      background: rgba(0,0,0,0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 50;
    }

    .modal-content {
      background: white;
      padding: 24px;
      border-radius: 12px;
      width: 500px;
      max-width: 90%;
      max-height: 90vh;
      overflow-y: auto;
    }
    
    .data-grid {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 8px 16px;
      margin: 16px 0;
    }
    
    .label {
      font-weight: 600;
      color: #64748b;
    }
  `;

    @state()
    private tasks: any[] = [];

    @state()
    private loading = true;

    @state()
    private selectedTask: any | null = null;

    @state()
    private processingId: string | null = null;

    async connectedCallback() {
        super.connectedCallback();
        this.fetchTasks();
    }

    async fetchTasks() {
        this.loading = true;
        try {
            // In a real app, userId would be dynamic. 
            // For this demo, we assume the backend filters correctly or we pass a test user.
            const userId = 'system';
            const tasks = await apiClient.get<any[]>(`/workflow/my-tasks?userId=${userId}`);
            this.tasks = tasks || [];
        } catch (e) {
            console.error('Failed to fetch tasks', e);
        } finally {
            this.loading = false;
        }
    }

    async completeTask(task: any, outcome: string) {
        this.processingId = task.tokenId;
        try {
            await apiClient.post(`/workflow/my-tasks/${task.tokenId}/complete`, {
                outcome,
                taskData: {} // Could capture form data here if we had a form
            });
            // Remove from list locally for instant feedback
            this.tasks = this.tasks.filter(t => t.tokenId !== task.tokenId);
            this.selectedTask = null;

            this.dispatchEvent(new CustomEvent('task-completed', {
                detail: { outcome, task },
                bubbles: true
            }));

        } catch (e) {
            console.error('Failed to complete task', e);
            alert('Failed to complete task');
        } finally {
            this.processingId = null;
        }
    }

    render() {
        return html`
      <div class="inbox-container">
        <div class="header">
          <h2>
            <span>📥 My Inbox</span>
            ${this.tasks.length > 0 ? html`<span class="badge">${this.tasks.length}</span>` : ''}
          </h2>
          <button class="btn-view" @click=${this.fetchTasks} title="Refresh">↻</button>
        </div>

        ${this.loading ? html`
          <div class="loading">Loading tasks...</div>
        ` : this.tasks.length === 0 ? html`
          <div class="empty-state">
            <h3>All caught up! 🎉</h3>
            <p>You have no pending tasks.</p>
          </div>
        ` : html`
          <div class="task-list">
            ${this.tasks.map(task => this.renderTaskItem(task))}
          </div>
        `}
      </div>

      ${this.renderDetailsModal()}
    `;
    }

    renderTaskItem(task: any) {
        const isProcessing = this.processingId === task.tokenId;
        const taskName = task.workflowName || 'Workflow Task';

        // Attempt to find a meaningful title from context data
        let contextTitle = '';
        try {
            if (task.contextData) {
                const ctx = JSON.parse(task.contextData);
                // Look for common name fields
                contextTitle = ctx.name || ctx.title || ctx.subject || ctx.email || `Item #${task.entityId}`;
            }
        } catch (e) { contextTitle = `Item #${task.entityId}`; }

        return html`
      <div class="task-item">
        <div class="task-info">
          <h3>${contextTitle}</h3>
          <div class="task-meta">
            <span>📌 ${taskName}</span>
            <span>⏱️ Due: ${task.dueAt ? new Date(task.dueAt).toLocaleDateString() : 'No Deadline'}</span>
          </div>
        </div>
        <div class="actions">
          <button class="btn-view" 
            @click=${() => this.selectedTask = task} 
            ?disabled=${isProcessing}>
            Review
          </button>
          <button class="btn-approve" 
            @click=${() => this.completeTask(task, 'APPROVE')}
            ?disabled=${isProcessing}>
            ${isProcessing ? '...' : 'Approve'}
          </button>
          <button class="btn-reject" 
            @click=${() => this.completeTask(task, 'REJECT')}
            ?disabled=${isProcessing}>
            Reject
          </button>
        </div>
      </div>
    `;
    }

    renderDetailsModal() {
        if (!this.selectedTask) return '';
        const task = this.selectedTask;
        let contextData = {};
        try { contextData = JSON.parse(task.contextData || '{}'); } catch (e) { }

        return html`
      <div class="details-modal" @click=${() => this.selectedTask = null}>
        <div class="modal-content" @click=${(e: Event) => e.stopPropagation()}>
          <h2 style="margin-top:0">Review Task</h2>
          
          <div class="data-grid">
            <span class="label">Workflow:</span> <span>${task.workflowName}</span>
            <span class="label">Assigned To:</span> <span>${task.assignedUserId || task.assignedRole || 'Anyone'}</span>
            <span class="label">Received:</span> <span>${new Date(task.arrivedAt).toLocaleString()}</span>
          </div>

          <h3>Application Data</h3>
          <div class="data-grid">
            ${Object.entries(contextData).map(([key, value]) => html`
              <span class="label" style="text-transform:capitalize">${key.replace(/([A-Z])/g, ' $1').trim()}:</span>
              <span>${String(value)}</span>
            `)}
          </div>

          <div style="margin-top: 24px; display: flex; gap: 12px; justify-content: flex-end;">
            <button class="btn-reject" @click=${() => this.completeTask(task, 'REJECT')}>Reject</button>
            <button class="btn-approve" @click=${() => this.completeTask(task, 'APPROVE')}>Approve</button>
          </div>
        </div>
      </div>
    `;
    }
}
