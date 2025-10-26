/**
 * API Client Setup and Configuration
 * Initialize interceptors and configure the API client for AppBana
 */

import { apiClient } from './api-client.ts';
import {
  authInterceptor,
  loggingInterceptor,
  errorHandlerInterceptor,
  loadingInterceptor,
  requestIdInterceptor,
  retryInterceptor,
} from './api-interceptors.ts';

/**
 * Initialize API client with default interceptors
 */
export function setupApiClient(options: {
  enableLogging?: boolean;
  enableRetry?: boolean;
  onLoadingChange?: (isLoading: boolean, activeRequests: number) => void;
  onError?: (error: any) => void;
} = {}) {
  const {
    enableLogging = process.env.NODE_ENV === 'development',
    enableRetry = true,
    onLoadingChange,
    onError,
  } = options;

  // Add request ID to all requests
  apiClient.interceptor.use(requestIdInterceptor());

  // Add authentication token from localStorage
  apiClient.interceptor.use(
    authInterceptor(() => {
      return localStorage.getItem('appbana_token');
    })
  );

  // Add logging in development mode
  if (enableLogging) {
    apiClient.interceptor.use(loggingInterceptor({
      logRequests: true,
      logResponses: true,
      logErrors: true,
    }));
  }

  // Add retry logic for failed requests
  if (enableRetry) {
    apiClient.interceptor.use(retryInterceptor({
      maxRetries: 3,
      retryDelay: 1000,
      retryCondition: (error) => {
        // Only retry on 5xx errors and network errors
        return !error.status || error.status >= 500;
      },
    }));
  }

  // Add global error handler
  apiClient.interceptor.use(errorHandlerInterceptor(onError));

  // Add loading state management
  if (onLoadingChange) {
    apiClient.interceptor.use(loadingInterceptor(onLoadingChange));
  }

  console.log('[API Client] Initialized with interceptors');
}

/**
 * Set authentication token
 */
export function setAuthToken(token: string | null) {
  if (token) {
    localStorage.setItem('appbana_token', token);
  } else {
    localStorage.removeItem('appbana_token');
  }
}

/**
 * Get current authentication token
 */
export function getAuthToken(): string | null {
  return localStorage.getItem('appbana_token');
}

/**
 * Clear authentication
 */
export function clearAuth() {
  localStorage.removeItem('appbana_token');
}

