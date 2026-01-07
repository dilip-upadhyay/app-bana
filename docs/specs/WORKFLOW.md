# AppBana Workflow Automation - Architecture & Implementation Plan

**Version 2.0** | Last Updated: December 6, 2025

---

## Executive Summary

AppBana's **Workflow Automation** transforms the platform from a data-entry tool into a **process orchestration engine**. This document incorporates comprehensive architectural feedback to ensure production-grade reliability, scalability, and alignment with AppBana's metadata-driven philosophy.

### Core Principles

1. **Metadata-Driven**: Workflows stored as versioned JSON files
2. **ACID-Compliant Runtime**: Workflow state persisted in SQL for reliability
3. **Event-Driven Architecture**: Automatic triggers on entity CRUD operations
4. **Human + System Tasks**: Support both manual approvals and automated actions
5. **Deep Integration**: Seamless connection to AppBana's Pages, Entities, and AI Builder

---

## Critical Architectural Decisions

### ✅ Task Type Differentiation (Phase 1 - Critical)

**Problem**: Original spec grouped everything as "Standard State", causing confusion between tasks that wait for humans vs. tasks that execute immediately.

**Solution**: Explicit task types in data model:
- **USER_TASK**: Workflow pauses, waits for human input (e.g., "Manager Approval")
- **SERVICE_TASK**: Executes instantly, non-blocking (e.g., "Update Status", "Send Email")
- **WAIT_STATE**: Time-based pause (e.g., "Wait 2 days before escalation")
- **DECISION**: Conditional router (XOR gateway)
- **START/END**: Entry and exit points

**Impact**: Engine knows which tasks to persist and which to execute immediately, preventing deadlocks.

---

### ✅ Workflow Versioning (Phase 1 - Critical)

**Problem**: Deferred to Phase 5, but running workflows will break when definitions change.

**Solution**: Version locking from Day 1:
- Each workflow has `version` field (integer, auto-incremented)
- Running instances store `workflowVersionId` (immutable)
- Instances complete on their original version
- New instances use latest ACTIVE version
- UI shows version selector: "Deploy v2" vs "Keep v1 running"

**Impact**: Zero downtime deployments, no data corruption.

---

### ✅ Assignment Logic (Phase 1 - Critical)

**Problem**: "Roles" attached to transitions, but assignment is a state-level concern.

**Solution**: Assignment configuration on USER_TASK:
```typescript
assignment: {
  type: 'USER' | 'ROLE' | 'QUEUE' | 'DYNAMIC';
  value: string; // User ID, Role name, or expression like "${Order.owner.managerId}"
}
```

**Examples**:
- Static: `{ type: 'USER', value: 'user-123' }`
- Role-based: `{ type: 'ROLE', value: 'Manager' }`
- Dynamic: `{ type: 'DYNAMIC', value: '${Order.createdBy.manager}' }`
- Queue: `{ type: 'QUEUE', value: 'finance-team' }` (Phase 2: requires claim mechanism)

---

### ✅ SLA & Timeouts (Phase 2 - High Priority)

**Problem**: No handling for stalled workflows.

**Solution**: Timeout configuration on USER_TASK:
```typescript
timeout: {
  duration: string;        // '24h', '3d', '1w'
  action: 'ESCALATE' | 'AUTO_TRANSITION' | 'NOTIFY';
  targetNodeId?: string;   // For escalation
  notifyUsers?: string[];  // For reminders
}
```

**Backend**: Java `ScheduledExecutorService` polls `APPBANA_WF_TOKEN` table every 5 minutes, triggers actions on expired tasks.

---

### ✅ Parallel Execution (Phase 3 - Medium Priority)

**Problem**: Current design assumes XOR (choose one path). Real workflows need AND (do both).

**Solution**: 
- **Phase 1**: XOR only (Decision nodes)
- **Phase 3**: Add `PARALLEL_GATEWAY` (Fork) and `JOIN_GATEWAY` (Wait for all)

**Example Use Case**: "Onboarding needs IT setup AND HR setup simultaneously"

---

### ✅ Draft vs. Published Workflows (Phase 1 - Critical)

**Problem**: Direct editing of active workflows corrupts running instances.

**Solution**: Workflow status field:
- `DRAFT`: Editable, not executable
- `ACTIVE`: Read-only, used by runtime
- `ARCHIVED`: Historical, hidden from UI

**UI Flow**: Edit Draft → Click "Publish" → Creates new version → Activates v2

---

## Key Capabilities

### Visual Process Design
- **Drag & Drop Interface**: Salesforce Flow-inspired canvas
- **Task Type Palette**: User Task, Service Task, Decision, Wait, Start/End
- **Auto-Layout**: Hierarchical topological sort algorithm
- **Zoom & Pan**: 0.1x - 3x with mouse wheel
- **Minimap**: Overview navigator for complex workflows

### Multi-Entity Orchestration
- **Cross-Entity Conditions**: `Order.amount > Customer.creditLimit`
- **Related Entity Detection**: Parse foreign keys to prioritize related entities
- **Entity Context Per State**: Each task operates on a specific entity
- **Example**: Order workflow can read Customer data for approval logic

### Intelligent Decision Making
- **Expression-Based Rules**: Java expression parser (MVEL or SpEL)
- **Priority-Based Evaluation**: Top-to-bottom rule checking
- **Mandatory ELSE Path**: UI enforces fallback to prevent orphan tokens
- **Natural Language Preview**: "When Order amount is greater than 50000"

### Dynamic Actions
- **onEntry Actions**: Execute when entering a state (e.g., "Send notification")
- **onExit Actions**: Execute when leaving a state (e.g., "Update status field")
- **Service Task Types**: Update Record, Send Email, Webhook
- **Audit Integration**: All transitions logged to `appbana_audit`

---

## Data Models (Revised Architecture)

### 1. Workflow Metadata (`WorkflowMeta`) - JSON Storage

Stored in: `apps/{appId}/workflows/{workflowId}.json`

```typescript
export interface WorkflowMeta {
  id: string;                    // "order_approval_workflow"
  appId: string;                 // Parent app ID
  name: string;                  // "Order Approval Process"
  version: number;               // Auto-incremented on publish (1, 2, 3...)
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED';
  
  // Trigger: How workflow starts
  trigger: {
    entityId: string;            // "Order"
    event: 'ON_CREATE' | 'ON_UPDATE' | 'ON_DELETE' | 'MANUAL';
    condition?: string;          // Optional: "amount > 0"
  };
  
  // Primary entity for the workflow
  entities: {
    primary: string;             // "Order"
    available: string[];         // ["Order", "Customer", "Approver"]
  };
  
  // Workflow graph
  nodes: WorkflowNode[];
  transitions: WorkflowTransition[];
  
  // Metadata
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}
```

### 2. Workflow Nodes (Discriminated Union)

```typescript
export type WorkflowNode = 
  | StartNode 
  | EndNode 
  | UserTaskNode 
  | ServiceTaskNode 
  | DecisionNode 
  | WaitNode;

// Base interface
interface BaseNode {
  id: string;
  name: string;
  position?: { x: number; y: number };
  color?: string;
}

// ========== USER TASK (Human Interaction) ==========
export interface UserTaskNode extends BaseNode {
  type: 'USER_TASK';
  
  // Entity context
  entityContext: string;         // "Order"
  
  // Assignment: WHO does this task?
  assignment: {
    type: 'USER' | 'ROLE' | 'QUEUE' | 'DYNAMIC';
    value: string;               // User ID, Role name, or "${Order.owner.managerId}"
  };
  
  // UI: WHAT page to show?
  pageConfig: {
    mode: 'AUTO_GENERATE' | 'EXISTING_PAGE' | 'NONE';
    pageId?: string;             // If EXISTING_PAGE
    formMode?: 'CREATE' | 'EDIT' | 'VIEW'; // If AUTO_GENERATE
    fieldsToShow?: string[];     // If AUTO_GENERATE
  };
  
  // Actions: WHAT to do on entry/exit?
  onEntry?: Action[];            // e.g., [{ type: 'SEND_EMAIL', to: '${assignee}' }]
  onExit?: Action[];             // e.g., [{ type: 'UPDATE_FIELD', field: 'status', value: 'In Progress' }]
  
  // SLA: WHEN to escalate?
  timeout?: {
    duration: string;            // "24h", "3d", "1w"
    action: 'ESCALATE' | 'AUTO_TRANSITION' | 'NOTIFY';
    targetNodeId?: string;       // For ESCALATE
    notifyUsers?: string[];      // For NOTIFY
  };
}

// ========== SERVICE TASK (Automated Action) ==========
export interface ServiceTaskNode extends BaseNode {
  type: 'SERVICE_TASK';
  
  entityContext: string;         // "Order"
  
  // What to execute?
  action: {
    type: 'UPDATE_RECORD' | 'SEND_EMAIL' | 'WEBHOOK' | 'SCRIPT';
    config: Record<string, any>;
  };
  
  // Examples:
  // UPDATE_RECORD: { field: 'status', value: 'Approved' }
  // SEND_EMAIL: { to: '${Order.owner.email}', template: 'approval_notification' }
  // WEBHOOK: { url: 'https://api.example.com/notify', method: 'POST', body: {...} }
}

// ========== DECISION (Conditional Router) ==========
export interface DecisionNode extends BaseNode {
  type: 'DECISION';
  
  entityContext: string;         // "Order"
  
  // Outgoing paths (evaluated top-to-bottom)
  branches: DecisionBranch[];
}

export interface DecisionBranch {
  id: string;
  label: string;                 // "High Amount"
  priority: number;              // Lower = higher priority (1, 2, 3...)
  condition: string | 'ELSE';    // "amount > 50000" or "ELSE"
  targetNodeId: string;          // Next node ID
  
  // Cross-entity support
  crossEntity?: {
    leftEntity: string;          // "Order"
    leftField: string;           // "amount"
    operator: ConditionOperator;
    rightEntity: string;         // "Customer"
    rightField: string;          // "creditLimit"
  };
}

// ========== WAIT STATE (Time-based Pause) ==========
export interface WaitNode extends BaseNode {
  type: 'WAIT';
  
  duration: string;              // "2h", "1d", "3d"
  
  onTimeout?: {
    targetNodeId: string;        // Where to go after waiting
  };
}

// ========== START / END ==========
export interface StartNode extends BaseNode {
  type: 'START';
}

export interface EndNode extends BaseNode {
  type: 'END';
  
  // Final action (optional)
  outcome?: 'SUCCESS' | 'CANCELLED' | 'REJECTED';
  onEntry?: Action[];            // e.g., Update final status
}
```

