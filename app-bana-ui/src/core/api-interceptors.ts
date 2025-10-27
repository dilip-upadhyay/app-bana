/**
 * Common API Interceptors for AppBana
 * Pre-built interceptors for common use cases
 */

import { Interceptor, RequestConfig, ApiError } from './api-interceptor.ts';

/**
 * Authentication Interceptor
 * Automatically adds auth token to requests
 */
export function authInterceptor(tokenProvider: () => string | null): Interceptor {
  return {
    name: 'auth',
    onRequest: (config: RequestConfig) => {
      const token = tokenProvider();
      if (token) {
        config.headers = {
          ...config.headers,
          'X-AppBana-Token': token,
        };
      }
      return config;
    },
  };
}

/**
 * Logging Interceptor
 * Logs all requests and responses for debugging
 */
export function loggingInterceptor(options: { logRequests?: boolean; logResponses?: boolean; logErrors?: boolean } = {}): Interceptor {
  const { logRequests = true, logResponses = true, logErrors = true } = options;

  return {
    name: 'logging',
    onRequest: (config: RequestConfig) => {
      if (logRequests) {
        console.log('[API Request]', {
          method: config.method || 'GET',
          url: config.url,
          headers: config.headers,
          body: config.body,
        });
      }
      return config;
    },
    onResponse: (response, data) => {
      if (logResponses) {
        console.log('[API Response]', {
          status: response.status,
          statusText: response.statusText,
          url: response.url,
          data,
        });
      }
      return data;
    },
    onError: (error: ApiError) => {
      if (logErrors) {
        console.error('[API Error]', {
          message: error.message,
          status: error.status,
          statusText: error.statusText,
          url: error.response?.url,
          data: error.data,
        });
      }
    },
  };
}

/**
 * Retry Interceptor
 * Automatically retries failed requests
 */
export function retryInterceptor(options: { maxRetries?: number; retryDelay?: number; retryCondition?: (error: ApiError) => boolean } = {}): Interceptor {
  const { maxRetries = 3, retryDelay = 1000, retryCondition } = options;
  const retryCountMap = new Map<string, number>();

  const shouldRetry = (error: ApiError): boolean => {
    if (retryCondition) {
      return retryCondition(error);
    }
    // Default: retry on 5xx errors and network errors
    return !error.status || error.status >= 500;
  };

  return {
    name: 'retry',
    onError: async (error: ApiError) => {
      const url = error.response?.url || '';
      const retryCount = retryCountMap.get(url) || 0;

      if (retryCount < maxRetries && shouldRetry(error)) {
        retryCountMap.set(url, retryCount + 1);
        console.log(`[Retry] Attempt ${retryCount + 1}/${maxRetries} for ${url}`);

        // Wait before retry with exponential backoff
        await new Promise(resolve => setTimeout(resolve, retryDelay * Math.pow(2, retryCount)));
      } else {
        retryCountMap.delete(url);
      }
    },
  };
}

/**
 * Cache Interceptor
 * Caches GET requests
 */
export function cacheInterceptor(options: { ttl?: number; maxSize?: number } = {}): Interceptor {
  const { ttl = 60000, maxSize = 100 } = options; // Default 60 seconds TTL
  const cache = new Map<string, { data: any; timestamp: number }>();

  return {
    name: 'cache',
    onRequest: (config: RequestConfig) => {
      if (config.method === 'GET' || !config.method) {
        const cached = cache.get(config.url!);
        if (cached && Date.now() - cached.timestamp < ttl) {
          console.log('[Cache] Hit:', config.url);
          // Return cached data by throwing a special response
          throw { __cached: true, data: cached.data };
        }
      }
      return config;
    },
    onResponse: (response, data) => {
      if (response.status === 200 && (response.url.includes('GET') || !response.url.includes('POST'))) {
        const shouldCache = response.status === 200 && (response.url.includes('GET') || !response.url.includes('POST'));
        if (shouldCache) {
          // Clean old cache entries if size limit reached
          if (cache.size >= maxSize) {
            const firstKey = cache.keys().next().value;
            if (firstKey) cache.delete(firstKey);
          }

          cache.set(response.url, {
            data,
            timestamp: Date.now(),
          });
        }
      }
      return data;
    },
  };
}

