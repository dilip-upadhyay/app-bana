/**
 * Healthcare & FHIR-Specific API Extensions
 * Supports AppBana's December 2025 Healthcare goals
 */

import { apiClient } from './api-client.ts';
import type { QueryParams } from './api-client.ts';

/**
 * FHIR Service for Healthcare Interoperability
 * Supports FHIR R4 resources with full audit trail
 */
export class FHIRService {
  private baseUrl: string = '';

  /**
   * Configure FHIR base URL
   */
  configure(baseUrl: string, authToken?: string) {
    this.baseUrl = baseUrl;
    if (authToken) {
      apiClient.setHeader('Authorization', `Bearer ${authToken}`);
    }
  }

  /**
   * Search FHIR resources with parameters
   */
  async search(resourceType: string, params: QueryParams = {}): Promise<any> {
    return apiClient.get(`${this.baseUrl}/${resourceType}`, params, {
      headers: {
        'Accept': 'application/fhir+json',
        'X-PHI-Access': 'true', // Mark as PHI access for audit
      },
    });
  }

  /**
   * Get specific FHIR resource by ID
   */
  async get(resourceType: string, id: string): Promise<any> {
    return apiClient.get(`${this.baseUrl}/${resourceType}/${id}`, undefined, {
      headers: {
        'Accept': 'application/fhir+json',
        'X-PHI-Access': 'true',
      },
    });
  }

  /**
   * Get patient demographics
   */
  async getPatient(patientId: string): Promise<any> {
    return this.get('Patient', patientId);
  }

  /**
   * Get patient observations (vitals, labs, etc.)
   */
  async getObservations(patientId: string, params: {
    category?: string;
    code?: string;
    date?: string;
  } = {}): Promise<any> {
    return this.search('Observation', {
      patient: patientId,
      ...params,
    });
  }

  /**
   * Get patient encounters (visits)
   */
  async getEncounters(patientId: string, params: {
    date?: string;
    status?: string;
  } = {}): Promise<any> {
    return this.search('Encounter', {
      patient: patientId,
      ...params,
    });
  }

  /**
   * Get patient medications
   */
  async getMedications(patientId: string): Promise<any> {
    return this.search('MedicationRequest', {
      patient: patientId,
    });
  }
}

/**
 * Workflow Service for Stateful Workflow Engine (October Goal)
 */
export class WorkflowService {
  /**
   * Get workflow definition
   */
  async getDefinition(workflowId: string): Promise<any> {
    return apiClient.get(`/api/workflow/definition/${workflowId}`);
  }

  /**
   * List all workflow definitions
   */
  async listDefinitions(): Promise<any[]> {
    return apiClient.get('/api/workflow/definitions');
  }

  /**
   * Create workflow instance
   */
  async createInstance(workflowId: string, data: any): Promise<any> {
    return apiClient.post('/api/workflow/instance', {
      workflowId,
      data,
    });
  }

  /**
   * Get workflow instance
   */
  async getInstance(instanceId: string): Promise<any> {
    return apiClient.get(`/api/workflow/instance/${instanceId}`);
  }

  /**
   * Execute workflow transition
   */
  async transition(instanceId: string, transition: string, data?: any): Promise<any> {
    return apiClient.post(`/api/workflow/instance/${instanceId}/transition`, {
      transition,
      data,
    });
  }

  /**
   * Get workflow history/audit trail
   */
  async getHistory(instanceId: string): Promise<any[]> {
    return apiClient.get(`/api/workflow/instance/${instanceId}/history`);
  }

  /**
   * Query workflow instances
   */
  async query(params: {
    workflowId?: string;
    state?: string;
    assignee?: string;
    limit?: number;
    offset?: number;
  } = {}): Promise<{ rows: any[]; total: number }> {
    return apiClient.get('/api/workflow/instances', params);
  }
}

/**
 * Plugin Service for Plugin Marketplace (December Goal)
 */
export class PluginService {
  /**
   * List available plugins in marketplace
   */
  async listMarketplace(): Promise<any[]> {
    return apiClient.get('/api/plugins/marketplace');
  }

