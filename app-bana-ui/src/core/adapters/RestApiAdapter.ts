/**
 * REST API Adapter
 * 
 * Connects to external REST APIs and maps them to entities.
 * Useful for integrating with external services (Stripe, GitHub, etc.)
 * or connecting to existing REST backends.
 */

import { BaseAdapter } from '../DataSourceAdapter';
import type {
  QueryParams,
  QueryResult,
  DatasourceCapabilities,
  ConnectionTestResult,
  Filter
} from '../DataSourceAdapter';

/**
 * REST API Configuration
 */
export interface RestApiConfig {
  baseUrl: string;              // Base URL (e.g., "https://api.github.com")
  apiKey?: string;              // API key for authentication
  authType?: 'none' | 'apikey' | 'bearer' | 'basic' | 'oauth2';
  headers?: Record<string, string>; // Custom headers
  
  // Authentication details
  username?: string;            // For basic auth
  password?: string;            // For basic auth
  tokenEndpoint?: string;       // OAuth2 token endpoint
  clientId?: string;            // OAuth2 client ID
  clientSecret?: string;        // OAuth2 client secret
  
  // Request/Response mapping
  idField?: string;             // ID field name (default: "id")
  dataField?: string;           // Response data field (e.g., "items", "data")
  totalField?: string;          // Response total count field
  paginationStyle?: 'offset' | 'page' | 'cursor'; // Pagination style
  
  // Rate limiting
  rateLimit?: number;           // Max requests per minute
  retryAttempts?: number;       // Retry failed requests
  timeout?: number;             // Request timeout (ms)
}

/**
 * REST API Adapter
 */
export class RestApiAdapter extends BaseAdapter {
  readonly capabilities: DatasourceCapabilities = {
    create: true,
    read: true,
    update: true,
    delete: true,
    transactions: false,
    relationships: false,
    fullTextSearch: false,
    aggregations: false,
    pagination: true,
    sorting: true,
    filtering: true,
    schemaMigration: false,
    indexing: false,
    constraints: false,
    realtime: false,
    caching: true,
    offline: false
  };
  
  private config!: RestApiConfig;
  private headers: Record<string, string> = {};
  private requestCount = 0;
  private lastRequestTime = 0;
  
  async connect(config: RestApiConfig): Promise<void> {
    this.config = {
      ...config,
      idField: config.idField || 'id',
      paginationStyle: config.paginationStyle || 'offset',
      rateLimit: config.rateLimit || 60,
      retryAttempts: config.retryAttempts || 3,
      timeout: config.timeout || 30000
    };
    
    // Setup headers
    this.headers = { ...config.headers };
    
    // Setup authentication
    if (config.authType === 'apikey' && config.apiKey) {
      this.headers['X-API-Key'] = config.apiKey;
    } else if (config.authType === 'bearer' && config.apiKey) {
      this.headers['Authorization'] = `Bearer ${config.apiKey}`;
    } else if (config.authType === 'basic' && config.username && config.password) {
      const credentials = btoa(`${config.username}:${config.password}`);
      this.headers['Authorization'] = `Basic ${credentials}`;
    }
    
    // Set default content type
    if (!this.headers['Content-Type']) {
      this.headers['Content-Type'] = 'application/json';
    }
  }
  
  async testConnection(): Promise<ConnectionTestResult> {
    const startTime = Date.now();
    
    try {
      const response = await this.fetch(this.config.baseUrl, {
        method: 'GET'
      });
      
      const elapsedMs = Date.now() - startTime;
      
      if (response.ok) {
        return {
          success: true,
          message: `Connected to ${this.config.baseUrl}`,
          metadata: {
            version: response.headers.get('X-API-Version') || undefined,
            product: response.headers.get('Server') || 'REST API',
            elapsedMs
          }
        };
      } else {
        return {
          success: false,
          message: `HTTP ${response.status}: ${response.statusText}`
        };
      }
    } catch (error: any) {
      return {
        success: false,
        message: error.message
      };
    }
  }
  
  async query<T = Record<string, any>>(entity: string, params: QueryParams): Promise<QueryResult<T>> {
    const url = this.buildUrl(entity, params);
    
    const response = await this.fetch(url, {
      method: 'GET'
    });
    
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
    
    const responseData = await response.json();
    
    return this.normalizeResponse<T>(responseData, params);
  }
  
  async get(entity: string, id: string): Promise<Record<string, any> | null> {
    const url = `${this.config.baseUrl}/${entity}/${id}`;
    
    const response = await this.fetch(url, {
      method: 'GET'
    });
    
    if (response.status === 404) {
      return null;
    }
    
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  }
  
  async create(entity: string, data: Record<string, any>): Promise<Record<string, any>> {
    const url = `${this.config.baseUrl}/${entity}`;
    
    const response = await this.fetch(url, {
      method: 'POST',
      body: JSON.stringify(data)
    });
    
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  }
  
  async update(entity: string, id: string, data: Record<string, any>): Promise<Record<string, any>> {
    const url = `${this.config.baseUrl}/${entity}/${id}`;
    
    const response = await this.fetch(url, {
      method: 'PUT',
      body: JSON.stringify(data)
    });
    
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  }
  
  async delete(entity: string, id: string): Promise<void> {
    const url = `${this.config.baseUrl}/${entity}/${id}`;
    
    const response = await this.fetch(url, {
      method: 'DELETE'
    });
    
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
  }
  
