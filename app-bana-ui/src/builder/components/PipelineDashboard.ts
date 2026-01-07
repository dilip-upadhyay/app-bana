import { LitElement, html, css } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { encodeRuntimeState } from '../../models/runtime-state';
import { apiClient } from '../../core/api-client';
import { getApiUrl } from '../../core/api-config';
import { AuthService } from '../../pages/auth/auth-service';

interface PipelineStatus {
  [env: string]: {
    versionId: string;
    versionNumber: number;
    label: string;
    deployedAt: number;
    deployedBy: string;
  }
}

@customElement('pipeline-dashboard')
export class PipelineDashboard extends LitElement {
  static styles = css`
    :host {
      display: block;
      padding: 24px;
      font-family: 'Inter', sans-serif;
    }
    
    .pipeline-container {
      display: flex;
      align-items: flex-start;
      gap: 32px;
      overflow-x: auto;
      padding-bottom: 20px;
    }
    
    .env-stage {
      flex: 1;
      min-width: 250px;
      max-width: 300px;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
      display: flex;
      flex-direction: column;
    }

    .env-header {
      padding: 16px;
      background: #f8fafc;
      border-bottom: 1px solid #e2e8f0;
      border-radius: 12px 12px 0 0;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    
    .env-name {
      font-weight: 600;
      color: #1e293b;
      font-size: 1.1rem;
    }
    
    .env-badge {
      padding: 4px 8px;
      border-radius: 999px;
      font-size: 0.75rem;
      font-weight: 500;
      text-transform: uppercase;
    }
    
    .env-badge.dev { background: #dbeafe; color: #1e40af; }
    .env-badge.sit { background: #fce7f3; color: #9d174d; }
    .env-badge.prod { background: #dcfce7; color: #166534; }

    .env-body {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 16px;
      min-height: 140px;
      justify-content: center;
      align-items: center;
    }
    
    .empty-state {
      color: #94a3b8;
      font-size: 0.875rem;
      text-align: center;
    }
    
    .current-version {
      text-align: center;
    }
    
    .version-tag {
      font-size: 1.5rem;
      font-weight: 700;
      color: #0f172a;
      display: block;
      margin-bottom: 8px;
    }
    
    .version-meta {
      font-size: 0.875rem;
      color: #64748b;
    }
    
    .deploy-info {
      font-size: 0.75rem;
      color: #94a3b8;
      margin-top: 4px;
    }
    
    .arrow-connector {
      display: flex;
      align-items: center;
      justify-content: center;
      padding-top: 60px;
      color: #cbd5e1;
      font-size: 2rem;
    }

    .action-row {
      display: flex;
      gap: 8px;
      width: 100%;
      margin-top: 12px;
    }

    .promote-btn {
      flex: 1;
      padding: 8px;
      background: #3b82f6;
      color: white;
      border: none;
      border-radius: 6px;
      font-weight: 500;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 4px;
      transition: all 0.2s;
    }
    
    .promote-btn:hover {
      background: #2563eb;
    }
    
     .launch-btn {
      flex: 1;
      padding: 8px;
      background: #10b981;
      color: white;
      border: none;
      border-radius: 6px;
      font-weight: 500;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 4px;
      text-decoration: none;
    }
    
    .launch-btn:hover {
      background: #059669;
    }
    
    .promote-btn:disabled {
      background: #94a3b8;
      cursor: not-allowed;
    }
  `;

  @property({ type: String }) appId = '';

  @state() private status: PipelineStatus = {};
  @state() private isLoading = false;

  connectedCallback() {
    super.connectedCallback();
    this.fetchStatus();
  }

  async fetchStatus() {
    if (!this.appId) return;
    this.isLoading = true;
    try {
      const tenantId = AuthService.getUser()?.tenantId || 'default';
      this.status = await apiClient.get(getApiUrl(`/api/${tenantId}/apps/${this.appId}/pipeline`));
    } catch (e) {
      console.error(e);
    } finally {
      this.isLoading = false;
    }
  }

  private async promote(versionId: string, targetEnv: string) {
    if (!confirm(`Are you sure you want to promote version to ${targetEnv}?`)) return;

    try {
      const tenantId = AuthService.getUser()?.tenantId || 'default';
      await apiClient.post(getApiUrl(`/api/${tenantId}/apps/${this.appId}/deploy/${versionId}`), {
        environment: targetEnv
      });
      await this.fetchStatus();
    } catch (e) {
      alert('Error promoting version');
    }
  }

  private launch(env: string) {
    // Get tenant ID from logged-in user
    const user = AuthService.getUser();
    const tenantId = user?.tenantId || (user as any)?.tenant_id || 'default';

    // New Path-Based Routing: /run/:tenantId/:appId
    // The env parameter can be added as a query param if needed for deployed versions
    const url = env === 'DEV'
      ? `/run/${tenantId}/${this.appId}`
      : `/run/${tenantId}/${this.appId}?env=${env}`;

    window.open(url, '_blank');
  }

  render() {
    const dev = this.status['DEV'];
    const sit = this.status['SIT'];
    const prod = this.status['PROD'];

    // Determine promotion flows
    const canPromoteToSit = dev && (!sit || sit.versionNumber < dev.versionNumber);
    const canPromoteToProd = sit && (!prod || prod.versionNumber < sit.versionNumber);

    return html`
      <div class="pipeline-container">
        
        <!-- DEV -->
        <div class="env-stage">
          <div class="env-header">
            <span class="env-name">Development</span>
            <span class="env-badge dev">DEV</span>
          </div>
          <div class="env-body">
            ${dev ? this.renderVersion(dev, 'DEV') : html`<div class="empty-state">No active deployment</div>`}
          </div>
        </div>

        <div class="arrow-connector">→</div>

        <!-- SIT -->
        <div class="env-stage">
          <div class="env-header">
            <span class="env-name">SIT / QA</span>
            <span class="env-badge sit">SIT</span>
          </div>
          <div class="env-body">
            ${sit ? this.renderVersion(sit, 'SIT') : html`<div class="empty-state">Not deployed</div>`}
            
            ${canPromoteToSit ? html`
              <button class="promote-btn" style="width:100%; margin-top:8px" @click=${() => this.promote(dev!.versionId, 'SIT')}>
                🚀 Promote v${dev!.versionNumber}
              </button>
            ` : ''}
          </div>
        </div>

        <div class="arrow-connector">→</div>

        <!-- PROD -->
        <div class="env-stage">
          <div class="env-header">
            <span class="env-name">Production</span>
            <span class="env-badge prod">PROD</span>
          </div>
          <div class="env-body">
            ${prod ? this.renderVersion(prod, 'PROD') : html`<div class="empty-state">Not deployed</div>`}

            ${canPromoteToProd ? html`
              <button class="promote-btn" style="width:100%; margin-top:8px" @click=${() => this.promote(sit!.versionId, 'PROD')}>
                🚀 Promote v${sit!.versionNumber}
              </button>
            ` : ''}
          </div>
        </div>

      </div>
    `;
  }

  private renderVersion(v: PipelineStatus[string], env: string) {
    return html`
      <div class="current-version">
        <span class="version-tag">${v.label}</span>
        <div class="version-meta">Version #${v.versionNumber}</div>
        <div class="deploy-info">
          Deployed by ${v.deployedBy || 'Unknown'}<br>
          ${new Date(v.deployedAt).toLocaleDateString()}
        </div>
      </div>
      <div class="action-row">
         <button class="launch-btn" @click=${() => this.launch(env)}>
          🔗 Open App
        </button>
      </div>
    `;
  }
}
