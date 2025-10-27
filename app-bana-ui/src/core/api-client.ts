/**
 * API Client Wrapper for AppBana
 * Provides a centralized HTTP client with interceptor support
 */

import { InterceptorManager, Interceptor, ApiError } from './api-interceptor';

export interface ApiClientConfig {
  baseUrl?: string;
  timeout?: number;
  headers?: Record<string, string>;
}

export interface QueryParams {
  [key: string]: string | number | boolean | null | undefined;
}

export interface RequestConfig {
  url?: string;
  method?: string;
  headers?: HeadersInit;
  body?: any;
  params?: QueryParams;
  [key: string]: any; // Allow additional properties like _retryCount
}

export interface RequestOptions {
  url: string;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  headers?: Record<string, string>;
  body?: any;
  params?: QueryParams;
}

/**
 * Main API Client Class
 * Wraps fetch API with interceptor support and convenience methods
 */
export class ApiClient {
  private baseUrl: string;
  private defaultHeaders: Record<string, string>;
  private timeout: number;
  private interceptors: InterceptorManager;
  private abortControllers: Map<string, AbortController>;

  constructor(config: ApiClientConfig = {}) {
    this.baseUrl = config.baseUrl || '';
    this.defaultHeaders = config.headers || {};
    this.timeout = config.timeout || 30000;
    this.interceptors = new InterceptorManager();
    this.abortControllers = new Map();
  }

  /**
   * Get the interceptor manager
   */
  get interceptor(): InterceptorManager {
    return this.interceptors;
  }

  /**
   * Set base URL
   */
  setBaseUrl(url: string): void {
    this.baseUrl = url;
  }

  /**
   * Set default header
   */
  setHeader(key: string, value: string): void {
    this.defaultHeaders[key] = value;
  }

  /**
   * Remove default header
   */
  removeHeader(key: string): void {
    delete this.defaultHeaders[key];
  }

  /**
   * Build URL with query parameters
   */
  private buildUrl(endpoint: string, params?: QueryParams): string {
    const url = endpoint.startsWith('http')
      ? endpoint
      : `${this.baseUrl}${endpoint}`;

    if (!params) return url;

    const searchParams = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        searchParams.set(key, String(value));
      }
    });

    const queryString = searchParams.toString();
    return queryString ? `${url}?${queryString}` : url;
  }

  /**
   * Create an ApiError from response
   */
  private async createApiError(response: Response): Promise<ApiError> {
    const error = new Error(`HTTP ${response.status}: ${response.statusText}`) as ApiError;
    error.status = response.status;
    error.statusText = response.statusText;
    error.response = response;

    try {
      // Clone the response before reading to avoid "body already read" errors
      const clonedResponse = response.clone();
      const text = await clonedResponse.text();
      if (text) {
        try {
          error.data = JSON.parse(text);
        } catch {
          error.data = text;
        }
      }
    } catch {
      // Ignore body parsing errors
    }

    return error;
  }

  /**
   * Main request method with interceptor support
   */
  async request<T = any>(endpoint: string, config: RequestConfig = {}): Promise<T> {
    const { params, skipInterceptors, ...fetchOptions } = config;

    // Build full URL
    const url = this.buildUrl(endpoint, params);

    // Prepare request config
    let requestConfig: RequestConfig = {
      ...fetchOptions,
      url,
      headers: {
        ...this.defaultHeaders,
        ...fetchOptions.headers,
      },
    };

    // Apply request interceptors
    if (!skipInterceptors) {
      const modifiedConfig = await this.interceptors.applyRequestInterceptors(requestConfig);
      if (modifiedConfig === null) {
        throw new Error('Request aborted by interceptor');
      }
      requestConfig = modifiedConfig;
    }

    // Setup abort controller for timeout
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.timeout);

    try {
      // Make the request
      const response = await fetch(requestConfig.url!, {
        ...requestConfig,
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      // Handle error responses
      if (!response.ok) {
        const error = await this.createApiError(response);
        if (!skipInterceptors) {
          await this.interceptors.applyErrorInterceptors(error);
        }
        throw error;
      }

      // Parse response
      const contentType = response.headers.get('content-type');
      let data: any;

      if (contentType?.includes('application/json')) {
        data = await response.json();
      } else {
        const text = await response.text();
        try {
          data = JSON.parse(text);
        } catch {
          data = text;
        }
      }

      // Apply response interceptors
      if (!skipInterceptors) {
        data = await this.interceptors.applyResponseInterceptors(response, data);
      }

      return data as T;

    } catch (error: any) {
      clearTimeout(timeoutId);

      // Handle abort/timeout
      if (error.name === 'AbortError') {
        const timeoutError = new Error('Request timeout') as ApiError;
        if (!skipInterceptors) {
          await this.interceptors.applyErrorInterceptors(timeoutError);
        }
        throw timeoutError;
      }

      // Handle other errors
      const apiError = error as ApiError;
      if (!skipInterceptors && !apiError.status) {
        await this.interceptors.applyErrorInterceptors(apiError);
      }

      throw error;
    }
  }

  /**
   * GET request
   */
  async get<T = any>(endpoint: string, params?: QueryParams, config?: RequestConfig): Promise<T> {
    return this.request<T>(endpoint, {
      ...config,
      method: 'GET',
      params,
    });
  }

  /**
   * POST request
   */
  async post<T = any>(endpoint: string, data?: any, config?: RequestConfig): Promise<T> {
    return this.request<T>(endpoint, {
      ...config,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...config?.headers,
      },
      body: data ? JSON.stringify(data) : undefined,
    });
  }

  /**
   * PUT request
   */
  async put<T = any>(endpoint: string, data?: any, config?: RequestConfig): Promise<T> {
    return this.request<T>(endpoint, {
      ...config,
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        ...config?.headers,
      },
      body: data ? JSON.stringify(data) : undefined,
    });
  }

  /**
   * PATCH request
   */
  async patch<T = any>(endpoint: string, data?: any, config?: RequestConfig): Promise<T> {
    return this.request<T>(endpoint, {
      ...config,
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        ...config?.headers,
      },
      body: data ? JSON.stringify(data) : undefined,
    });
  }

  /**
   * DELETE request
   */
  async delete<T = any>(endpoint: string, params?: QueryParams, config?: Omit<RequestConfig, 'params'>): Promise<T> {
    return this.request<T>(endpoint, {
      ...config,
      method: 'DELETE',
      params,
    });
  }

  /**
   * Cancel a request by key
   */
  cancel(key: string): void {
    const controller = this.abortControllers.get(key);
    if (controller) {
      controller.abort();
      this.abortControllers.delete(key);
    }
  }

  /**
   * Cancel all pending requests
   */
  cancelAll(): void {
    this.abortControllers.forEach(controller => controller.abort());
    this.abortControllers.clear();
  }
}

// Create default instance
export const apiClient = new ApiClient();
