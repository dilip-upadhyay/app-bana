-- =====================================================
-- Workflow Tables - Phase 1 Backend Implementation
-- =====================================================
-- Purpose: Store workflow definitions, runtime instances, and execution tokens
-- Architecture: Backend-first, ACID-compliant, supports versioning and maker-checker
-- Reference: docs/WORKFLOW_FEATURE_SPEC.md v2.0
-- =====================================================

-- =====================================================
-- Table 1: Workflow Definitions (Metadata + JSON)
-- =====================================================
-- Stores workflow templates with versioning support
-- Instances lock to specific version on creation (prevents breaking changes)
CREATE TABLE IF NOT EXISTS appbana_wf_definition (
    id VARCHAR(36) PRIMARY KEY,
    app_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    trigger_entity VARCHAR(100),          -- Entity name (e.g., "PaymentRequest", "LeaveRequest")
    trigger_event VARCHAR(50),            -- ON_CREATE | ON_UPDATE | ON_DELETE | MANUAL
    trigger_condition TEXT,               -- MVEL expression (e.g., "$${PaymentRequest.amount > 10000}")
    version INTEGER NOT NULL DEFAULT 1,   -- Auto-incremented on publish
    status VARCHAR(20) NOT NULL,          -- DRAFT | ACTIVE | ARCHIVED
    definition_json TEXT NOT NULL,        -- Full workflow JSON (nodes, transitions, assignment rules)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT uq_workflow_app_name_version UNIQUE (app_id, name, version)
);

-- Indexes for workflow definition queries
CREATE INDEX IF NOT EXISTS idx_wf_def_app_status ON appbana_wf_definition(app_id, status);
CREATE INDEX IF NOT EXISTS idx_wf_def_trigger ON appbana_wf_definition(trigger_entity, trigger_event);

-- =====================================================
-- Table 2: Workflow Instances (Runtime State)
-- =====================================================
-- Tracks each workflow execution (one per entity)
-- Locks to workflow version on creation (isolation from definition changes)
CREATE TABLE IF NOT EXISTS appbana_wf_instance (
    id VARCHAR(36) PRIMARY KEY,
    workflow_definition_id VARCHAR(36) NOT NULL,
    workflow_version INTEGER NOT NULL,    -- Locked version (never changes after creation)
    app_id VARCHAR(36) NOT NULL,
    entity_id VARCHAR(36) NOT NULL,       -- ID of entity that triggered workflow (e.g., PaymentRequest.id)
    entity_type VARCHAR(100) NOT NULL,    -- Entity name (denormalized for queries)
    status VARCHAR(20) NOT NULL,          -- RUNNING | COMPLETED | FAILED | CANCELLED
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    context_data TEXT,                    -- JSON snapshot of entity data at start
    created_by VARCHAR(255),
    CONSTRAINT fk_wf_instance_definition FOREIGN KEY (workflow_definition_id) 
        REFERENCES appbana_wf_definition(id) ON DELETE CASCADE
);

-- Indexes for instance queries
CREATE INDEX IF NOT EXISTS idx_wf_instance_entity ON appbana_wf_instance(entity_id, entity_type);
CREATE INDEX IF NOT EXISTS idx_wf_instance_status ON appbana_wf_instance(status);
CREATE INDEX IF NOT EXISTS idx_wf_instance_app ON appbana_wf_instance(app_id, status);
CREATE INDEX IF NOT EXISTS idx_wf_instance_def ON appbana_wf_instance(workflow_definition_id);

-- =====================================================
-- Table 3: Workflow Tokens (Execution Position Markers)
-- =====================================================
-- Represents current position(s) in workflow graph
-- USER_TASK tokens pause execution until user completes task
-- SERVICE_TASK tokens execute immediately and auto-transition
-- Multiple tokens support parallel execution (Phase 3)
CREATE TABLE IF NOT EXISTS appbana_wf_token (
    id VARCHAR(36) PRIMARY KEY,
    workflow_instance_id VARCHAR(36) NOT NULL,
    node_id VARCHAR(100) NOT NULL,        -- Current node in workflow graph
    node_type VARCHAR(50) NOT NULL,       -- USER_TASK | SERVICE_TASK | DECISION | WAIT | START | END
    status VARCHAR(20) NOT NULL,          -- ACTIVE | COMPLETED | FAILED | CANCELLED
    
    -- Assignment fields (for USER_TASK only)
    assignment_type VARCHAR(50),          -- USER | ROLE | QUEUE | DYNAMIC | null (for non-user tasks)
    assigned_user_id VARCHAR(36),         -- Specific user ID (null if unassigned or role/queue)
    assigned_role VARCHAR(100),           -- Role name (null if user-assigned)
    assigned_queue VARCHAR(100),          -- Queue name (null if user/role-assigned)
    
    -- SLA fields
    due_at TIMESTAMP,                     -- SLA deadline (null if no SLA)
    sla_status VARCHAR(20),               -- ON_TIME | OVERDUE | ESCALATED | null
    
    -- Execution metadata
    arrived_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    completed_by VARCHAR(255),            -- User who completed task (for audit)
    outcome VARCHAR(100),                 -- Transition taken (e.g., "APPROVE", "REJECT", "SEND_BACK")
    task_data TEXT,                       -- JSON data for task (form inputs, comments, etc.)
    
    CONSTRAINT fk_wf_token_instance FOREIGN KEY (workflow_instance_id) 
        REFERENCES appbana_wf_instance(id) ON DELETE CASCADE
);