### 3. Supporting Types

```typescript
// Actions executed on state entry/exit
export interface Action {
  type: 'UPDATE_FIELD' | 'SEND_EMAIL' | 'WEBHOOK' | 'CREATE_RECORD';
  config: Record<string, any>;
}

// Transitions (visual connections)
export interface WorkflowTransition {
  id: string;
  sourceNodeId: string;
  targetNodeId: string;
  label?: string;                // Optional label for the arrow
}

// Condition operators
export type ConditionOperator = 
  | 'EQUALS' 
  | 'NOT_EQUALS' 
  | 'GREATER_THAN' 
  | 'LESS_THAN' 
  | 'GREATER_THAN_OR_EQUAL' 
  | 'LESS_THAN_OR_EQUAL' 
  | 'CONTAINS' 
  | 'NOT_CONTAINS' 
  | 'STARTS_WITH' 
  | 'ENDS_WITH' 
  | 'IS_EMPTY' 
  | 'IS_NOT_EMPTY' 
  | 'IN' 
  | 'NOT_IN';
```

### 4. Runtime Data Model (SQL Tables)

#### Table: `APPBANA_WF_DEFINITION`
Stores workflow metadata (redundant with JSON files for query performance).

| Column | Type | Description |
|--------|------|-------------|
| `id` | VARCHAR(255) | PK, workflow ID |
| `app_id` | VARCHAR(255) | FK to app |
| `name` | VARCHAR(500) | Display name |
| `version` | INTEGER | Version number |
| `status` | VARCHAR(20) | DRAFT, ACTIVE, ARCHIVED |
| `trigger_entity` | VARCHAR(255) | Entity ID |
| `trigger_event` | VARCHAR(20) | ON_CREATE, ON_UPDATE, MANUAL |
| `definition_json` | CLOB/TEXT | Full JSON blob |
| `created_at` | TIMESTAMP | Creation time |
| `created_by` | VARCHAR(255) | User ID |

**Indexes**: `(app_id, status)`, `(trigger_entity, trigger_event)`

#### Table: `APPBANA_WF_INSTANCE`
Stores running workflow processes.

| Column | Type | Description |
|--------|------|-------------|
| `instance_id` | VARCHAR(36) | PK, UUID |
| `workflow_id` | VARCHAR(255) | FK to definition |
| `workflow_version` | INTEGER | **CRITICAL: Version lock** |
| `entity_id` | VARCHAR(255) | Business record ID (e.g., Order ID) |
| `entity_type` | VARCHAR(255) | Entity name (e.g., "Order") |
| `status` | VARCHAR(20) | RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED |
| `current_node_id` | VARCHAR(255) | Current state (for single-token workflows) |
| `created_at` | TIMESTAMP | Start time |
| `completed_at` | TIMESTAMP | End time |
| `context_data` | CLOB/TEXT | JSON for variables (Phase 2) |

**Indexes**: `(entity_id, entity_type)`, `(workflow_id, status)`, `(status, created_at)`

#### Table: `APPBANA_WF_TOKEN`
Stores individual task assignments (supports parallel execution in Phase 3).

| Column | Type | Description |
|--------|------|-------------|
| `token_id` | VARCHAR(36) | PK, UUID |
| `instance_id` | VARCHAR(36) | FK to instance |
| `node_id` | VARCHAR(255) | Current node ID |
| `node_type` | VARCHAR(20) | USER_TASK, WAIT, SERVICE_TASK |
| `assigned_user_id` | VARCHAR(255) | Assignee (if USER_TASK) |
| `assigned_at` | TIMESTAMP | Assignment time |
| `due_at` | TIMESTAMP | Deadline (for SLA) |
| `status` | VARCHAR(20) | ACTIVE, COMPLETED, ESCALATED |
| `payload` | CLOB/TEXT | Task-specific data (JSON) |

**Indexes**: `(instance_id)`, `(assigned_user_id, status)`, `(due_at)`

---

## Business Use Cases

### Order Approval Workflow
Automatically route orders to the right approver based on amount:
- **Under $10,000**: Auto-approved
- **$10,000 - $50,000**: Manager approval required
- **Over $50,000**: Senior management approval required

### Credit Check Process
Route loan applications based on customer creditworthiness:
- **High Credit Score (>750)**: Fast-track approval
- **Medium Credit (650-750)**: Standard review process
- **Low Credit (<650)**: Enhanced verification required

### Multi-Level Approvals
Create sophisticated approval chains:
- Draft → Submit → Department Head → Finance → CFO → Approved

---

## Workflow Components

| Component | Description | Use Case |
|-----------|-------------|----------|
| **Start State** | Workflow begins here | New order created |
| **Standard State** | User interaction or system processing | Order draft, Manager approval |
| **Decision Node** | Routes based on data conditions | Amount > $50K? |
| **End State** | Workflow completion | Order approved |

---

## How It Works

**Example: Order Approval Process**

```
Workflow: "Order Approval Process"
├─ Primary Entity: Order
├─ Available Entities: [Order, Customer, Approver]
│
├─ State: Draft
│  ├─ Entity: Order
│  ├─ Form: Auto-generate
│  └─ Fields: [customerName, amount, items]
│
├─ Decision: Amount Check ◆
│  └─ Conditions:
│     ├─ IF Order.amount > 50000 → Senior Approval
│     ├─ IF Order.amount > 10000 → Manager Approval
│     └─ ELSE → Auto Approved
│
├─ State: Manager Approval
│  ├─ Entity: Order
│  └─ Form: ApprovalForm
│
└─ End: Approved
```

**The Result**: Orders automatically flow through the right approval path based on your business rules - no coding required.

---

## Decision Logic Examples

Compare values across entities for sophisticated routing:

```javascript
// Credit limit check
IF Order.amount > Customer.creditLimit
   THEN Senior_Approval
   ELSE Auto_Approved

// Manager availability
IF Approver.isAvailable = true AND Order.priority = 'High'
   THEN Immediate_Review
   ELSE Queue_For_Review
```

---

## Backend Architecture & API

### 1. Workflow Engine (`WorkflowEngine.java`)

Core execution engine integrated into `ApiServer.java`.

**Key Responsibilities**:
1. **Trigger Detection**: Listen to entity CRUD operations via PostOperationHooks
2. **Instance Creation**: Create workflow instances when triggers match
3. **State Transition**: Move tokens from node to node
4. **Condition Evaluation**: Parse and evaluate expressions (MVEL/SpEL)
5. **Action Execution**: Execute onEntry/onExit actions
6. **Audit Logging**: Log all transitions to `appbana_audit`

**Core Methods**:
```java
public class WorkflowEngine {
    // Create new workflow instance
    public WorkflowInstance startWorkflow(
        String workflowId, 
        String entityId, 
        Map<String, Object> entityData
    );
    
    // Evaluate if workflow should start (trigger condition)
    public boolean shouldTrigger(
        WorkflowMeta workflow, 
        String event, 
        Map<String, Object> entityData
    );
    
    // Advance workflow to next node
    public void transition(
        String instanceId, 
        String tokenId, 
        String outcome, 
        Map<String, Object> userData
    );
    
    // Evaluate decision branches
    public String evaluateDecision(
        DecisionNode decision, 
        Map<String, Object> entityData,
        Map<String, Map<String, Object>> relatedEntities
    );
    
    // Execute actions (onEntry/onExit)
    public void executeActions(
        List<Action> actions, 
        Map<String, Object> context
    );
    
    // Fetch related entity data for cross-entity conditions
    public Map<String, Object> fetchRelatedEntity(
        String entityType, 
        String relatedEntityId
    );
}
```