  /**
   * Get plugin details
   */
  async getPlugin(pluginId: string): Promise<any> {
    return apiClient.get(`/api/plugins/${pluginId}`);
  }

  /**
   * Install plugin
   */
  async install(pluginId: string): Promise<any> {
    return apiClient.post(`/api/plugins/${pluginId}/install`);
  }

  /**
   * Uninstall plugin
   */
  async uninstall(pluginId: string): Promise<any> {
    return apiClient.delete(`/api/plugins/${pluginId}/install`);
  }

  /**
   * List installed plugins
   */
  async listInstalled(): Promise<any[]> {
    return apiClient.get('/api/plugins/installed');
  }

  /**
   * Verify plugin integrity (signed manifest check)
   */
  async verifyIntegrity(pluginId: string): Promise<{ valid: boolean; signature: string }> {
    return apiClient.get(`/api/plugins/${pluginId}/verify`);
  }
}

/**
 * Report Service for Reporting Engine (November Goal)
 */
export class ReportService {
  /**
   * List report definitions
   */
  async listDefinitions(): Promise<any[]> {
    return apiClient.get('/api/reports/definitions');
  }

  /**
   * Get report definition
   */
  async getDefinition(reportId: string): Promise<any> {
    return apiClient.get(`/api/reports/definition/${reportId}`);
  }

  /**
   * Save report definition
   */
  async saveDefinition(report: any): Promise<any> {
    return apiClient.post('/api/reports/definition', report);
  }

  /**
   * Generate report (CSV/Excel)
   */
  async generate(reportId: string, format: 'csv' | 'excel', params: any = {}): Promise<Blob> {
    return apiClient.request(`/api/reports/${reportId}/generate`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': format === 'csv' ? 'text/csv' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      },
      body: JSON.stringify({ format, params }),
    });
  }

  /**
   * Preview report data
   */
  async preview(reportId: string, params: any = {}): Promise<{ rows: any[]; columns: any[] }> {
    return apiClient.post(`/api/reports/${reportId}/preview`, params);
  }
}

/**
 * Realtime Service for WebSocket/MQTT Support (November Goal)
 */
export class RealtimeService {
  private connections: Map<string, WebSocket> = new Map();

  /**
   * Subscribe to entity changes
   */
  subscribe(entity: string, callback: (event: any) => void): () => void {
    const ws = new WebSocket(`${this.getWsUrl()}/realtime/${entity}`);

    ws.onmessage = (event) => {
      const data = JSON.parse(event.data);
      callback(data);
    };

    ws.onerror = (error) => {
      console.error(`[Realtime] Connection error for ${entity}:`, error);
    };

    const key = `entity:${entity}`;
    this.connections.set(key, ws);

    // Return unsubscribe function
    return () => {
      ws.close();
      this.connections.delete(key);
    };
  }

  /**
   * Subscribe to specific record changes
   */
  subscribeToRecord(entity: string, id: string, callback: (event: any) => void): () => void {
    const ws = new WebSocket(`${this.getWsUrl()}/realtime/${entity}/${id}`);

    ws.onmessage = (event) => {
      const data = JSON.parse(event.data);
      callback(data);
    };

    const key = `record:${entity}:${id}`;
    this.connections.set(key, ws);

    return () => {
      ws.close();
      this.connections.delete(key);
    };
  }

  /**
   * Close all connections
   */
  disconnect(): void {
    this.connections.forEach(ws => ws.close());
    this.connections.clear();
  }

  private getWsUrl(): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}`;
  }
}

// Create singleton instances
export const fhirService = new FHIRService();
export const workflowService = new WorkflowService();
export const pluginService = new PluginService();
export const reportService = new ReportService();
export const realtimeService = new RealtimeService();

// Export extended API with all services
export const extendedApi = {
  fhir: fhirService,
  workflow: workflowService,
  plugin: pluginService,
  report: reportService,
  realtime: realtimeService,
};