/**
 * Error Handler Interceptor
 * Shows user-friendly error messages
 */
export function errorHandlerInterceptor(onError?: (error: ApiError) => void): Interceptor {
  return {
    name: 'errorHandler',
    onError: (error: ApiError) => {
      let message = 'An error occurred';

      if (error.status === 401) {
        message = 'Unauthorized. Please log in.';
      } else if (error.status === 403) {
        message = 'You do not have permission to perform this action.';
      } else if (error.status === 404) {
        message = 'Resource not found.';
      } else if (error.status === 500) {
        message = 'Server error. Please try again later.';
      } else if (error.message.includes('timeout')) {
        message = 'Request timeout. Please check your connection.';
      } else if (!error.status) {
        message = 'Network error. Please check your connection.';
      }

      if (onError) {
        onError({ ...error, message });
      } else {
        console.error('[Error Handler]', message, error);
      }
    },
  };
}

/**
 * Loading State Interceptor
 * Manages global loading state
 */
export function loadingInterceptor(
  onLoadingChange: (isLoading: boolean, activeRequests: number) => void
): Interceptor {
  let activeRequests = 0;

  return {
    name: 'loading',
    onRequest: (config: RequestConfig) => {
      activeRequests++;
      onLoadingChange(true, activeRequests);
      return config;
    },
    onResponse: (response, data) => {
      activeRequests = Math.max(0, activeRequests - 1);
      onLoadingChange(activeRequests > 0, activeRequests);
      return data;
    },
    onError: (error: ApiError) => {
      activeRequests = Math.max(0, activeRequests - 1);
      onLoadingChange(activeRequests > 0, activeRequests);
    },
  };
}

/**
 * Request ID Interceptor
 * Adds unique request ID to each request
 */
export function requestIdInterceptor(): Interceptor {
  return {
    name: 'requestId',
    onRequest: (config: RequestConfig) => {
      const requestId = `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
      config.headers = {
        ...config.headers,
        'X-Request-ID': requestId,
      };
      return config;
    },
  };
}

/**
 * Transform Response Interceptor
 * Transforms response data structure
 */
export function transformResponseInterceptor<T>(
  transformer: (data: any) => T
): Interceptor {
  return {
    name: 'transformResponse',
    onResponse: (response, data) => {
      return transformer(data);
    },
  };
}

/**
 * Rate Limiting Interceptor
 * Limits number of requests per time window
 */
export function rateLimitInterceptor(options: { maxRequests?: number; windowMs?: number } = {}): Interceptor {
  const { maxRequests = 100, windowMs = 60000 } = options;
  const requestTimestamps: number[] = [];

  return {
    name: 'rateLimit',
    onRequest: (config: RequestConfig) => {
      const now = Date.now();

      // Remove old timestamps outside the window
      while (requestTimestamps.length > 0 && requestTimestamps[0] < now - windowMs) {
        requestTimestamps.shift();
      }

      // Check if rate limit exceeded
      if (requestTimestamps.length >= maxRequests) {
        throw new Error('Rate limit exceeded. Please try again later.');
      }

      requestTimestamps.push(now);
      return config;
    },
  };
}

/**
 * Token Refresh Interceptor
 * Automatically refreshes auth token on 401
 */
export function tokenRefreshInterceptor(
  refreshTokenFn: () => Promise<string>,
  tokenSetter: (token: string) => void
): Interceptor {
  let isRefreshing = false;
  let refreshPromise: Promise<string> | null = null;

  return {
    name: 'tokenRefresh',
    onError: async (error: ApiError) => {
      if (error.status === 401) {
        if (!isRefreshing) {
          isRefreshing = true;
          refreshPromise = refreshTokenFn();

          try {
            const newToken = await refreshPromise;
            tokenSetter(newToken);
            console.log('[Token Refresh] Token refreshed successfully');
          } catch (refreshError) {
            console.error('[Token Refresh] Failed to refresh token', refreshError);
          } finally {
            isRefreshing = false;
            refreshPromise = null;
          }
        } else if (refreshPromise) {
          // Wait for ongoing refresh
          await refreshPromise;
        }
      }
    },
  };
}