### 2. Expression Evaluator

Use **MVEL** (Lightweight) or **Spring Expression Language** (SpEL) for condition parsing.

**Example with MVEL**:
```java
import org.mvel2.MVEL;

public class ExpressionEvaluator {
    public boolean evaluate(String expression, Map<String, Object> context) {
        try {
            Object result = MVEL.eval(expression, context);
            return result instanceof Boolean ? (Boolean) result : false;
        } catch (Exception e) {
            logger.error("Expression evaluation failed: {}", expression, e);
            return false;
        }
    }
}

// Usage:
// expression: "amount > 5000 && status == 'Pending'"
// context: { "amount": 10000, "status": "Pending" }
// result: true
```

**Cross-Entity Expression**:
```java
// expression: "Order.amount > Customer.creditLimit"
Map<String, Object> context = Map.of(
    "Order", orderData,
    "Customer", customerData
);
evaluate("Order.amount > Customer.creditLimit", context);
```

### 3. PostOperationHook Integration

Modify `ApiServer.java` to trigger workflows on entity changes:

```java
// In handleEntityCreate() method
private void handleEntityCreate(HttpExchange exchange, String entityName) throws IOException {
    // Existing CRUD logic...
    String entityId = createdRecord.get("id").toString();
    Map<String, Object> entityData = createdRecord;
    
    // NEW: Workflow trigger
    workflowEngine.checkAndStartWorkflows(
        entityName, 
        "ON_CREATE", 
        entityId, 
        entityData
    );
    
    // Continue with response...
}
```

**checkAndStartWorkflows() Implementation**:
```java
public void checkAndStartWorkflows(
    String entityType, 
    String event, 
    String entityId, 
    Map<String, Object> entityData
) {
    // Find all ACTIVE workflows for this entity+event
    List<WorkflowMeta> workflows = workflowStorage.findByTrigger(entityType, event);
    
    for (WorkflowMeta workflow : workflows) {
        // Check trigger condition (if any)
        if (workflow.getTrigger().getCondition() != null) {
            boolean conditionMet = expressionEvaluator.evaluate(
                workflow.getTrigger().getCondition(), 
                entityData
            );
            if (!conditionMet) continue;
        }
        
        // Start workflow
        WorkflowInstance instance = startWorkflow(workflow.getId(), entityId, entityData);
        logger.info("Started workflow {} for entity {}", workflow.getName(), entityId);
    }
}
```

### 4. Scheduler for Timeouts/SLA

Use Java's `ScheduledExecutorService` to poll for overdue tasks:

```java
public class WorkflowScheduler {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public void start() {
        // Check every 5 minutes
        scheduler.scheduleAtFixedRate(
            this::checkTimeouts, 
            0, 
            5, 
            TimeUnit.MINUTES
        );
    }
    
    private void checkTimeouts() {
        // Find all tokens with due_at < NOW and status = ACTIVE
        List<WorkflowToken> overdueTokens = tokenRepository.findOverdue(Instant.now());
        
        for (WorkflowToken token : overdueTokens) {
            WorkflowNode node = getNodeById(token.getNodeId());
            
            if (node instanceof UserTaskNode userTask) {
                handleTaskTimeout(token, userTask.getTimeout());
            } else if (node instanceof WaitNode waitNode) {
                // Auto-transition after wait duration
                transition(token.getInstanceId(), token.getId(), "TIMEOUT", Map.of());
            }
        }
    }
    
    private void handleTaskTimeout(WorkflowToken token, TimeoutConfig timeout) {
        switch (timeout.getAction()) {
            case ESCALATE:
                // Move to escalation node
                transition(token.getInstanceId(), token.getId(), "ESCALATE", Map.of());
                break;
            case NOTIFY:
                // Send reminder notification
                notificationService.sendReminder(token.getAssignedUserId(), token);
                // Extend due date
                token.setDueAt(Instant.now().plus(Duration.parse(timeout.getDuration())));
                break;
            case AUTO_TRANSITION:
                // Force transition with default outcome
                transition(token.getInstanceId(), token.getId(), "AUTO", Map.of());
                break;
        }
    }
}
```

### 5. REST API Endpoints

#### **Workflow Definition Management** (Studio)

```
GET    /api/workflows?appId={appId}
       → List all workflows for app (all versions)

GET    /api/workflows/{workflowId}?version={version}
       → Get specific workflow definition (defaults to latest)

POST   /api/workflows
       → Create new workflow (starts as DRAFT, version 1)

PUT    /api/workflows/{workflowId}
       → Update workflow (only if status=DRAFT)

POST   /api/workflows/{workflowId}/publish
       → Publish workflow (DRAFT → ACTIVE, increment version)

DELETE /api/workflows/{workflowId}?version={version}
       → Archive workflow version (ACTIVE → ARCHIVED)
```

#### **Workflow Runtime** (Execution)

```
POST   /api/workflows/{workflowId}/start
       Body: { "entityId": "order-123" }
       → Manually start workflow instance

GET    /api/my-tasks
       Query: status=ACTIVE&limit=50
       → Get current user's pending tasks (USER_TASK tokens)

GET    /api/my-tasks/{tokenId}
       → Get task details + linked page

POST   /api/my-tasks/{tokenId}/complete
       Body: { "outcome": "Approve", "data": {...} }
       → Complete task, advance workflow

GET    /api/workflow-instances?entityId={entityId}
       → Get all workflow instances for an entity

GET    /api/workflow-instances/{instanceId}
       → Get instance status + current node
```

#### **Admin/Monitoring**

```
GET    /api/admin/workflows/instances?status=RUNNING
       → List all running instances (admin only)

GET    /api/admin/workflows/{workflowId}/metrics
       → Performance metrics (avg time per state, bottlenecks)

POST   /api/admin/workflows/instances/{instanceId}/cancel
       → Emergency workflow termination
```

### 6. Flyway Migration (SQL Schema)

**File**: `V3__workflow_tables.sql`

```sql
-- Workflow definitions
CREATE TABLE IF NOT EXISTS appbana_wf_definition (
    id VARCHAR(255) PRIMARY KEY,
    app_id VARCHAR(255) NOT NULL,
    name VARCHAR(500) NOT NULL,
    version INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL, -- DRAFT, ACTIVE, ARCHIVED
    trigger_entity VARCHAR(255),
    trigger_event VARCHAR(20),
    definition_json CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    UNIQUE(id, version)
);

CREATE INDEX IF NOT EXISTS idx_wf_def_app_status 
ON appbana_wf_definition(app_id, status);

CREATE INDEX IF NOT EXISTS idx_wf_def_trigger 
ON appbana_wf_definition(trigger_entity, trigger_event, status);

-- Workflow instances
CREATE TABLE IF NOT EXISTS appbana_wf_instance (
    instance_id VARCHAR(36) PRIMARY KEY,
    workflow_id VARCHAR(255) NOT NULL,
    workflow_version INTEGER NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    entity_type VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL, -- RUNNING, PAUSED, COMPLETED, FAILED
    current_node_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    context_data CLOB,
    FOREIGN KEY (workflow_id, workflow_version) 
        REFERENCES appbana_wf_definition(id, version)
);

CREATE INDEX IF NOT EXISTS idx_wf_inst_entity 
ON appbana_wf_instance(entity_id, entity_type);

CREATE INDEX IF NOT EXISTS idx_wf_inst_status 
ON appbana_wf_instance(status, created_at);

-- Workflow tokens (tasks)
CREATE TABLE IF NOT EXISTS appbana_wf_token (
    token_id VARCHAR(36) PRIMARY KEY,
    instance_id VARCHAR(36) NOT NULL,
    node_id VARCHAR(255) NOT NULL,
    node_type VARCHAR(20) NOT NULL,
    assigned_user_id VARCHAR(255),
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    due_at TIMESTAMP,
    status VARCHAR(20) NOT NULL, -- ACTIVE, COMPLETED, ESCALATED
    payload CLOB,
    FOREIGN KEY (instance_id) REFERENCES appbana_wf_instance(instance_id)
);

CREATE INDEX IF NOT EXISTS idx_wf_token_instance 
ON appbana_wf_token(instance_id);

CREATE INDEX IF NOT EXISTS idx_wf_token_assignee 
ON appbana_wf_token(assigned_user_id, status);

CREATE INDEX IF NOT EXISTS idx_wf_token_due 
ON appbana_wf_token(due_at) WHERE status = 'ACTIVE';
```

---

## Visual Designer Interface

### Drag & Drop Canvas

