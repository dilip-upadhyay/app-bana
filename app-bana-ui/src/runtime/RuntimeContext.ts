/**
 * RuntimeContext - Provides tenant, app, and environment context to runtime components
 *
 * This singleton service maintains the current runtime execution context
 * and provides it to all components that need to make API calls.
 * 
 * Usage:
 * ```typescript
 * // In AppRuntimeShell (initialization)
 * RuntimeContext.getInstance().setContext('tenant123', 'app456', 'prod');
 * 
 * // In any runtime component (FormContainer, etc.)
 * const { tenantId, appId, env } = RuntimeContext.getInstance().getContext();
 * await apiClient.post(`/appbana-studio/${tenantId}/apps/${appId}/${entity}`, data);
 * ```
 * 
 * @see AppRuntimeShell for initialization
 * @see FormContainer for usage example
 */

export class RuntimeContext {
  private static instance: RuntimeContext;

  private tenantId: string = 'default';
  private appId: string | null = null;
  private env: string = 'dev';
  private initialized: boolean = false;

  /**
   * Private constructor for singleton pattern
   */
  private constructor() { }

  /**
   * Get singleton instance
   */
  static getInstance(): RuntimeContext {
    if (!RuntimeContext.instance) {
      RuntimeContext.instance = new RuntimeContext();
    }
    return RuntimeContext.instance;
  }

  /**
   * Set runtime context (called once by AppRuntimeShell on app load)
   * 
   * @param tenantId - Tenant identifier (e.g., 'default', 'tenant123')
   * @param appId - Application identifier (e.g., 'my-app', 'app-456')
   * @param env - Environment (e.g., 'dev', 'staging', 'prod')
   */
  setContext(tenantId: string, appId: string, env: string = 'dev'): void {
    if (!tenantId || tenantId.trim() === '') {
      throw new Error('RuntimeContext: tenantId is required');
    }
    if (!appId || appId.trim() === '') {
      throw new Error('RuntimeContext: appId is required');
    }

    this.tenantId = tenantId.trim();
    this.appId = appId.trim();
    this.env = env.trim() || 'dev';
    this.initialized = true;

    console.log(`[RuntimeContext] Context set: tenant=${this.tenantId}, app=${this.appId}, env=${this.env}`);
  }

  /**
   * Get current runtime context
   * 
   * @returns Context object with tenantId, appId, env
   * @throws Error if context not initialized
   */
  getContext(): { tenantId: string; appId: string; env: string } {
    if (!this.initialized || !this.appId) {
      throw new Error(
        'RuntimeContext not initialized. Call setContext() first (usually done by AppRuntimeShell).'
      );
    }

    return {
      tenantId: this.tenantId,
      appId: this.appId,
      env: this.env
    };
  }

  /**
   * Get context with fallback for development/testing
   * Use this when context might not be available (e.g., standalone components)
   * 
   * @returns Context object, or default values if not initialized
   */
  getContextSafe(): { tenantId: string; appId: string; env: string } {
    if (this.context) return this.context;

    // Try to parse from URL if not initialized (e.g. /run/:tenant/:app)
    const path = window.location.pathname;
    if (path.startsWith('/run/')) {
      const parts = path.split('/');
      if (parts.length >= 4) {
        return {
          tenantId: parts[2],
          appId: parts[3],
          env: 'dev'
        };
      }
    }

    // Try to parse from query param 'state' (Legacy)
    const params = new URLSearchParams(window.location.search);
    const stateParam = params.get('state');
    if (stateParam) {
      try {
        const state = JSON.parse(atob(stateParam));
        return {
          tenantId: state.tenantId || 'default',
          appId: state.appId || '',
          env: state.env || 'dev'
        };
      } catch (e) {
        // invalid state
      }
    }

    // Fallback: Check if we have global runtime state injected
    const shell = document.querySelector('app-runtime-shell');
    if (shell && (shell as any).runtimeState) {
      const rs = (shell as any).runtimeState;
      return {
        tenantId: rs.tenantId || 'default',
        appId: rs.app?.id || rs.appId || '',
        env: 'dev'
      };
    }

    // NO AUTH SERVICE FALLBACK.
    // Return default values for safety but log warning.
    console.warn('[RuntimeContext] Context not initialized and URL parsing failed. Using strict defaults.');
    return { tenantId: 'default', appId: '', env: 'dev' };
  }

  /**
   * Getter for internal context logic helper
   */
  private get context(): { tenantId: string; appId: string; env: string } | null {
    if (this.initialized && this.appId) {
      return {
        tenantId: this.tenantId,
        appId: this.appId,
        env: this.env
      };
    }
    return null;
  }

  /**
   * Check if context is initialized
   */
  isInitialized(): boolean {
    return this.initialized && this.appId !== null;
  }

  /**
   * Clear context (mainly for testing)
   */
  clear(): void {
    this.tenantId = 'default';
    this.appId = null;
    this.env = 'dev';
    this.initialized = false;
  }

  /**
   * Get tenant ID only
   */
  getTenantId(): string {
    return this.getContext().tenantId;
  }

  /**
   * Get app ID only
   */
  getAppId(): string {
    return this.getContext().appId;
  }

  /**
   * Get environment only
   */
  getEnv(): string {
    return this.getContext().env;
  }
}

/**
 * Convenience function to get runtime context
 * Equivalent to RuntimeContext.getInstance().getContext()
 */
export function getRuntimeContext() {
  return RuntimeContext.getInstance().getContext();
}

/**
 * Convenience function to get runtime context with fallback
 * Equivalent to RuntimeContext.getInstance().getContextSafe()
 */
export function getRuntimeContextSafe() {
  return RuntimeContext.getInstance().getContextSafe();
}
