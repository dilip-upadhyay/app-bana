import { AuthService } from '../../pages/auth/auth-service';

/**
 * Service for auto-deploying apps to LOCAL environment on Studio save
 */
class AutoDeployService {
    private deployTimer: number | null = null;
    private readonly DEBOUNCE_MS = 2000;

    /**
     * Schedule a LOCAL deployment after debounce delay.
     * Multiple rapid calls will be debounced to a single deploy.
     */
    scheduleDeployToLocal(appId: string) {
        if (this.deployTimer) {
            clearTimeout(this.deployTimer);
        }

        console.log('[AutoDeploy] Scheduling deploy to LOCAL in 2s...');
        this.deployTimer = setTimeout(() => {
            this.deployToLocal(appId);
        }, this.DEBOUNCE_MS);
    }

    /**
     * Deploy current app to LOCAL environment with sample data
     */
    private async deployToLocal(appId: string) {
        const user = AuthService.getUser();
        const tenantId = user?.tenantId || 'default';

        try {
            console.log('[AutoDeploy] Deploying to LOCAL:', appId);

            const response = await fetch(
                `/api/${tenantId}/apps/${appId}/deploy/local`,
                {
                    method: 'PUT',
                    headers: {
                        'X-Session-Token': user?.token || '',
                        'Content-Type': 'application/json'
                    }
                }
            );

            if (!response.ok) {
                const error = await response.text();
                console.error('[AutoDeploy] ❌ Failed:', error);
                this.showToast('❌ LOCAL DEPLOY FAILED', '#ef4444');
                return;
            }

            const result = await response.json();
            console.log('[AutoDeploy] ✅ Deployed to LOCAL:', result);
            this.showToast('🚀 AUTO-DEPLOYED TO LOCAL', '#10b981');

        } catch (e) {
            console.error('[AutoDeploy] ❌ Exception:', e);
            this.showToast('❌ LOCAL DEPLOY FAILED', '#ef4444');
        }
    }

    /**
     * Show toast notification
     */
    private showToast(message: string, backgroundColor: string) {
        const toast = document.createElement('div');
        toast.style.cssText = `
      position: fixed; 
      bottom: 24px; 
      right: 24px;
      padding: 12px 20px; 
      background: ${backgroundColor}; 
      color: white;
      border-radius: 8px; 
      font-size: 14px; 
      font-weight: 500;
      z-index: 10000;
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
      animation: slideIn 0.3s ease-out;
    `;
        toast.textContent = message;
        document.body.appendChild(toast);

        // Remove after 2.5 seconds
        setTimeout(() => {
            toast.style.animation = 'slideOut 0.3s ease-in';
            setTimeout(() => document.body.removeChild(toast), 300);
        }, 2500);
    }
}

// Export singleton instance
export const autoDeployService = new AutoDeployService();
