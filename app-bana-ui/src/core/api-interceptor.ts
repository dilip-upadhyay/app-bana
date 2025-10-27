/**
 * API Interceptor System for AppBana
 * Provides request/response/error interceptors for all API calls
 */

export interface RequestInterceptor {
  /**
   * Called before each request is sent
   * Can modify request config or abort request
   * @returns Modified config or null to abort request
   */
  onRequest?: (config: RequestConfig) => RequestConfig | Promise<RequestConfig> | null;
}

export interface ResponseInterceptor {
  /**
   * Called after successful response
   * Can transform response data
   */
  onResponse?: (response: Response, data: any) => any | Promise<any>;
}

export interface ErrorInterceptor {
  /**
   * Called when request fails
   * Can handle or transform errors
   */
  onError?: (error: ApiError) => void | Promise<void>;
}

export interface Interceptor extends RequestInterceptor, ResponseInterceptor, ErrorInterceptor {
  name?: string;
}

export interface RequestConfig extends RequestInit {
  url?: string;
  params?: Record<string, any>;
  skipInterceptors?: boolean;
}

export interface ApiError extends Error {
  status?: number;
  statusText?: string;
  response?: Response;
  data?: any;
  config?: RequestConfig;
}

/**
 * Interceptor Manager
 * Manages the chain of interceptors for API calls
 */
export class InterceptorManager {
  private interceptors: Interceptor[] = [];

  /**
   * Add an interceptor to the chain
   */
  use(interceptor: Interceptor): () => void {
    this.interceptors.push(interceptor);

    // Return unsubscribe function
    return () => {
      const index = this.interceptors.indexOf(interceptor);
      if (index > -1) {
        this.interceptors.splice(index, 1);
      }
    };
  }

  /**
   * Remove an interceptor by name
   */
  remove(name: string): boolean {
    const index = this.interceptors.findIndex(i => i.name === name);
    if (index > -1) {
      this.interceptors.splice(index, 1);
      return true;
    }
    return false;
  }

  /**
   * Clear all interceptors
   */
  clear(): void {
    this.interceptors = [];
  }

  /**
   * Get all interceptors
   */
  getAll(): Interceptor[] {
    return [...this.interceptors];
  }

  /**
   * Apply request interceptors
   */
  async applyRequestInterceptors(config: RequestConfig): Promise<RequestConfig | null> {
    let currentConfig = config;

    for (const interceptor of this.interceptors) {
      if (interceptor.onRequest) {
        const result = await interceptor.onRequest(currentConfig);
        if (result === null) {
          return null; // Request aborted
        }
        currentConfig = result;
      }
    }

    return currentConfig;
  }

  /**
   * Apply response interceptors
   */
  async applyResponseInterceptors(response: Response, data: any): Promise<any> {
    let currentData = data;

    for (const interceptor of this.interceptors) {
      if (interceptor.onResponse) {
        currentData = await interceptor.onResponse(response, currentData);
      }
    }

    return currentData;
  }

  /**
   * Apply error interceptors
   */
  async applyErrorInterceptors(error: ApiError): Promise<void> {
    for (const interceptor of this.interceptors) {
      if (interceptor.onError) {
        await interceptor.onError(error);
      }
    }
  }
}
