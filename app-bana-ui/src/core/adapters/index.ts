/**
 * Adapter Index
 * Export all adapters and register them with the registry
 */

export { RestApiAdapter } from './RestApiAdapter';
export { JsonFileAdapter } from './JsonFileAdapter';

// Re-export types
export type { RestApiConfig } from './RestApiAdapter';
export type { JsonFileConfig } from './JsonFileAdapter';