```
┌─────────────────────────────────────────────────────────────────┐
│ 🎨 Workflow Designer: Order Approval Process                    │
│ [◀ Palette] [➕ State] [🔀 Transition] [🎯 Auto Layout]         │
│ [🗺️ Minimap] [💾 Save]                                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────┐   ┌──────────────────────────────────┐  ┌───────┐│
│  │ Element  │   │         Canvas                    │  │ Props ││
│  │ Palette  │   │                                   │  │ Panel ││
│  │          │   │  [Start] → [Draft] → ◆ → [End]   │  │       ││
│  │ 📦 State │   │                                   │  │ ⚙️    ││
│  │ ◆ Decis. │   │  [Zoom: 100%]  [Minimap]         │  │ State ││
│  │ ▶ Start  │   │                                   │  │ Props ││
│  │ ◉ End    │   │                                   │  │       ││
│  └──────────┘   └──────────────────────────────────┘  └───────┘│
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Key Features:**
- **Element Palette**: Drag states and decisions onto the canvas
- **Properties Panel**: Configure each element with a simple form
- **Zoom & Pan**: Navigate large workflows easily
- **Auto-Layout**: One click to organize your entire workflow
- **Minimap**: Birds-eye view for quick navigation

### State Configuration

```
┌─────────────────────────────────┐
│ ⚙️ State Properties             │
├─────────────────────────────────┤
│ State Name                      │
│ [Manager Approval          ]    │
│                                 │
│ Entity Context                  │
│ [Order                   ▼]     │
│                                 │
│ Form Configuration              │
│ ○ No Form                       │
│ ● Auto-generate                 │
│ ○ Use Existing Page             │
│                                 │
│ Mode: [Edit            ▼]       │
│                                 │
│ Fields to Display:              │
│ ☑ amount                        │
│ ☑ customerName                  │
│ ☑ status                        │
│                                 │
│ Color Theme: [#667eea]          │
└─────────────────────────────────┘
```

### Decision Configuration

```
┌─────────────────────────────────┐
│ ◆ Decision: Amount Check        │
├─────────────────────────────────┤
│ Outgoing Paths (3)              │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ 1️⃣ High Amount              │ │
│ │ → Target: Senior Approval   │ │
│ │                             │ │
│ │ Entity: Order               │ │
│ │ Field: amount               │ │
│ │ Condition: > 50000          │ │
│ │                             │ │
│ │ "When Order.amount is       │ │
│ │  greater than 50000"        │ │
│ └─────────────────────────────┘ │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ 2️⃣ Medium Amount            │ │
│ │ → Manager Approval          │ │
│ │ Condition: > 10000          │ │
│ └─────────────────────────────┘ │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ ⭐ ELSE (Default Path)       │ │
│ │ → Auto Approved             │ │
│ └─────────────────────────────┘ │
│                                 │
│ [➕ Add Path]                   │
└─────────────────────────────────┘
```

---

## Benefits

### For Business Users
✅ **No Coding Required**: Visual interface for everyone  
✅ **Instant Updates**: Change processes without IT involvement  
✅ **Clear Visualization**: See your entire process at a glance  
✅ **Rapid Prototyping**: Test business logic in minutes  

### For Organizations
✅ **Faster Time-to-Market**: Deploy new processes in hours, not weeks  
✅ **Reduced Costs**: 90% less development time vs custom code  
✅ **Improved Compliance**: Enforce business rules automatically  
✅ **Better Insights**: Track process bottlenecks and metrics  

---

## Example Workflows

### Simple Order Approval
```
START (Draft) 
  → DECISION (Amount > 10K?)
     ├─ YES → MANAGER_APPROVAL → END (Approved)
     └─ NO → END (Auto-Approved)
```

### Complex Loan Approval
```
START (Application)
  → DECISION (Credit Score?)
     ├─ Score > 750 → AUTO_APPROVED
     ├─ Score > 650 → DECISION (Income?)
     │                 ├─ Income > 100K → MANAGER
     │                 └─ Income ≤ 100K → SENIOR
     └─ Score ≤ 650 → REJECTED
```

### Multi-Entity Customer Onboarding
```
START (New Customer)
  → VERIFY_DOCUMENTS
  → DECISION (Documents Complete?)
     ├─ YES → CREATE_ACCOUNT
              → SEND_WELCOME_EMAIL
              → END (Active)
     └─ NO → REQUEST_MORE_DOCS
             → VERIFY_DOCUMENTS (loop)
```

---

## Supported Operations

### Condition Operators

| Operator | Use Case | Example |
|----------|----------|---------|
| `equals` | Exact match | `status = 'Pending'` |
| `greaterThan` | Numeric/Date comparison | `amount > 10000` |
| `lessThan` | Threshold check | `age < 65` |
| `contains` | Text search | `notes contains 'urgent'` |
| `isEmpty` | Missing data | `assignedTo is empty` |
| `in` | Multiple values | `status in ['Pending', 'Review']` |

---

## Getting Started

### Step 1: Create Your First Workflow
1. Click **"New Workflow"** in the designer
2. Name your workflow (e.g., "Order Approval")
3. Select primary entity (e.g., "Order")

### Step 2: Add States
1. Drag **Start** state onto canvas
2. Add **Standard States** for each approval step
3. Add **Decision** nodes for routing logic
4. Add **End** state for completion

### Step 3: Configure Decisions
1. Click on a Decision node
2. Click **"Add Path"** for each route
3. Select entity, field, and condition
4. Choose target state
5. Add **ELSE** path as fallback

### Step 4: Configure Forms
1. Click on a State
2. Choose form type (Auto-generate or Existing)
3. Select fields to display
4. Set form mode (Create/Edit/View)

### Step 5: Save & Test
1. Click **"Auto Layout"** to organize
2. Click **"Save Workflow"**
3. Test with sample data

---

## Success Stories

### E-Commerce Company
**Challenge**: Manual order approval taking 2-3 days  
**Solution**: Automated workflow with amount-based routing  
**Result**: Approval time reduced to 2 hours, 95% accuracy  

### Financial Services
**Challenge**: Complex loan approval with 15+ decision points  
**Solution**: Multi-level workflow with cross-entity conditions  
**Result**: 60% faster processing, consistent compliance  

### Healthcare Provider
**Challenge**: Patient referral process involving 5 departments  
**Solution**: Visual workflow with automated notifications  
**Result**: 80% reduction in missed referrals  

---

## Technical Highlights

- **Performance**: Support for workflows with 100+ states
- **Scale**: Handle thousands of concurrent workflow executions
- **Integration**: Works seamlessly with existing AppBana entities
- **Mobile-Ready**: Responsive design for all devices
- **Cloud-Native**: Built for modern cloud infrastructure

---

## Next Steps

**Ready to automate your business processes?**

1. **Schedule a Demo**: See the workflow designer in action
2. **Try It Yourself**: Start with our interactive tutorial
3. **Talk to an Expert**: Discuss your specific use case

---

**Contact Us**  
Email: support@appbana.com  
Website: www.appbana.com  

---

*Document Version: 1.0*  
*Last Updated: December 6, 2025*

---

## Key Capabilities

### Visual Process Design
- **Drag & Drop Interface**: Build workflows visually with an intuitive canvas
- **Smart Element Palette**: Choose from States, Decisions, Start/End points
- **Auto-Layout**: Automatically organize complex workflows
- **Zoom & Pan**: Navigate large processes with ease

### Multi-Entity Orchestration
- **Cross-Entity Logic**: Create workflows spanning multiple data entities
- **Smart Routing**: Route records based on data conditions across entities
- **Example**: Route orders based on both order amount AND customer credit limit

### Intelligent Decision Making
- **Conditional Branching**: Route workflows based on business rules
- **Cross-Entity Comparisons**: Compare values across related entities
- **Priority-Based Evaluation**: Control the order of condition checking
- **Fallback Paths**: Define default routes when no condition matches

### Dynamic Form Integration
- **Auto-Generated Forms**: Forms created automatically from your data structure
- **Existing Page Integration**: Use your pre-built forms
- **Field-Level Control**: Choose exactly which fields to show in each step

---

## Business Use Cases

### Order Approval Workflow
Automatically route orders to the right approver based on amount:
- **Under $10,000**: Auto-approved
- **$10,000 - $50,000**: Manager approval required
- **Over $50,000**: Senior management approval required

### Credit Check Process
Route loan applications based on customer creditworthiness:
- **High Credit Score (>750)**: Fast-track approval
- **Medium Credit (650-750)**: Standard review process
- **Low Credit (<650)**: Enhanced verification required

### Multi-Level Approvals
Create sophisticated approval chains:
- Draft → Submit → Department Head → Finance → CFO → Approved

---

## Workflow Components

| Component | Type | Description | Use Case |
|-----------|------|-------------|----------|
| **Start State** | Entry Point | Workflow begins here | New order created |
| **Standard State** | Action Node | User interaction or system processing | Order draft, Manager approval |
| **Decision Node** | Conditional Router | Routes based on data conditions | Amount > $50K? |
| **End State** | Terminal | Workflow completion | Order approved |
| **Archive State** | Terminal | Record archived | Order cancelled |

---

## How It Works

```
Workflow: "Order Approval Process"
├─ Primary Entity: Order
├─ Available Entities: [Order, Customer, Approver, Product]
│
├─ State: Draft
│  ├─ Entity Context: Order
│  ├─ Form: Auto-generate (Create mode)
│  └─ Fields: [customerName, amount, items]
│
├─ Decision: Amount Check ◆
│  ├─ Entity Context: Order
│  └─ Conditions:
│     ├─ IF Order.amount > 50000 → Senior Approval
│     ├─ IF Order.amount > 10000 → Manager Approval
│     └─ ELSE → Auto Approved
│
├─ State: Manager Approval
│  ├─ Entity Context: Order
│  ├─ Related Entity: Approver
│  └─ Form: Existing (ApprovalForm)
│
└─ End: Approved
   └─ Entity Context: Order
```

**The Result**: Orders automatically flow through the right approval path based on your business rules - no coding required.

---

## Decision Logic Examples

Support for comparing fields across related entities:

```javascript
// Example: Credit limit check
IF Order.amount > Customer.creditLimit
   THEN Senior_Approval
   ELSE Auto_Approved

// Example: Manager availability
IF Approver.isAvailable = true AND Order.priority = 'High'
   THEN Immediate_Review
   ELSE Queue_For_Review
```

---

## Implementation Roadmap (Revised)

### 🔴 Phase 1: Backend Engine + Data Model (Weeks 1-2) - CRITICAL

**Goal**: Production-ready workflow execution engine with no UI

**Backend Tasks**:
1. **Database Schema** (V3__workflow_tables.sql)
   - `appbana_wf_definition` table
   - `appbana_wf_instance` table
   - `appbana_wf_token` table
   - Indexes for performance

2. **Core Engine** (`WorkflowEngine.java`)
   - `startWorkflow()` - Create instances
   - `transition()` - Move between nodes
   - `evaluateDecision()` - Branch logic
   - `executeActions()` - onEntry/onExit actions

3. **Expression Evaluator** (`ExpressionEvaluator.java`)
   - MVEL integration
   - Cross-entity context support
   - Error handling

4. **PostOperationHooks** (modify `ApiServer.java`)
   - Hook into handleEntityCreate/Update/Delete
   - Auto-start workflows on triggers
   - checkAndStartWorkflows() implementation

5. **Storage Layer** (`WorkflowStorage.java`)
   - Save/load WorkflowMeta JSON
   - Query by trigger (entity + event)
   - Version management

6. **REST API Endpoints**:
   - POST `/api/workflows` - Create workflow
   - GET `/api/workflows/{id}` - Get definition
   - POST `/api/workflows/{id}/publish` - Publish (DRAFT → ACTIVE)
   - POST `/api/workflows/{id}/start` - Manual start
   - GET `/api/my-tasks` - User task inbox
   - POST `/api/my-tasks/{tokenId}/complete` - Complete task

**Frontend Tasks** (Minimal):
- Update `workflow.ts` with new type definitions (UserTaskNode, ServiceTaskNode, etc.)
- Create `WorkflowMeta` TypeScript interfaces matching Java

**Testing**:
- Unit tests: Expression evaluation, decision logic
- Integration tests: API → Create workflow → Trigger → Complete task
- Manual test: Postman/PowerShell scripts

**Success Criteria**:
- ✅ Can POST workflow JSON to `/api/workflows`
- ✅ Creating an Order auto-starts workflow
- ✅ GET `/api/my-tasks` returns pending tasks
- ✅ POST `/api/my-tasks/{id}/complete` advances workflow

---

### 🟡 Phase 2: Visual Designer (Weeks 3-4)

**Goal**: Drag-and-drop workflow builder UI

**Frontend Tasks**:
1. **Refactor BuilderCanvas** for workflow mode
   - `mode` prop: `'page'` | `'workflow'`
   - Render workflow nodes instead of UI components
   - SVG line rendering for transitions

2. **Element Palette** (`WorkflowPalette.ts`)
   - Drag User Task, Service Task, Decision, Wait, Start, End
   - Type icons and labels

3. **Properties Panels**:
   - **UserTaskNode Properties**:
     - Entity selector
     - Assignment config (User/Role/Dynamic)
     - Page config (None/Auto/Existing)
     - Timeout/SLA config
     - onEntry/onExit actions
   - **ServiceTaskNode Properties**:
     - Action type dropdown
     - Config JSON editor
   - **DecisionNode Properties**:
     - Outgoing paths list
     - Per-path condition builder
     - Priority ordering (drag-drop)
     - ELSE path toggle
   - **WaitNode Properties**:
     - Duration input
     - Target node selector

4. **Workflow Manager Tab** (Studio)
   - List all workflows
   - Version history
   - Publish/Archive buttons
   - Status badges (DRAFT/ACTIVE/ARCHIVED)

5. **Condition Builder** (`ConditionBuilder.ts`)
   - Entity selector
   - Field selector (from schema)
   - Operator dropdown
   - Value input OR cross-entity field selector
   - Natural language preview

**Success Criteria**:
- ✅ Create workflow visually
- ✅ Add User Task with assignment
- ✅ Add Decision with 2+ branches
- ✅ Save workflow JSON (matches Phase 1 schema)
- ✅ Publish workflow (DRAFT → ACTIVE)

---

### 🟡 Phase 3: Runtime Integration (Weeks 5-6)

**Goal**: End-user task management and execution

**Frontend Tasks**:
1. **My Tasks Inbox** (`MyTasksInbox.ts`)
   - List assigned tasks
   - Filters: By entity, by workflow, by due date
   - Task cards with:
     - Entity icon
     - Workflow name
     - Task name
     - Due date (with urgency indicator)
     - Quick actions (Open, Claim)

2. **Task Detail Page** (`TaskDetailPage.ts`)
   - Load task token from API
   - Fetch linked PageMeta (if exists)
   - Render form (Auto-generate or Existing)
   - Action buttons (Approve, Reject, custom outcomes)
   - Comment/history section

3. **AppRuntimeShell Integration**:
   - Task notification badge
   - "My Tasks" menu item
   - Check for active tasks on record open
   - Override default view with workflow page

4. **Workflow Instance Viewer** (Admin):
   - Visual flow with current state highlighted
   - Timeline of transitions
   - Token details
   - Metrics (time in each state)

**Backend Tasks**:
1. **WorkflowScheduler.java**:
   - Background thread for SLA monitoring
   - Check every 5 minutes
   - Handle ESCALATE/NOTIFY/AUTO_TRANSITION

2. **Audit Integration**:
   - Log all transitions to `appbana_audit`
   - Format: `{ action: 'WORKFLOW_TRANSITION', from: 'Draft', to: 'Approval' }`

**Success Criteria**:
- ✅ User sees pending tasks in inbox
- ✅ Clicking task opens correct form
- ✅ Completing task advances workflow
- ✅ SLA escalations trigger automatically
- ✅ All transitions logged to audit

---

### 🟢 Phase 4: Advanced Features (Weeks 7-8)

**Goal**: Parallel execution, advanced actions, AI integration

**Features**:
1. **Parallel Gateway (Fork/Join)**:
   - New node types: `PARALLEL_FORK`, `PARALLEL_JOIN`
   - Multiple simultaneous tokens per instance
   - Join waits for all incoming tokens

2. **Advanced Actions**:
   - Send Email (with templates)
   - Webhook (HTTP POST/PUT/GET)
   - Create/Update Record (cross-entity)
   - Script execution (sandboxed)

3. **Queue Assignment**:
   - Task pools for teams
   - Claim mechanism (one user takes ownership)
   - Load balancing

4. **AI Builder Integration** (`AiChatBuilder.ts`):
   - Intent: `CREATE_WORKFLOW`
   - Prompt: "Create expense approval workflow for amounts over $500"
   - Generate `WorkflowMeta` JSON
   - Insert into canvas

5. **Workflow Templates**:
   - Pre-built workflows (Loan Approval, Expense Reimbursement, etc.)
   - Template gallery
   - One-click install

**Success Criteria**:
- ✅ Fork workflow into 2 parallel paths, join later
- ✅ Send email action works
- ✅ AI chat generates workflow from description
- ✅ Install template from gallery

---

### 🟣 Phase 5: Performance & Polish (Week 9)

**Goal**: Production hardening

**Tasks**:
1. **Validation**:
   - UI: Prevent orphan nodes
   - UI: Enforce ELSE path on decisions
   - API: Validate workflow JSON schema
   - API: Prevent circular loops (max depth check)

2. **Performance**:
   - Index optimization (SQL)
   - Workflow definition caching (5-minute TTL)
   - Lazy load workflow graphs (>100 nodes)

3. **Error Handling**:
   - Expression evaluation errors → log, skip transition
   - Missing related entity → graceful degradation
   - Workflow crashes → mark instance as FAILED

4. **Documentation**:
   - User guide: Creating first workflow
   - Developer guide: Adding custom actions
   - API reference: OpenAPI spec

5. **Testing**:
   - Load testing: 1000 concurrent instances
   - Stress testing: 500-node workflows
   - Security: Permission checks on task completion

**Success Criteria**:
- ✅ 10,000+ active instances with <100ms query time
- ✅ 500-node workflow loads in <2 seconds
- ✅ All API endpoints documented in OpenAPI
- ✅ Zero data loss on server crash (ACID compliance)

---

## Visual Designer Interface

#### 1.1 Visual Designer
- ✅ Salesforce Flow-inspired canvas with drag-drop
- ✅ Element palette (States, Decisions, Start/End)
- ✅ Node types: Standard (rectangle), Decision (diamond), Start/End (circles)
- ✅ Zoom controls (0.1x - 3x) with mouse wheel
- ✅ Pan canvas by dragging background
- ✅ Auto-layout algorithm (hierarchical topological sort)
- ✅ Minimap for navigation
- ✅ Collapsible properties panel

#### 1.2 Multi-Entity Support
- ⏳ Workflow-level entity management
  - Primary entity selection
  - Available entities list
  - Add/remove entities
- ⏳ State-level entity context
  - Entity dropdown per state
  - Related entities prioritized at top
  - Any entity in app available
- ⏳ Visual indicators
  - Entity badge on state cards
  - Color-coding by entity

#### 1.3 Form Configuration
- ⏳ Three form modes per state:
  - **None**: System state (no UI)
  - **Auto-generate**: Create form from entity schema
  - **Existing**: Select pre-built page
- ⏳ Auto-generate options:
  - Mode: Create / Edit / View
  - Field selection (checkboxes)
  - Field ordering
- ⏳ Existing page options:
  - Dropdown of all pages
  - Filter by entity type

#### 1.4 Decision Logic
- ⏳ Decision node properties panel
- ⏳ "Outgoing Paths" management
- ⏳ Per-path configuration:
  - Target state (dropdown)
  - Entity selection
  - Field selection (from entity schema)
  - Operator selection (>, <, =, etc.)
  - Value input
  - Natural language preview
  - Priority ordering (drag-to-reorder)
- ⏳ ELSE/Fallback path (one per decision)

#### 1.5 Cross-Entity Conditions
- ⏳ Simple conditions: `Order.amount > 10000`
- ⏳ Cross-entity conditions: `Order.amount > Customer.creditLimit`
- ⏳ Condition builder UI:
  - Left entity + field dropdown
  - Operator dropdown
  - Right side: Value OR Entity + field
  - Toggle between value/entity comparison

#### 1.6 Backward Compatibility
- ⏳ Legacy workflows without entities remain functional
- ⏳ Optional entity context (graceful degradation)
- ⏳ Migration helper: Detect entity from workflow name

---

## UI Specifications

### Workflow Designer Layout

```
┌─────────────────────────────────────────────────────────────────┐
│ 🎨 Workflow Designer: Order Approval Process                    │
│ [◀ Palette] [➕ State] [🔀 Transition] [🎯 Auto Layout]         │
│ [🗺️ Minimap] [💾 Save]                                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────┐   ┌──────────────────────────────────┐  ┌───────┐│
│  │ Element  │   │         Canvas                    │  │ Props ││
│  │ Palette  │   │                                   │  │ Panel ││
│  │          │   │  [Start] → [Draft] → ◆ → [End]   │  │       ││
│  │ 📦 State │   │                                   │  │ ⚙️    ││
│  │ ◆ Decis. │   │  [Zoom: 100%]  [Minimap]         │  │ State ││
│  │ ▶ Start  │   │                                   │  │ Props ││
│  │ ◉ End    │   │                                   │  │       ││
│  └──────────┘   └──────────────────────────────────┘  └───────┘│
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Properties Panel: Standard State

```
┌─────────────────────────────────┐
│ ⚙️ State Properties        [◀]  │
├─────────────────────────────────┤
│ State Name                      │
│ [Manager Approval          ]    │
│                                 │
│ Node Type                       │
│ [📦 Standard State       ▼]     │
│                                 │
│ 📦 Entity Context               │
│ [Order                   ▼]     │
│   • Order (Primary)             │
│   • Customer (Related)          │
│   ─────────────────────         │
│   • Approver                    │
│   • Product                     │
│                                 │
│ 📝 Form Configuration           │
│ ○ No Form                       │
│ ● Auto-generate                 │
│ ○ Use Existing Page             │
│                                 │
│ [Auto-generate selected]        │
│ Mode: [Edit            ▼]       │
│                                 │
│ Fields to Display:              │
│ ☑ amount                        │
│ ☑ customerName                  │
│ ☑ status                        │
│ ☐ internalNotes                 │
│ ☐ createdDate                   │
│                                 │
│ Color Theme                     │
│ [#667eea]                       │
│                                 │
│ ☐ Set as Initial State ⭐       │
│                                 │
│ [🗑️ Delete State]               │
└─────────────────────────────────┘
```

### Properties Panel: Decision Node

```
┌─────────────────────────────────┐
│ ◆ Decision Properties      [◀]  │
├─────────────────────────────────┤
│ Decision Name                   │
│ [Amount Check              ]    │
│                                 │
│ 📦 Entity Context               │
│ [Order                   ▼]     │
│                                 │
│ 📍 Outgoing Paths (3)           │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ 1️⃣ High Amount              │ │
│ │ Priority: 1                 │ │
│ │                             │ │
│ │ → Target: Senior Approval   │ │
│ │                             │ │
│ │ 📦 Entity: Order            │ │
│ │ 📊 Field: [amount     ▼]    │ │
│ │ ⚖️ Operator: [>       ▼]    │ │
│ │ 💰 Value: [50000      ]     │ │
│ │                             │ │
│ │ Natural Language:           │ │
│ │ "When Order.amount is       │ │
│ │  greater than 50000"        │ │
│ │                             │ │
│ │ [Edit] [Delete] [⬆] [⬇]    │ │
│ └─────────────────────────────┘ │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ 2️⃣ Medium Amount            │ │
│ │ ... (similar structure)     │ │
│ └─────────────────────────────┘ │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ ⭐ ELSE (Fallback)           │ │
│ │ → Auto Approved             │ │
│ │ (Always matches if no       │ │
│ │  other condition is true)   │ │
│ └─────────────────────────────┘ │
│                                 │
│ [➕ Add Path]                   │
└─────────────────────────────────┘
```

### Cross-Entity Condition Builder

```
┌─────────────────────────────────┐
│ 🔗 Cross-Entity Condition       │
├─────────────────────────────────┤
│ Compare:                        │
│                                 │
│ 📦 [Order          ▼]           │
│ 📊 [amount         ▼]           │
│                                 │
│ ⚖️ Operator: [>    ▼]           │
│                                 │
│ To:                             │
│ ○ Fixed Value                   │
│ ● Another Entity Field          │
│                                 │
│ 📦 [Customer       ▼]           │
│ 📊 [creditLimit    ▼]           │
│                                 │
│ Natural Language:               │
│ "When Order.amount is greater   │
│  than Customer.creditLimit"     │
│                                 │
│ [Apply] [Cancel]                │
└─────────────────────────────────┘
```

---

## Data Model

### StateMachine Interface

```typescript
interface StateMachine {
  id: string;
  name: string;
  
  // Multi-entity support
  entities?: {
    primary?: string;      // "Order"
    available?: string[];  // ["Order", "Customer", "Approver"]
  };
  
  // Legacy (backward compatibility)
  entityName?: string;
  
  states: State[];
  transitions: Transition[];
  initialState: string;
  statusField?: string;
}
```

### State Interface

```typescript
interface State {
  id: string;
  name: string;
  type?: 'state' | 'decision' | 'start' | 'end';
  color?: string;
  position?: { x: number; y: number };
  
  // Entity context
  entityContext?: string;
  
  // Form configuration
  formConfig?: {
    type: 'auto' | 'existing' | 'none';
    pageId?: string;           // If type='existing'
    mode?: 'create' | 'edit' | 'view';  // If type='auto'
    fieldsToShow?: string[];   // If type='auto'
  };
}
```

### Transition Interface

```typescript
interface Transition {
  id: string;
  from: string;  // state id
  to: string;    // state id
  label?: string;
  priority?: number;
  isFallback?: boolean;
  
  condition?: {
    entity?: string;
    field?: string;
    operator?: ConditionOperator;
    value?: any;
    naturalLanguage?: string;
    
    // Cross-entity support
    crossEntity?: {
      leftEntity: string;
      leftField: string;
      operator: ConditionOperator;
      rightEntity: string;
      rightField: string;
    };
  };
  
  roles?: string[];
}
```

---

## User Stories

### Story 1: Create Simple Approval Workflow
**As a** business user  
**I want to** create an order approval workflow  
**So that** orders are automatically routed based on amount  

**Acceptance Criteria:**
- Create workflow named "Order Approval"
- Set Order as primary entity
- Add Draft state with auto-generated form
- Add Amount Check decision with 3 paths:
  - `amount > 50000` → Senior Approval
  - `amount > 10000` → Manager Approval
  - ELSE → Auto Approved
- Save and test workflow

### Story 2: Configure State Forms
**As a** business user  
**I want to** configure which form appears in each state  
**So that** users see appropriate data entry screens  

**Acceptance Criteria:**
- Select state "Manager Approval"
- Choose form type: Auto-generate
- Set mode: Edit
- Select fields: amount, customerName, status
- Preview shows selected fields only

### Story 3: Cross-Entity Condition
**As a** business user  
**I want to** compare fields across entities  
**So that** I can route based on customer credit limit  

**Acceptance Criteria:**
- Add decision "Credit Check"
- Set entity: Order
- Create condition: Order.amount > Customer.creditLimit
- Select Customer entity from related entities
- Select creditLimit field
- Natural language shows: "When Order amount is greater than Customer credit limit"

### Story 4: Visualize Workflow
**As a** business user  
**I want to** see my workflow visually  
**So that** I understand the process flow  

**Acceptance Criteria:**
- States show as rectangles with entity badges
- Decisions show as diamonds
- Arrows show condition summaries
- Can zoom 50% to 300%
- Can pan by dragging
- Minimap shows entire workflow

### Story 5: Auto-Layout Complex Workflow
**As a** business user  
**I want to** automatically organize my workflow  
**So that** I don't manually position 50 states  

**Acceptance Criteria:**
- Click "Auto Layout" button
- System arranges states in layers
- Start state at top
- End state at bottom
- Transitions flow left-to-right, top-to-bottom
- No overlapping states

---

## Technical Architecture

### Component Structure

```
StateMachineDesigner (Main Component)
├─ ElementPalette
│  ├─ StateItem (draggable)
│  ├─ DecisionItem (draggable)
│  ├─ StartItem (draggable)
│  └─ EndItem (draggable)
│
├─ Canvas
│  ├─ SVGLayer (transitions/arrows)
│  ├─ StateNodes
│  │  ├─ StandardState
│  │  ├─ DecisionState (diamond)
│  │  ├─ StartState (circle)
│  │  └─ EndState (circle)
│  ├─ ZoomControls
│  └─ Minimap
│
└─ PropertiesPanel (collapsible)
   ├─ StateProperties
   │  ├─ EntitySelector
   │  ├─ FormConfigurator
   │  └─ ColorPicker
   │
   ├─ DecisionProperties
   │  ├─ OutgoingPathsList
   │  └─ PathEditor
   │     ├─ EntitySelector
   │     ├─ FieldSelector
   │     ├─ OperatorSelector
   │     ├─ ValueInput
   │     └─ CrossEntityToggle
   │
   └─ TransitionsList
```

### Services

```typescript
// WorkflowStorage.ts
class WorkflowStorage {
  saveStateMachine(machine: StateMachine): Promise<void>
  getStateMachine(id: string): Promise<StateMachine>
  getStateMachineByEntity(entityName: string): Promise<StateMachine>
}

// WorkflowEngine.ts
class WorkflowEngine {
  canTransition(machine: StateMachine, from: string, to: string, record: any): boolean
  executeTransition(machine: StateMachine, transition: Transition, record: any): Promise<void>
  evaluateCondition(condition: TransitionCondition, record: any, relatedRecords?: Map<string, any>): boolean
}

// EntitySchemaService.ts (NEW)
class EntitySchemaService {
  getAvailableEntities(appId: string): Promise<string[]>
  getEntityFields(entityName: string): Promise<Field[]>
  getRelatedEntities(entityName: string): Promise<string[]>
}
```

---

## Implementation Phases

### ✅ Phase 0: Visual Foundation (COMPLETE)
- Salesforce Flow UI
- Element palette
- Zoom/pan controls
- Auto-layout
- Minimap
- Collapsible panels

### ⏳ Phase 1: Entity-Aware Foundation (IN PROGRESS)
**Sprint Goal:** Basic entity integration  
**Duration:** 1 week  
**Features:**
- Workflow-level entity management
- State entity context selector
- Form configuration (auto/existing/none)
- Entity badge on state cards
- Update data model

### 📋 Phase 2: Decision Logic (NEXT)
**Sprint Goal:** Full decision support  
**Duration:** 2 weeks  
**Features:**
- Decision properties panel
- Outgoing paths management
- Condition builder with entity awareness
- Priority ordering (drag-drop)
- ELSE/fallback path
- Visual condition summaries on arrows

### 📋 Phase 3: Cross-Entity Conditions
**Sprint Goal:** Multi-entity orchestration  
**Duration:** 1 week  
**Features:**
- Cross-entity condition builder
- Related entity detection
- Entity relationship visualization
- Complex condition preview

### 📋 Phase 4: Runtime Execution
**Sprint Goal:** Execute workflows on live data  
**Duration:** 2 weeks  
**Features:**
- WorkflowEngine integration with CRUD operations
- Fetch related entity data at runtime
- Evaluate cross-entity conditions
- State transition execution
- Form rendering per state config

### 📋 Phase 5: Testing & Polish
**Sprint Goal:** Production-ready  
**Duration:** 1 week  
**Features:**
- Validation (no orphan states, no missing conditions)
- Error handling
- Workflow testing with sample data
- Performance optimization
- Documentation

---

## Success Metrics

### User Experience
- **Time to create workflow:** < 10 minutes for simple approval
- **Workflow comprehension:** Users understand flow without documentation
- **Error rate:** < 5% validation errors on save

### Technical
- **Performance:** Support workflows with 100+ states
- **Zoom/Pan:** 60 FPS rendering
- **Load time:** < 500ms for complex workflows

### Business Value
- **Workflow adoption:** 80% of apps use workflows
- **Automation coverage:** 70% of manual approvals automated
- **Development time:** 90% reduction vs custom code

---

## Open Questions & Decisions

### 1. **Entity Relationship Discovery**
**Question:** How do we detect related entities?  
**Options:**
- A) Parse foreign key fields (e.g., `customerId` → Customer entity)
- B) Manual configuration (user specifies relationships)
- C) AI-powered suggestion based on field names

