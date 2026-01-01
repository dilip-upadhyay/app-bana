/**
 * API Client Wrapper for AppBana
 * Provides a centralized HTTP client with interceptor support
 */

import { InterceptorManager, ApiError } from './api-interceptor';
import { RuntimeContext } from '../runtime/RuntimeContext';
import { getApiBaseUrl } from './api-config';

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
  private readonly timeout: number;
  private readonly interceptors: InterceptorManager;
  private readonly abortControllers: Map<string, AbortController>;

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
    for (const [key, value] of Object.entries(params)) {
      if (value !== null && value !== undefined && value !== '') {
        searchParams.set(key, String(value));
      }
    }

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

  private async applyErrorResponse(response: Response, skipInterceptors?: boolean): Promise<never> {
    const error = await this.createApiError(response);
    if (!skipInterceptors) {
      await this.interceptors.applyErrorInterceptors(error);
    }
    throw error;
  }

  private async parseResponse(response: Response): Promise<any> {
    const contentType = response.headers.get('content-type');
    if (contentType?.includes('application/json')) {
      return response.json();
    }
    const text = await response.text();
    try { return JSON.parse(text); } catch { return text; }
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
      const response = await fetch(requestConfig.url!, { ...requestConfig, signal: controller.signal });
      if (!response.ok) await this.applyErrorResponse(response, skipInterceptors);
      let data = await this.parseResponse(response);
      if (!skipInterceptors) data = await this.interceptors.applyResponseInterceptors(response, data);
      return data as T;
    } catch (error: any) {
      // Handle abort/timeout
      if (error.name === 'AbortError') {
        const timeoutError = new Error('Request timeout') as ApiError;
        if (!skipInterceptors) await this.interceptors.applyErrorInterceptors(timeoutError);
        throw timeoutError;
      }
      const apiError = error as ApiError;
      if (!skipInterceptors && !apiError.status) await this.interceptors.applyErrorInterceptors(apiError);
      throw error;
    } finally {
      clearTimeout(timeoutId);
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
    for (const controller of this.abortControllers.values()) {
      controller.abort();
    }
    this.abortControllers.clear();
  }
}

// Create default instance
export const apiClient = new ApiClient();

/**
 * Fetch table data for entity and fields
 * @param entity string
 * @param fields string[]
 * @param options { pageSize?: number, sort?: string }
 */
export interface TableFetchOptions {
  pageSize?: number;
  sort?: string;
  page?: number; // 1-based page number
  offset?: number; // explicit override if provided (takes precedence over page)
  filters?: Record<string, string>; // per-column filters (exact match server-side)
}

/**
 * Fetch table data for an entity and selected fields with pagination support.
 * Uses existing advanced list endpoint: /api/{entity}?fields=a,b&limit=25&offset=0&sort=name
 */
export async function fetchTableData(entity: string, fields: string[], options: TableFetchOptions = {}) {
  const limit = options.pageSize || 25;
  let offset = 0;
  if (typeof options.offset === 'number') {
    offset = options.offset;
  } else if (options.page && options.page > 0) {
    offset = (options.page - 1) * limit;
  }
  // Always include 'id' field for stable selection/keying even if not displayed
  const requestFields = Array.from(new Set(['id', ...fields]));
  const params: Record<string, any> = {
    fields: requestFields.join(','),
    limit,
    offset,
    sort: options.sort || ''
  };
  if (options.filters) {
    const filterPairs: string[] = [];
    for (const [k, v] of Object.entries(options.filters)) {
      if (v && v.trim() !== '') {
        // backend expects raw value; encode to be safe
        filterPairs.push(`${encodeURIComponent(k)}:${encodeURIComponent(v.trim())}`);
      }
    }
    if (filterPairs.length > 0) params.filter = filterPairs.join(',');
  }
  const base = getApiBaseUrl();
  const { tenantId, appId } = RuntimeContext.getInstance().getContextSafe();
  return apiClient.get(`${base}/appbana-studio/${tenantId}/apps/${appId}/${entity}`, params);
}

/** Bulk delete records by ids */
export async function bulkDelete(entity: string, ids: (string | number)[]) {
  const base = getApiBaseUrl();
  const { tenantId, appId } = RuntimeContext.getInstance().getContextSafe();
  return apiClient.post(`${base}/appbana-studio/${tenantId}/apps/${appId}/${entity}/bulk-delete`, { ids });
}

/** Bulk export records by ids; returns { count, rows } */
export async function bulkExport(entity: string, ids: (string | number)[]) {
  const base = getApiBaseUrl();
  const { tenantId, appId } = RuntimeContext.getInstance().getContextSafe();
  return apiClient.post(`${base}/appbana-studio/${tenantId}/apps/${appId}/${entity}/bulk-export`, { ids });
}

/** Create a new row */
export async function createRow(entity: string, data: Record<string, any>) {
  const base = getApiBaseUrl();
  const { tenantId, appId } = RuntimeContext.getInstance().getContextSafe();
  // Use app-scoped endpoint to ensure data is properly segregated by tenant and app
  return apiClient.post(`${base}/api/${tenantId}/apps/${appId}/${entity}`, data);
}

/** Update single row by id */
export async function updateRow(entity: string, id: string | number, data: Record<string, any>) {
  const base = getApiBaseUrl();
  const { tenantId, appId } = RuntimeContext.getInstance().getContextSafe();
  return apiClient.put(`${base}/appbana-studio/${tenantId}/apps/${appId}/${entity}/${id}`, data);
}

/**
 * Field-Level Security (FLS) API Functions
 */

/** Get field permissions for current user and entity */
export async function getFieldPermissions(entityName: string): Promise<{ readable: string[], editable: string[] }> {
  const base = getApiBaseUrl();
  try {
    // Call the FLS endpoints we just created
    const [readableResp, editableResp] = await Promise.all([
      fetch(`${base}/api/field-permissions/readable?entity=${encodeURIComponent(entityName)}`),
      fetch(`${base}/api/field-permissions/editable?entity=${encodeURIComponent(entityName)}`)
    ]);

    if (!readableResp.ok || !editableResp.ok) {
      console.warn('FLS API returned error, defaulting to full access');
      return { readable: ['*'], editable: ['*'] };
    }

    const readable = await readableResp.json() as string[];
    const editable = await editableResp.json() as string[];

    return { readable, editable };
  } catch (error) {
    console.warn('FLS API not available, defaulting to full access:', error);
    return { readable: ['*'], editable: ['*'] };
  }
}

/** Check if field is readable for current user */
export function canReadField(fieldName: string, readableFields: string[] | undefined | null): boolean {
  if (!readableFields || !Array.isArray(readableFields)) {
    console.warn('canReadField called with invalid readableFields:', readableFields);
    return true; // Default to allowing read if permissions are invalid
  }
  return readableFields.includes('*') || readableFields.includes(fieldName);
}

/** Check if field is editable for current user */
export function canEditField(fieldName: string, editableFields: string[] | undefined | null): boolean {
  if (!editableFields || !Array.isArray(editableFields)) {
    console.warn('canEditField called with invalid editableFields:', editableFields);
    return true; // Default to allowing edit if permissions are invalid
  }
  return editableFields.includes('*') || editableFields.includes(fieldName);
}
