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
    // In development and production, use relative paths to allow correct proxying
    // (Vite proxy handles localhost:5173 -> localhost:8080)
    return '';
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