**Decision:** Phase 1: Option B (manual), Phase 3: Option A (auto-detect)

### 2. **Form Field Ordering**
**Question:** How do users control field order in auto-generated forms?  
**Options:**
- A) Drag-drop field list
- B) Use entity schema order
- C) Alphabetical

**Decision:** Phase 1: Option B, Phase 4: Option A

### 3. **Condition Validation**
**Question:** Should we validate conditions at design time?  
**Options:**
- A) Real-time validation with warnings
- B) Validation on save
- C) No validation (catch at runtime)

**Decision:** Phase 2: Option A (real-time)

### 4. **Multi-Path Decisions**
**Question:** Can decision have 10+ outgoing paths?  
**Options:**
- A) Unlimited paths
- B) Limit to 10 paths
- C) Recommend using nested decisions

**Decision:** Phase 1: Option A (unlimited), UI warns if > 10

### 5. **Workflow Versioning**
**Question:** How to handle workflow changes in production?  
**Options:**
- A) Immediate: Changes apply to all records
- B) Versioned: New version created, old records use old version
- C) Gradual: User chooses when to migrate

**Decision:** Phase 5: Option B (versioning)

---

## Risks & Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Complex workflows cause UI performance issues | High | Medium | Virtualization, lazy rendering, SVG optimization |
| Cross-entity conditions fail at runtime | High | Medium | Validation at design time, error handling |
| Users confused by multi-entity workflows | Medium | High | Tooltips, examples, AI assistant guidance |
| Backward compatibility breaks existing workflows | High | Low | Thorough testing, migration script, optional entities |
| Form auto-generation doesn't match needs | Medium | Medium | Hybrid approach: auto-generate as starting point, allow customization |

