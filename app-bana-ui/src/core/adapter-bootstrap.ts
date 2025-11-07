/**
 * Adapter Bootstrap
 * Registers all built-in adapters with the registry
 */

import { registerAdapter } from './AdapterRegistry';
import { RestApiAdapter, JsonFileAdapter } from './adapters';

/**
 * Register all built-in adapters
 * Call this once during app initialization
 */
export function registerBuiltInAdapters(): void {
  // REST API Adapter
  registerAdapter('rest-api', RestApiAdapter, {
    name: 'REST API',
    description: 'Connect to external REST APIs',
    configSchema: {
      type: 'object',
      required: ['baseUrl'],
      properties: {
        baseUrl: { type: 'string', description: 'Base URL of the API' },
        apiKey: { type: 'string', description: 'API key for authentication' },
        authType: { 
          type: 'string', 
          enum: ['none', 'apikey', 'bearer', 'basic', 'oauth2'],
          description: 'Authentication type'
        }
      }
    }
  });
  
  registerAdapter('graphql', RestApiAdapter, {
    name: 'GraphQL API',
    description: 'Connect to GraphQL APIs'
  });
  
  registerAdapter('soap', RestApiAdapter, {
    name: 'SOAP API',
    description: 'Connect to SOAP/XML web services'
  });
  
  // File-based Adapters
  registerAdapter('json-file', JsonFileAdapter, {
    name: 'JSON File',
    description: 'File-based JSON storage for prototyping'
  });
  
  registerAdapter('in-memory', JsonFileAdapter, {
    name: 'In-Memory',
    description: 'Volatile in-memory storage (lost on refresh)'
  });
  
  registerAdapter('localstorage', JsonFileAdapter, {
    name: 'LocalStorage',
    description: 'Browser localStorage (persistent, offline-first)'
  });
  
  registerAdapter('sessionstorage', JsonFileAdapter, {
    name: 'SessionStorage',
    description: 'Browser sessionStorage (cleared on tab close)'
  });
  
  console.log('✅ Built-in adapters registered');
}