  async disconnect(): Promise<void> {
    // No persistent connection for REST APIs
    this.requestCount = 0;
    this.lastRequestTime = 0;
  }
  
  /**
   * Build URL with query parameters
   */
  private buildUrl(entity: string, params: QueryParams): string {
    const url = new URL(`${this.config.baseUrl}/${entity}`);
    
    // Pagination
    if (params.limit !== undefined) {
      if (this.config.paginationStyle === 'offset') {
        url.searchParams.set('limit', String(params.limit));
        if (params.offset !== undefined) {
          url.searchParams.set('offset', String(params.offset));
        }
      } else if (this.config.paginationStyle === 'page') {
        url.searchParams.set('per_page', String(params.limit));
        const page = Math.floor((params.offset || 0) / params.limit) + 1;
        url.searchParams.set('page', String(page));
      }
    }
    
    // Search
    if (params.search) {
      url.searchParams.set('q', params.search);
    }
    
    // Sorting
    if (params.sort && params.sort.length > 0) {
      const sortStr = params.sort
        .map(s => `${s.desc ? '-' : ''}${s.field}`)
        .join(',');
      url.searchParams.set('sort', sortStr);
    }
    
    // Filters
    if (params.filters && params.filters.length > 0) {
      for (const filter of params.filters) {
        const paramValue = this.filterToQueryParam(filter);
        if (paramValue !== null) {
          url.searchParams.set(filter.field, paramValue);
        }
      }
    }
    
    // Fields (select specific fields)
    if (params.fields && params.fields.length > 0) {
      url.searchParams.set('fields', params.fields.join(','));
    }
    
    return url.toString();
  }
  
  /**
   * Convert filter to query parameter value
   */
  private filterToQueryParam(filter: Filter): string | null {
    switch (filter.operator) {
      case 'eq':
        return String(filter.value);
      case 'ne':
        return `!${filter.value}`;
      case 'gt':
        return `>${filter.value}`;
      case 'gte':
        return `>=${filter.value}`;
      case 'lt':
        return `<${filter.value}`;
      case 'lte':
        return `<=${filter.value}`;
      case 'in':
        return Array.isArray(filter.value) ? filter.value.join(',') : String(filter.value);
      case 'contains':
        return `*${filter.value}*`;
      default:
        return String(filter.value);
    }
  }
  
  /**
   * Normalize API response to QueryResult format
   */
  private normalizeResponse<T>(responseData: any, params: QueryParams): QueryResult<T> {
    let data: T[];
    let total: number;
    
    // Handle different response formats
    if (Array.isArray(responseData)) {
      // Response is directly an array
      data = responseData;
      total = responseData.length;
    } else if (this.config.dataField && responseData[this.config.dataField]) {
      // Response has data in specific field (e.g., { items: [...], total: 100 })
      data = responseData[this.config.dataField];
      total = this.config.totalField && responseData[this.config.totalField]
        ? responseData[this.config.totalField]
        : data.length;
    } else if (responseData.data) {
      // Common pattern: { data: [...], total: 100 }
      data = responseData.data;
      total = responseData.total || responseData.count || data.length;
    } else if (responseData.items) {
      // Alternative pattern: { items: [...], total_count: 100 }
      data = responseData.items;
      total = responseData.total_count || responseData.total || data.length;
    } else {
      // Unknown format, wrap in array
      data = [responseData] as T[];
      total = 1;
    }
    
    const offset = params.offset || 0;
    const hasMore = offset + data.length < total;
    
    return {
      data,
      total,
      hasMore,
      nextOffset: hasMore ? offset + data.length : undefined
    };
  }
  
  /**
   * Fetch with rate limiting and retries
   */
  private async fetch(url: string, options: RequestInit, attempt = 1): Promise<Response> {
    // Rate limiting
    await this.enforceRateLimit();
    
    // Add timeout
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.config.timeout);
    
    try {
      const response = await fetch(url, {
        ...options,
        headers: this.headers,
        signal: controller.signal
      });
      
      clearTimeout(timeoutId);
      
      // Retry on rate limit or server error
      if ((response.status === 429 || response.status >= 500) && attempt < this.config.retryAttempts!) {
        const retryAfter = response.headers.get('Retry-After');
        const waitTime = retryAfter ? Number.parseInt(retryAfter) * 1000 : Math.pow(2, attempt) * 1000;
        
        console.warn(`Request failed with ${response.status}, retrying in ${waitTime}ms...`);
        await new Promise(resolve => setTimeout(resolve, waitTime));
        
        return this.fetch(url, options, attempt + 1);
      }
      
      return response;
    } catch (error: any) {
      clearTimeout(timeoutId);
      
      if (error.name === 'AbortError') {
        throw new Error(`Request timeout after ${this.config.timeout}ms`);
      }
      
      throw error;
    }
  }
  
  /**
   * Enforce rate limiting
   */
  private async enforceRateLimit(): Promise<void> {
    const now = Date.now();
    const timeSinceLastRequest = now - this.lastRequestTime;
    const minInterval = 60000 / this.config.rateLimit!; // ms between requests
    
    if (timeSinceLastRequest < minInterval) {
      const waitTime = minInterval - timeSinceLastRequest;
      await new Promise(resolve => setTimeout(resolve, waitTime));
    }
    
    this.lastRequestTime = Date.now();
    this.requestCount++;
  }
}
