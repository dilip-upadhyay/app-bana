// Utility to convert between canvas metadata and backend JSON format

import { WorkflowMetadata, NodeMetadata, ConnectionMetadata } from '../models/WorkflowMetadata';

export class JsonConverter {
    /**
     * Convert canvas metadata to backend workflow JSON
     * Removes position data and flattens structure
     */
    static toBackendJson(metadata: WorkflowMetadata): any {
        const nodes: Record<string, any> = {};

        // Convert nodes array to object, flatten properties
        metadata.nodes.forEach(node => {
            nodes[node.id] = {
                id: node.id,
                type: node.type,
                label: node.label,
                ...node.properties
            };
        });

        // Convert connections to transitions
        const transitions = metadata.connections.map(conn => ({
            from: conn.from,
            to: conn.to,
            condition: conn.condition || null,
            label: conn.label
        }));

        return {
            id: metadata.id,
            name: metadata.name,
            nodes,
            transitions
        };
    }

    /**
     * Convert backend JSON to canvas metadata
     * Adds position data using auto-layout
     */
    static fromBackendJson(json: any): WorkflowMetadata {
        const nodes: NodeMetadata[] = [];

        // Convert nodes object to array
        Object.entries(json.nodes || {}).forEach(([key, nodeData]: [string, any], index) => {
            const { id, type, label, ...properties } = nodeData;

            nodes.push({
                id,
                type,
                label,
                position: {
                    x: index * 250,
                    y: 100
                }, // Will be auto-laid out later
                properties
            });
        });

        // Convert transitions to connections
        const connections: ConnectionMetadata[] = (json.transitions || []).map((t: any, index: number) => ({
            id: `conn-${index}`,
            from: t.from,
            to: t.to,
            label: t.label,
            condition: t.condition
        }));

        return {
            id: json.id || '',
            name: json.name || 'Untitled Workflow',
            version: json.version || 1,
            schemaVersion: '1.0.0',
            triggerEntity: json.triggerEntity,
            triggerEvent: json.triggerEvent,
            triggerCondition: json.triggerCondition,
            nodes,
            connections
        };
    }
}
