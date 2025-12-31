/**
 * API Configuration
 * Centralized configuration for API base URLs
 */

/**
 * Get the API base URL based on the current environment
 * - In development (port 5173), uses localhost:8080
 * - In production/deployment, uses the current origin
 */
export function getApiBaseUrl(): string {
    // Check if we're running in Vite dev server (port 5173)
    if (globalThis.location?.port === '5173') {
        return 'http://localhost:8080';
    }

    // In production, use the current origin (same server)
    return globalThis.location?.origin || '';
}

/**
 * Get the full API URL for a given endpoint
 * @param endpoint - The API endpoint path (should start with /)
 */
export function getApiUrl(endpoint: string): string {
    const base = getApiBaseUrl();
    return `${base}${endpoint}`;
}

/**
 * Configuration object for API settings
 */
export const ApiConfig = {
    /**
     * Get the base URL for API requests
     */
    getBaseUrl: getApiBaseUrl,

    /**
     * Get a full API URL for an endpoint
     */
    getUrl: getApiUrl,
} as const;
