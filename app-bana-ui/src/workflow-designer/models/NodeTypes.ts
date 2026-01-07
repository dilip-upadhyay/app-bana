// Node type definitions and templates

import { NodeType, NodeTemplate } from './WorkflowMetadata';

export const NODE_TEMPLATES: NodeTemplate[] = [
    {
        type: 'START',
        label: 'Start',
        icon: '▶️',
        color: '#10b981',
        description: 'Workflow entry point'
    },
    {
        type: 'USER_TASK',
        label: 'User Task',
        icon: '👤',
        color: '#3b82f6',
        description: 'Assign task to user or role'
    },
    {
        type: 'SERVICE_TASK',
        label: 'Service Task',
        icon: '⚙️',
        color: '#8b5cf6',
        description: 'Automated action'
    },
    {
        type: 'DECISION',
        label: 'Decision',
        icon: '◆',
        color: '#f59e0b',
        description: 'Conditional routing'
    },
    {
        type: 'END',
        label: 'End',
        icon: '⏹',
        color: '#ef4444',
        description: 'Workflow completion'
    }
];

export function getNodeTemplate(type: NodeType): NodeTemplate | undefined {
    return NODE_TEMPLATES.find(t => t.type === type);
}

export function getNodeIcon(type: NodeType): string {
    return getNodeTemplate(type)?.icon || '❓';
}

export function getNodeColor(type: NodeType): string {
    return getNodeTemplate(type)?.color || '#64748b';
}
