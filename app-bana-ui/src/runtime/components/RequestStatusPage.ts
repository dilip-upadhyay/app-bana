import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { apiClient } from '../../core/api-client';

@customElement('request-status-page')
export class RequestStatusPage extends LitElement {
    static styles = css`
    :host {
      display: block;
      height: 100%;
      padding: 24px;
      box-sizing: border-box;
      background: var(--color-bg, #f8fafc);
    }

    .container {
      max-width: 1000px;
      margin: 0 auto;
      background: white;
      border-radius: 12px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1);
      padding: 24px;
      height: 100%;
      display: flex;
      flex-direction: column;
    }

    h2 {
      margin-top: 0;
      display: flex;
      align-items: center;
      gap: 12px;
      color: #1e293b;
    }

    .toolbar {
      display: flex;
      justify-content: flex-end;
      margin-bottom: 20px;
    }

    .grid {
      display: grid;
      grid-template-columns: 2fr 1fr 1fr 1fr;
      gap: 16px;
      padding: 12px 16px;
      border-bottom: 1px solid #e2e8f0;
      align-items: center;
    }

    .grid.header {
      font-weight: 600;
      background: #f8fafc;
      border-radius: 6px;
      color: #64748b;
      font-size: 0.875rem;
    }

    .grid.row {
      font-size: 0.95rem;
      color: #334155;
    }
    .grid.row:hover {
      background: #f8fafc;
    }

    .status-badge {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 9999px;
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
    }
    .status-RUNNING { background: #dbeafe; color: #1e40af; }
    .status-COMPLETED { background: #dcfce7; color: #166534; }
    .status-FAILED { background: #fee2e2; color: #991b1b; }

    .empty-state {
      text-align: center;
      padding: 48px;
      color: #94a3b8;
    }
  `;

    @state()
    private requests: any[] = [];

    @state()
    private loading = true;

    async connectedCallback() {
        super.connectedCallback();
        this.fetchRequests();
    }

    async fetchRequests() {
        this.loading = true;
        try {
            const userId = 'system';
            const data = await apiClient.get<any[]>(`/api/my-requests?userId=${userId}`);
            this.requests = data || [];
        } catch (e) {
            console.error('Failed to fetch requests', e);
        } finally {
            this.loading = false;
        }
    }

    render() {
        return html`
      <div class="container">
        <h2>
            <span>📤 My Requests</span>
        </h2>
        
        <div class="toolbar">
            <button @click=${this.fetchRequests} style="padding: 8px 16px; cursor: pointer;">Refresh</button>
        </div>

        <div class="grid header">
          <div>Application</div>
          <div>Submitted On</div>
          <div>Status</div>
          <div>Details</div>
        </div>

        ${this.loading ? html`<div style="padding:20px; text-align:center">Loading...</div>` : ''}

        ${!this.loading && this.requests.length === 0 ? html`
          <div class="empty-state">No requests found.</div>
        ` : ''}

        <div style="overflow-y:auto; flex:1">
          ${this.requests.map(req => html`
            <div class="grid row">
              <div style="font-weight:500">${req.workflowName || 'Workflow Request'}</div>
              <div>${new Date(req.startedAt).toLocaleDateString()}</div>
              <div>
                <span class="status-badge status-${req.status}">${req.status}</span>
              </div>
              <div style="font-size:0.85rem; color:#64748b">
                 #${req.instanceId.substring(0, 8)}
              </div>
            </div>
          `)}
        </div>
      </div>
    `;
    }
}