---

## Dependencies

### External
- Entity schema API (AppManager)
- Page/Form registry (for existing page selection)
- User/Role service (for role-based routing)

### Internal
- WorkflowStorage (localStorage → database migration)
- WorkflowEngine (condition evaluation)
- ConditionBuilder component (existing, needs entity awareness)

---

## Future Enhancements (Post-MVP)

1. **Parallel Paths**: Multiple simultaneous approvers
2. **Subflows**: Reusable workflow components
3. **Scheduled Actions**: Time-based triggers (e.g., "7 days after submission")
4. **Wait States**: Pause workflow until external event
5. **Loop States**: Iterate over list of items
6. **Workflow Templates**: Pre-built industry workflows (e.g., "Loan Approval", "Expense Reimbursement")
7. **AI Workflow Generation**: Natural language → workflow ("Create an approval flow for orders over $10K")
8. **Workflow Analytics**: Track bottlenecks, average time in each state
9. **Mobile Workflow Designer**: Touch-optimized interface
10. **Workflow Marketplace**: Share/download workflows from community

---

## Appendix

### A. Supported Condition Operators

| Operator | Types | Example |
|----------|-------|---------|
| `equals` | All | `status = 'Pending'` |
| `notEquals` | All | `status != 'Cancelled'` |
| `greaterThan` | Number, Date | `amount > 10000` |
| `lessThan` | Number, Date | `createdDate < '2025-01-01'` |
| `greaterThanOrEqual` | Number, Date | `priority >= 3` |
| `lessThanOrEqual` | Number, Date | `age <= 65` |
| `contains` | String | `notes contains 'urgent'` |
| `notContains` | String | `email not contains 'spam'` |
| `startsWith` | String | `phone startsWith '+1'` |
| `endsWith` | String | `domain endsWith '.com'` |
| `isEmpty` | All | `description is empty` |
| `isNotEmpty` | All | `assignedTo is not empty` |
| `in` | All | `status in ['Pending', 'Review']` |
| `notIn` | All | `category not in ['Test', 'Demo']` |

