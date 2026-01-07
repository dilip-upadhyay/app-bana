// Workflow validation utility

import { WorkflowMetadata, ValidationResult } from '../models/WorkflowMetadata';

export class WorkflowValidator {
    static validate(metadata: WorkflowMetadata): ValidationResult {
        const errors: string[] = [];
        const warnings: string[] = [];

        // Must have at least one START node
        const startNodes = metadata.nodes.filter(n => n.type === 'START');
        if (startNodes.length === 0) {
            errors.push('Workflow must have a START node');
        } else if (startNodes.length > 1) {
            errors.push('Workflow can only have one START node');
        }

        // Must have at least one END node
        const endNodes = metadata.nodes.filter(n => n.type === 'END');
        if (endNodes.length === 0) {
            errors.push('Workflow must have at least one END node');
        }

        // Check for disconnected nodes
        const connectedNodes = new Set<string>();
        metadata.connections.forEach(conn => {
            connectedNodes.add(conn.from);
            connectedNodes.add(conn.to);
        });

        metadata.nodes.forEach(node => {
            if (!connectedNodes.has(node.id) && node.type !== 'START' && node.type !== 'END') {
                warnings.push(`Node "${node.label}" is not connected`);
            }
        });

        // Validate USER_TASK nodes have assignment
        metadata.nodes.filter(n => n.type === 'USER_TASK').forEach(node => {
            if (!node.properties.assignedUserId &&
                !node.properties.assignedRole &&
                !node.properties.assignedQueue) {
                errors.push(`Task "${node.label}" has no assignment`);
            }
        });

        // Check for cycles (basic check)
        if (this.hasCycles(metadata)) {
            errors.push('Workflow contains circular dependencies');
        }

        return {
            valid: errors.length === 0,
            errors,
            warnings
        };
    }

    private static hasCycles(metadata: WorkflowMetadata): boolean {
        const visited = new Set<string>();
        const recursionStack = new Set<string>();

        const adjacencyList = new Map<string, string[]>();
        metadata.connections.forEach(conn => {
            if (!adjacencyList.has(conn.from)) {
                adjacencyList.set(conn.from, []);
            }
            adjacencyList.get(conn.from)!.push(conn.to);
        });

        const dfs = (nodeId: string): boolean => {
            visited.add(nodeId);
            recursionStack.add(nodeId);

            const neighbors = adjacencyList.get(nodeId) || [];
            for (const neighbor of neighbors) {
                if (!visited.has(neighbor)) {
                    if (dfs(neighbor)) return true;
                } else if (recursionStack.has(neighbor)) {
                    return true; // Cycle detected
                }
            }

            recursionStack.delete(nodeId);
            return false;
        };

        for (const node of metadata.nodes) {
            if (!visited.has(node.id)) {
                if (dfs(node.id)) return true;
            }
        }

        return false;
    }
}