-- Indexes for token queries (critical for performance)
CREATE INDEX IF NOT EXISTS idx_wf_token_instance ON appbana_wf_token(workflow_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_token_user ON appbana_wf_token(assigned_user_id, status);
CREATE INDEX IF NOT EXISTS idx_wf_token_role ON appbana_wf_token(assigned_role, status);
CREATE INDEX IF NOT EXISTS idx_wf_token_queue ON appbana_wf_token(assigned_queue, status);
CREATE INDEX IF NOT EXISTS idx_wf_token_sla ON appbana_wf_token(due_at, sla_status);
CREATE INDEX IF NOT EXISTS idx_wf_token_overdue ON appbana_wf_token(due_at);

-- =====================================================
-- Seed Data: Sample Workflow for Testing
-- =====================================================
-- Simple maker-checker workflow for PaymentRequest entity
INSERT INTO appbana_wf_definition (
    id, 
    app_id, 
    name, 
    description, 
    trigger_entity, 
    trigger_event, 
    trigger_condition,
    version, 
    status, 
    definition_json,
    created_by
) VALUES (
    'wf-payment-approval-001',
    'default-app',
    'Payment Approval Workflow',
    'Simple maker-checker workflow for payment requests over $10,000',
    'PaymentRequest',
    'ON_CREATE',
    '$${PaymentRequest.AMOUNT > 10000}',
    1,
    'ACTIVE',
    '{
        "id": "wf-payment-approval-001",
        "name": "Payment Approval Workflow",
        "nodes": {
            "start": {
                "id": "start",
                "type": "START",
                "label": "Start"
            },
            "review": {
                "id": "review",
                "type": "USER_TASK",
                "label": "Review Payment Request",
                "assignmentType": "DYNAMIC",
                "assignmentExpression": "$${PaymentRequest.createdBy.manager}",
                "slaHours": 24,
                "formFields": [
                    {"name": "comments", "type": "textarea", "required": false},
                    {"name": "decision", "type": "select", "options": ["APPROVE", "REJECT", "SEND_BACK"]}
                ]
            },
            "approved": {
                "id": "approved",
                "type": "SERVICE_TASK",
                "label": "Update Status to Approved",
                "serviceAction": "UPDATE_ENTITY",
                "entityType": "PaymentRequest",
                "updates": {"status": "APPROVED", "approvedAt": "$${NOW}"}
            },
            "rejected": {
                "id": "rejected",
                "type": "SERVICE_TASK",
                "label": "Update Status to Rejected",
                "serviceAction": "UPDATE_ENTITY",
                "entityType": "PaymentRequest",
                "updates": {"status": "REJECTED", "rejectedAt": "$${NOW}"}
            },
            "end": {
                "id": "end",
                "type": "END",
                "label": "End"
            }
        },
        "transitions": [
            {"from": "start", "to": "review", "condition": null},
            {"from": "review", "to": "approved", "condition": "$${outcome == ''APPROVE''}", "label": "Approve"},
            {"from": "review", "to": "rejected", "condition": "$${outcome == ''REJECT''}", "label": "Reject"},
            {"from": "review", "to": "start", "condition": "$${outcome == ''SEND_BACK''}", "label": "Send Back"},
            {"from": "approved", "to": "end", "condition": null},
            {"from": "rejected", "to": "end", "condition": null}
        ]
    }',
    'admin@appbana.com'
);

-- =====================================================
-- Views for Common Queries
-- =====================================================

-- View: Active user tasks with workflow context
CREATE VIEW IF NOT EXISTS v_my_active_tasks AS
SELECT 
    t.id AS token_id,
    t.node_id,
    t.assigned_user_id,
    t.assigned_role,
    t.assigned_queue,
    t.due_at,
    t.sla_status,
    t.arrived_at,
    i.id AS instance_id,
    i.entity_id,
    i.entity_type,
    i.context_data,
    d.name AS workflow_name,
    d.description AS workflow_description,
    d.app_id
FROM appbana_wf_token t
JOIN appbana_wf_instance i ON t.workflow_instance_id = i.id
JOIN appbana_wf_definition d ON i.workflow_definition_id = d.id
WHERE t.status = 'ACTIVE' 
  AND t.node_type = 'USER_TASK'
  AND i.status = 'RUNNING';

-- View: Workflow instance history with metadata
CREATE VIEW IF NOT EXISTS v_workflow_history AS
SELECT 
    i.id AS instance_id,
    i.entity_id,
    i.entity_type,
    i.status AS instance_status,
    i.started_at,
    i.completed_at,
    TIMESTAMPDIFF(MINUTE, i.started_at, COALESCE(i.completed_at, CURRENT_TIMESTAMP)) AS duration_minutes,
    d.name AS workflow_name,
    d.version AS workflow_version,
    d.app_id,
    COUNT(t.id) AS total_tokens,
    SUM(CASE WHEN t.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_tokens,
    SUM(CASE WHEN t.sla_status = 'OVERDUE' THEN 1 ELSE 0 END) AS overdue_tokens
FROM appbana_wf_instance i
JOIN appbana_wf_definition d ON i.workflow_definition_id = d.id
LEFT JOIN appbana_wf_token t ON i.id = t.workflow_instance_id
GROUP BY i.id, i.entity_id, i.entity_type, i.status, i.started_at, i.completed_at, 
         d.name, d.version, d.app_id;

-- =====================================================
-- Migration Complete
-- =====================================================
-- Tables: 3 (definition, instance, token)
-- Indexes: 15 (optimized for common queries)
-- Views: 2 (my tasks, workflow history)
-- Seed Data: 1 sample workflow (Payment Approval)
-- =====================================================