### B. Example Workflows

#### Simple Order Approval
```
START (Draft) 
  → DECISION (Amount > 10K?)
     ├─ YES → MANAGER_APPROVAL → END (Approved)
     └─ NO → END (Auto-Approved)
```

#### Complex Loan Approval
```
START (Application)
  → DECISION (Credit Score?)
     ├─ Score > 750 → AUTO_APPROVED
     ├─ Score > 650 → DECISION (Income?)
     │                 ├─ Income > 100K → MANAGER_APPROVAL
     │                 └─ Income <= 100K → SENIOR_APPROVAL
     └─ Score <= 650 → REJECTED
```

#### Multi-Entity Customer Onboarding
```
START (New Customer)
  [Entity: Customer]
  → VERIFY_DOCUMENTS
     [Entity: Document]
  → DECISION (Documents Complete?)
     ├─ YES → CREATE_ACCOUNT
              [Entity: Account]
              → SEND_WELCOME_EMAIL
              → END (Active)
     └─ NO → REQUEST_MORE_DOCS
             → VERIFY_DOCUMENTS (loop)
```

```

---

## Summary: Key Improvements from Architectural Review

### Critical Changes Implemented

1. **Task Type Differentiation** ✅
   - Replaced generic "Standard State" with explicit types: USER_TASK, SERVICE_TASK, WAIT
   - Engine now knows which tasks to persist vs execute immediately
   - Prevents workflow deadlocks

2. **Workflow Versioning** ✅
   - Moved from Phase 5 to Phase 1 (backend engine)
   - Instances lock to specific version on creation
   - Zero-downtime deployments now possible
   - No data corruption when workflows are updated

3. **Assignment Logic** ✅
   - Moved from transitions to USER_TASK nodes
   - Support for 4 assignment types: User, Role, Queue, Dynamic
   - Dynamic expressions: `${Order.owner.managerId}`

4. **SLA & Timeouts** ✅
   - Added timeout configuration to USER_TASK nodes
   - Three escalation actions: ESCALATE, AUTO_TRANSITION, NOTIFY
   - Background scheduler polls every 5 minutes

5. **Backend-First Approach** ✅
   - Phase 1 now builds headless engine BEFORE UI
   - Can test via API/Postman before building designer
   - Reduces risk of UI building features backend can't support

6. **ACID-Compliant Runtime** ✅
   - Workflow state stored in SQL tables, not just JSON
   - Server crash recovery via database persistence
   - Transactional state transitions

7. **Audit Integration** ✅
   - All workflow transitions logged to `appbana_audit`
   - Compliance-ready (SOC2, HIPAA tracking)

8. **Expression-Based Conditions** ✅
   - MVEL integration for complex business rules
   - Cross-entity support: `Order.amount > Customer.creditLimit`
   - No hard-coded operators, full flexibility

### Architecture Alignment with AppBana

1. **Metadata Storage**:
   - Workflow definitions: JSON files in `apps/{appId}/workflows/`
   - Runtime state: SQL tables (`appbana_wf_*`)
   - Follows same pattern as Pages/Entities

2. **Event-Driven Integration**:
   - PostOperationHooks listen to entity CRUD
   - Auto-start workflows on entity creation/update
   - Seamless integration with existing ApiServer

3. **UI Reuse**:
   - BuilderCanvas refactored for dual mode (page/workflow)
   - Properties panel pattern consistent with page builder
   - Same drag-drop UX philosophy

4. **AI Builder Integration**:
   - New intent: `CREATE_WORKFLOW`
   - Generates WorkflowMeta JSON from natural language
   - Consistent with existing AI capabilities

### Roadmap Changes

**Before (Original Plan)**:
- Phase 1: Visual Foundation
- Phase 2: Decision Logic
- Phase 3: Cross-Entity
- Phase 4: Runtime Execution ❌ Too late!

**After (Revised Plan)**:
- **Phase 1: Backend Engine** (Weeks 1-2) 🔴
  - Database schema, WorkflowEngine, PostOperationHooks, REST API
  - Can execute workflows via API before UI exists
- **Phase 2: Visual Designer** (Weeks 3-4) 🟡
  - Drag-drop canvas, properties panels, save JSON
- **Phase 3: Runtime Integration** (Weeks 5-6) 🟡
  - Task inbox, AppRuntimeShell integration, scheduler
- **Phase 4: Advanced Features** (Weeks 7-8) 🟢
  - Parallel execution, AI integration, templates
- **Phase 5: Polish** (Week 9) 🟣
  - Performance, validation, documentation

### Risk Mitigation

| Risk | Original Status | New Status |
|------|----------------|------------|
| Running workflows break on definition update | ⚠️ High (deferred to Phase 5) | ✅ Mitigated (versioning in Phase 1) |
| UI builds features backend can't execute | ⚠️ High (UI first) | ✅ Mitigated (backend first) |
| Workflow engine hangs on user tasks | ⚠️ Critical (no task type distinction) | ✅ Mitigated (USER_TASK vs SERVICE_TASK) |
| Complex workflows cause performance issues | ⚠️ Medium (no plan) | ✅ Mitigated (SQL indexes, caching, lazy load) |
| No assignment logic for tasks | ⚠️ High (roles on transitions) | ✅ Mitigated (assignment config on USER_TASK) |

### Next Steps (Immediate)

**Week 1-2 (Phase 1 - Backend Engine)**:
1. Day 1: Create Flyway migration `V3__workflow_tables.sql` (3 tables)
2. Day 2-3: Implement `WorkflowEngine.java` (6 core methods)
3. Day 4: Integrate MVEL for expression evaluation
4. Day 5: Add PostOperationHooks to `ApiServer.java`
5. Day 6-7: Implement 6 REST API endpoints
6. Day 8: Create `WorkflowStorage.java` (JSON file I/O)
7. Day 9: Unit tests for engine and expressions
8. Day 10: Integration testing with Postman/PowerShell

**Success Gate**: Must demonstrate workflow creation, trigger, and completion via API before proceeding to Phase 2.

---

**Document Version:** 2.0 (Revised Architecture)  
**Document Owner:** Engineering Team  
**Last Updated:** December 6, 2025  
**Next Review:** End of Phase 1 (December 20, 2025)

**Contributors:**
- Architecture Review: Gemini AI (Workflow Expert)
- Implementation Plan: GitHub Copilot
- Alignment with AppBana: Dilip Upadhyay

**References:**
- Original Spec: WORKFLOW_FEATURE_SPEC.md v1.0
- Architectural Feedback: https://gemini.google.com/share/41c089a858b1
- AppBana Docs: 01-ARCHITECTURE.md, 02-DEVELOPMENT_GUIDE.md

---

# Appendix: Technical Implementation Details (Phase 1)

## Technical Decisions

### 1. Flyway Placeholder Strategy
**Decision**: Use `$${` escaping in SQL, unescape in Java code
**Rationale**: Flyway requires placeholder escaping, but runtime needs actual `${` syntax
**Implementation**:
```java
if (condition != null && condition.contains("$${")) {
    condition = condition.replace("$${", "${");
}
```

### 2. H2 Database Limitations
**Decision**: Remove partial indexes (WHERE clauses)
**Rationale**: H2 2.2.222 doesn't support filtered indexes
**Impact**: Slightly less efficient queries, but acceptable for development
**Production**: PostgreSQL will support full index syntax

### 3. Column Name Case Sensitivity
**Decision**: Use UPPERCASE column names in expressions
**Rationale**: H2 returns ResultSet metadata in uppercase
**Example**: `${PaymentRequest.AMOUNT > 1000}` not `${...amount...}`
**Alternative Considered**: Use `ResultSet.getObject("amount")` - rejected due to case-insensitive lookup issues

### 4. CLOB Handling
**Decision**: Convert CLOB to String during ResultSet processing
**Rationale**: Jackson can't serialize CLOB objects directly
**Performance**: Acceptable for TEXT fields under 10MB
**Future**: Consider streaming for large BLOBs

### 5. ObjectMapper Configuration
**Decision**: Use `findAndRegisterModules()` globally
**Rationale**: Auto-discovers JSR310 module for LocalDateTime
**Files**: Router.java (static mapper), WorkflowApi.java (instance mapper)

## Lessons Learned

1. **H2 Limitations**: Always check database compatibility before using advanced SQL features
2. **Flyway Placeholders**: Document placeholder escaping strategy clearly in migrations
3. **Jackson Modules**: Auto-register modules to avoid serialization surprises
4. **CLOB Handling**: Convert to String early in data pipeline
5. **Case Sensitivity**: H2 metadata is uppercase, PostgreSQL is lowercase - normalize in code
6. **Comprehensive Logging**: Debug logs saved hours during troubleshooting
