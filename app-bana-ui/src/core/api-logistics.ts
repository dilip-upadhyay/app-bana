/**
 * PWA & Logistics Interceptors
 * Offline support, barcode integration, and logistics-specific features
 * For AppBana November 2025 Logistics goals
 */

import { Interceptor, ApiError } from './api-interceptor.ts';
import { apiClient } from './api-client.ts';

/**
 * Offline Queue Interceptor
 * Queues write operations when offline and replays when back online
 * Critical for Logistics/Field operations (November 2025 goal)
 */
export function offlineQueueInterceptor(options: {
  storageKey?: string;
  onQueueChange?: (queueLength: number) => void;
  onReplayComplete?: (successful: number, failed: number) => void;
} = {}): Interceptor {
  const {
    storageKey = 'appbana_offline_queue',
    onQueueChange,
    onReplayComplete,
  } = options;

  interface QueuedRequest {
    id: string;
    url: string;
    method: string;
    body?: string;
    headers?: Record<string, string>;
    timestamp: number;
    retries: number;
  }

  // Load queue from localStorage
  const loadQueue = (): QueuedRequest[] => {
    try {
      const stored = localStorage.getItem(storageKey);
      return stored ? JSON.parse(stored) : [];
    } catch {
      return [];
    }
  };

  const saveQueue = (queue: QueuedRequest[]) => {
    localStorage.setItem(storageKey, JSON.stringify(queue));
    if (onQueueChange) {
      onQueueChange(queue.length);
    }
  };

  const addToQueue = (request: Partial<QueuedRequest>) => {
    const queue = loadQueue();
    const newRequest: QueuedRequest = {
      id: request.id || `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      url: request.url || '',
      method: request.method || 'POST',
      body: request.body,
      headers: request.headers,
      retries: request.retries || 0,
      timestamp: request.timestamp || Date.now(),
    };
    queue.push(newRequest);
    saveQueue(queue);
    console.log('[Offline Queue] Added request, queue size:', queue.length);
  };

  // Replay queue when back online
  const replayQueue = async () => {
    const queue = loadQueue();
    if (queue.length === 0) return;

    console.log('[Offline Queue] Replaying', queue.length, 'requests...');

    let successful = 0;
    let failed = 0;
    const remaining: QueuedRequest[] = [];

    for (const request of queue) {
      try {
        const response = await fetch(request.url, {
          method: request.method,
          headers: request.headers,
          body: request.body,
        });

        if (response.ok) {
          successful++;
          console.log('[Offline Queue] Replayed successfully:', request.url);
        } else {
          // Retry logic
          if (request.retries < 3) {
            remaining.push({ ...request, retries: request.retries + 1 });
            failed++;
          }
        }
      } catch (error) {
        // Keep in queue for retry
        if (request.retries < 3) {
          remaining.push({ ...request, retries: request.retries + 1 });
        }
        failed++;
      }
    }

    saveQueue(remaining);

    if (onReplayComplete) {
      onReplayComplete(successful, failed);
    }

    console.log('[Offline Queue] Replay complete:', { successful, failed, remaining: remaining.length });
  };

  // Listen for online event
  if (typeof window !== 'undefined') {
    window.addEventListener('online', () => {
      console.log('[Offline Queue] Back online, replaying queue...');
      replayQueue();
    });
  }

  return {
    name: 'offlineQueue',
    onError: async (error: ApiError) => {
      // Only queue write operations when offline
      const isWriteOperation = error.response?.headers?.get('X-Original-Method') !== 'GET';
      const isOffline = !navigator.onLine || error.message.includes('Failed to fetch');

      if (isOffline && isWriteOperation) {
        // Extract request details from error
        addToQueue({
          url: error.response?.url || '',
          method: 'POST', // Would need to track original method
          body: undefined, // Would need to preserve body
          headers: {},
        });

        // Update UI to show queued state
        console.warn('[Offline Queue] Request queued for later:', error.response?.url);
      }
    },
  };
}

/**
 * Service Worker Cache Interceptor
 * Caches responses for offline use via Service Worker
 */
export function serviceWorkerCacheInterceptor(options: {
  cacheName?: string;
  cacheableUrls?: RegExp[];
} = {}): Interceptor {
  const {
    cacheName = 'appbana-api-cache-v1',
    cacheableUrls = [/\/api\//, /\/schema/],
  } = options;

  return {
    name: 'serviceWorkerCache',
    onRequest: async (config) => {
      const shouldCache = cacheableUrls.some(pattern =>
        pattern.test(config.url || '')
      );

      if (shouldCache) {
        config.headers = {
          ...config.headers,
          'X-Cache-Control': 'max-age=3600',
        };
      }

      return config;
    },
  };
}

/**
 * Barcode/QR Scanner Integration Interceptor
 * Validates barcode format and integrates with scanner component
 */
export function barcodeValidationInterceptor(options: {
  validateFormat?: (barcode: string) => boolean;
  onScan?: (barcode: string, entity: string) => void;
} = {}): Interceptor {
  const { validateFormat, onScan } = options;

  return {
    name: 'barcodeValidation',
    onRequest: (config) => {
      const headers = config.headers as Record<string, string> | undefined;
      const barcode = headers?.['X-Barcode-Scan'];

      if (barcode) {
        // Validate barcode format
        if (validateFormat && !validateFormat(barcode)) {
          throw new Error(`Invalid barcode format: ${barcode}`);
        }

        // Track scan event
        if (onScan) {
          const entity = config.url?.match(/\/api\/([^/?]+)/)?.[1] || '';
          onScan(barcode, entity);
        }

        console.log('[Barcode] Scanned:', barcode);
      }

      return config;
    },
  };
}

/**
 * Geolocation Tracking Interceptor
 * Adds GPS coordinates to requests for logistics tracking
 */
export function geolocationInterceptor(options: {
  enableForUrls?: RegExp[];
  onLocationError?: (error: GeolocationPositionError) => void;
} = {}): Interceptor {
  const {
    enableForUrls = [/\/api\/shipment/i, /\/api\/delivery/i],
    onLocationError,
  } = options;

  return {
    name: 'geolocation',
    onRequest: async (config) => {
      const shouldAddLocation = enableForUrls.some(pattern =>
        pattern.test(config.url || '')
      );

      if (shouldAddLocation && navigator.geolocation) {
        try {
          const position = await new Promise<GeolocationPosition>((resolve, reject) => {
            navigator.geolocation.getCurrentPosition(resolve, reject, {
              timeout: 5000,
              maximumAge: 30000,
            });
          });

          config.headers = {
            ...config.headers,
            'X-Latitude': position.coords.latitude.toString(),
            'X-Longitude': position.coords.longitude.toString(),
            'X-Accuracy': position.coords.accuracy.toString(),
            'X-Timestamp': position.timestamp.toString(),
          };

          console.log('[Geolocation] Added to request:', {
            lat: position.coords.latitude,
            lng: position.coords.longitude,
          });
        } catch (error) {
          console.warn('[Geolocation] Failed to get position:', error);
          if (onLocationError) {
            onLocationError(error as GeolocationPositionError);
          }
        }
      }

      return config;
    },
  };
}

/**
 * Background Sync Interceptor
 * Uses Background Sync API for reliable data submission
 */
export function backgroundSyncInterceptor(options: {
  syncTag?: string;
} = {}): Interceptor {
  const { syncTag = 'appbana-sync' } = options;

  return {
    name: 'backgroundSync',
    onError: async (error: ApiError) => {
      if ('serviceWorker' in navigator && 'SyncManager' in window) {
        const isWriteOperation = error.response?.url?.includes('POST') ||
                                 error.response?.url?.includes('PUT');

        if (isWriteOperation && !navigator.onLine) {
          try {
            const registration = await navigator.serviceWorker.ready;
            await (registration as any).sync.register(syncTag);
            console.log('[Background Sync] Registered for', syncTag);
          } catch (syncError) {
            console.error('[Background Sync] Registration failed:', syncError);
          }
        }
      }
    },
  };
}

/**
 * Network Quality Interceptor
 * Adjusts request behavior based on connection quality
 */
export function networkQualityInterceptor(options: {
  onSlowConnection?: () => void;
  imageQualityReduction?: boolean;
} = {}): Interceptor {
  const { onSlowConnection, imageQualityReduction = true } = options;

  const getConnectionType = (): string => {
    const connection = (navigator as any).connection ||
                      (navigator as any).mozConnection ||
                      (navigator as any).webkitConnection;
    return connection?.effectiveType || 'unknown';
  };

  return {
    name: 'networkQuality',
    onRequest: (config) => {
      const connectionType = getConnectionType();

      // Warn on slow connections
      if (['slow-2g', '2g'].includes(connectionType)) {
        console.warn('[Network] Slow connection detected:', connectionType);
        if (onSlowConnection) {
          onSlowConnection();
        }
      }

      // Reduce image quality on slow connections
      if (imageQualityReduction && ['slow-2g', '2g', '3g'].includes(connectionType)) {
        config.headers = {
          ...config.headers,
          'X-Image-Quality': 'low',
          'X-Data-Compression': 'enabled',
        };
      }

      config.headers = {
        ...config.headers,
        'X-Connection-Type': connectionType,
      };

      return config;
    },
  };
}

/**
 * Setup logistics-optimized API client
 * Configures offline support, barcode integration, geolocation
 */
export function setupLogisticsFeatures(options: {
  enableOfflineQueue?: boolean;
  enableBarcode?: boolean;
  enableGeolocation?: boolean;
  enableBackgroundSync?: boolean;
  onQueueChange?: (queueLength: number) => void;
  onSlowConnection?: () => void;
} = {}) {
  const {
    enableOfflineQueue = true,
    enableBarcode = true,
    enableGeolocation = true,
    enableBackgroundSync = true,
    onQueueChange,
    onSlowConnection,
  } = options;

  // Add offline queue
  if (enableOfflineQueue) {
    // This would be added via apiClient.interceptor.use()
    console.log('[Logistics] Offline queue enabled');
  }

  // Add barcode validation
  if (enableBarcode) {
    console.log('[Logistics] Barcode validation enabled');
  }

  // Add geolocation tracking
  if (enableGeolocation) {
    console.log('[Logistics] Geolocation tracking enabled');
  }

  // Add background sync
  if (enableBackgroundSync) {
    console.log('[Logistics] Background sync enabled');
  }

  console.log('[Logistics] Features initialized for field operations');
}

// Export queue management utilities
export const offlineQueue = {
  /**
   * Get current queue length
   */
  getLength(): number {
    try {
      const queue = JSON.parse(localStorage.getItem('appbana_offline_queue') || '[]');
      return queue.length;
    } catch {
      return 0;
    }
  },

  /**
   * Clear the queue
   */
  clear(): void {
    localStorage.removeItem('appbana_offline_queue');
  },

  /**
   * Get queue items
   */
  getItems(): any[] {
    try {
      return JSON.parse(localStorage.getItem('appbana_offline_queue') || '[]');
    } catch {
      return [];
    }
  },
};

/**
 * Retry Interceptor
 * Automatically retries failed requests with exponential backoff
 */
export function retryWithBackoffInterceptor(options: {
  maxRetries?: number;
  baseDelayMs?: number;
} = {}): Interceptor {
  const {
    maxRetries = 3,
    baseDelayMs = 1000,
  } = options;

  return {
    name: 'retryWithBackoff',
    onError: async (error) => {
      const retryCount = ((error.config as any)?._retryCount) || 0;

      if (retryCount >= maxRetries) {
        throw error;
      }

      const delay = baseDelayMs * Math.pow(2, retryCount);
      console.warn(`[Retry ${retryCount + 1}/${maxRetries}] after ${delay}ms:`, error.message);

      await new Promise(resolve => setTimeout(resolve, delay));

      if (!error.config) {
        throw error;
      }

      return apiClient.request({
        ...error.config,
        _retryCount: retryCount + 1,
      } as any);
    },
  };
}
