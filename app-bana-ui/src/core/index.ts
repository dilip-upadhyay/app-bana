/**
 * Core Module Exports
 * Central export point for all core functionality
 */

// API Client and Interceptors
export { ApiClient, apiClient } from './api-client.ts';
export type { ApiClientConfig, QueryParams, RequestConfig, RequestOptions } from './api-client.ts';

export { InterceptorManager } from './api-interceptor.ts';
export type {
  Interceptor,
  RequestInterceptor,
  ResponseInterceptor,
  ErrorInterceptor,
  ApiError,
} from './api-interceptor.ts';

export {
  authInterceptor,
  loggingInterceptor,
  retryInterceptor,
  cacheInterceptor,
  errorHandlerInterceptor,
  loadingInterceptor,
  requestIdInterceptor,
  transformResponseInterceptor,
  rateLimitInterceptor,
  tokenRefreshInterceptor,
} from './api-interceptors.ts';

// Services
export {
  SchemaService,
  EntityService,
  DatasourceService,
  AuditLogService,
  api,
  schemaService,
  entityService,
  datasourceService,
  auditLogService,
} from './api-service.ts';

// Extended Services (Healthcare, Workflow, Plugins, Reports, Realtime)
export {
  FHIRService,
  WorkflowService,
  PluginService,
  ReportService,
  RealtimeService,
  fhirService,
  workflowService,
  pluginService,
  reportService,
  realtimeService,
  extendedApi,
} from './api-extensions.ts';

// Healthcare & Compliance
export {
  phiAuditInterceptor,
  minimumNecessaryInterceptor,
  dataRedactionInterceptor,
  sessionTimeoutInterceptor,
  breakGlassInterceptor,
  encryptionValidator,
  deidentificationInterceptor,
  consentValidationInterceptor,
  setupHealthcareCompliance,
} from './api-healthcare.ts';

// Logistics & PWA
export {
  offlineQueueInterceptor,
  serviceWorkerCacheInterceptor,
  barcodeValidationInterceptor,
  geolocationInterceptor,
  backgroundSyncInterceptor,
  networkQualityInterceptor,
  setupLogisticsFeatures,
  offlineQueue,
} from './api-logistics.ts';

// Setup and configuration
export {
  setupApiClient,
  setAuthToken,
  getAuthToken,
  clearAuth,
} from './api-setup.ts';

// Re-export existing core modules
export { BaseElement } from './BaseElement.ts';
export { registerComponent, getComponent, getAllComponentTypes, ensureCoreRegistered } from './registry.ts';
export type { ComponentConstructor } from './registry.ts';

// Universal Datasource Adapters (NEW)
export type {
  DataSourceType,
  DataSourceAdapter,
  QueryParams as AdapterQueryParams,
  QueryResult,
  Filter,
  Sort,
  Aggregation,
  DatasourceCapabilities,
  ConnectionTestResult
} from './DataSourceAdapter';
export { BaseAdapter } from './DataSourceAdapter';

export { AdapterRegistry, adapterRegistry, registerAdapter } from './AdapterRegistry';
export { RestApiAdapter, JsonFileAdapter } from './adapters';
export type { RestApiConfig, JsonFileConfig } from './adapters';
export { registerBuiltInAdapters } from './adapter-bootstrap';

// Entity Schema Converter
export { EntitySchemaConverter } from './EntitySchemaConverter';
