# Workflow Maker-Checker Patterns

**Document Version:** 1.0  
**Last Updated:** December 6, 2025  
**Related:** WORKFLOW_FEATURE_SPEC.md

---

## Overview

**Yes, the workflow architecture fully supports maker-checker flows!** This document provides concrete implementation patterns for dual-control workflows where one user (maker) creates/modifies data and another user (checker) reviews and approves it.

---

## Core Maker-Checker Pattern

### Pattern 1: Simple Maker-Checker

**Use Case**: Payment Request Approval

```
Workflow: "Payment Request Maker-Checker"
├─ Trigger: ON_CREATE PaymentRequest
│
├─ USER_TASK: Maker Create Payment
│  ├─ Assignment: ${PaymentRequest.createdBy} (dynamic)
│  ├─ Form: Auto-generate (CREATE mode)
│  ├─ Fields: [payee, amount, purpose]
│  └─ onExit: UPDATE status = "Pending Review"
│
├─ USER_TASK: Checker Review
│  ├─ Assignment: ${PaymentRequest.createdBy.manager} (ensures maker ≠ checker)
│  ├─ Form: Auto-generate (VIEW mode - read-only)
│  ├─ Fields: [payee, amount, purpose, createdBy, createdAt]
│  ├─ Timeout: 24h → ESCALATE to senior_manager
│  └─ Outcomes: [Approve, Reject, SendBack]
│
├─ DECISION: Checker Outcome
│  ├─ IF outcome == 'Approve' → END (Approved)
│  ├─ IF outcome == 'Reject' → END (Rejected)
│  └─ IF outcome == 'SendBack' → Loop back to Maker
```

**JSON Definition**:
```json
{
  "id": "payment_maker_checker",
  "name": "Payment Maker-Checker",
  "version": 1,
  "status": "ACTIVE",
  "trigger": {
    "entityId": "PaymentRequest",
    "event": "ON_CREATE"
  },
  "entities": {
    "primary": "PaymentRequest",
    "available": ["PaymentRequest", "User"]
  },
  "nodes": [
    {
      "id": "maker_draft",
      "type": "USER_TASK",
      "name": "Maker: Create Payment",
      "entityContext": "PaymentRequest",
      "assignment": {
        "type": "DYNAMIC",
        "value": "${PaymentRequest.createdBy}"
      },
      "pageConfig": {
        "mode": "AUTO_GENERATE",
        "formMode": "CREATE",
        "fieldsToShow": ["payee", "amount", "purpose"]
      },
      "onExit": [
        {
          "type": "UPDATE_FIELD",
          "config": { "field": "status", "value": "Pending Review" }
        }
      ]
    },
    {
      "id": "checker_review",
      "type": "USER_TASK",
      "name": "Checker: Review Payment",
      "entityContext": "PaymentRequest",
      "assignment": {
        "type": "DYNAMIC",
        "value": "${PaymentRequest.createdBy.manager}"
      },
      "pageConfig": {
        "mode": "AUTO_GENERATE",
        "formMode": "VIEW",
        "fieldsToShow": ["payee", "amount", "purpose", "createdBy", "createdAt"]
      },
      "timeout": {
        "duration": "24h",
        "action": "ESCALATE",
        "targetNodeId": "senior_checker"
      }
    },
    {
      "id": "decision_outcome",
      "type": "DECISION",
      "name": "Checker Decision",
      "entityContext": "PaymentRequest",
      "branches": [
        {
          "id": "branch_approve",
          "label": "Approve",
          "priority": 1,
          "condition": "${outcome == 'Approve'}",
          "targetNodeId": "end_approved"
        },
        {
          "id": "branch_reject",
          "label": "Reject",
          "priority": 2,
          "condition": "${outcome == 'Reject'}",
          "targetNodeId": "end_rejected"
        },
        {
          "id": "branch_sendback",
          "label": "Send Back",
          "priority": 3,
          "condition": "${outcome == 'SendBack'}",
          "targetNodeId": "maker_draft"
        }
      ]
    },
    {
      "id": "end_approved",
      "type": "END",
      "name": "Approved",
      "outcome": "SUCCESS",
      "onEntry": [
        {
          "type": "UPDATE_FIELD",
          "config": { "field": "status", "value": "Approved" }
        }
      ]
    },
    {
      "id": "end_rejected",
      "type": "END",
      "name": "Rejected",
      "outcome": "REJECTED",
      "onEntry": [
        {
          "type": "UPDATE_FIELD",
          "config": { "field": "status", "value": "Rejected" }
        }
      ]
    }
  ],
  "transitions": [
    { "id": "t1", "sourceNodeId": "maker_draft", "targetNodeId": "checker_review" },
    { "id": "t2", "sourceNodeId": "checker_review", "targetNodeId": "decision_outcome" },
    { "id": "t3", "sourceNodeId": "decision_outcome", "targetNodeId": "end_approved" },
    { "id": "t4", "sourceNodeId": "decision_outcome", "targetNodeId": "end_rejected" },
    { "id": "t5", "sourceNodeId": "decision_outcome", "targetNodeId": "maker_draft", "label": "Send Back" }
  ]
}
```

**Key Features**:
- ✅ **Separation of Duties**: Assignment to `${createdBy.manager}` prevents self-approval
- ✅ **Three Outcomes**: Approve (success), Reject (terminate), Send Back (loop for revision)
- ✅ **SLA Enforcement**: 24-hour timeout with automatic escalation
- ✅ **Read-Only Review**: Checker sees VIEW mode form, cannot modify data
- ✅ **Audit Trail**: All transitions logged with maker/checker user IDs

---

## Pattern 2: Conditional Maker-Checker (Amount-Based)

**Use Case**: Expense Reimbursement with Tiered Approval

```
Workflow: "Expense Reimbursement"
├─ USER_TASK: Submit Expense (Maker)
│
├─ DECISION: Check Amount
│  ├─ IF amount > $1,000 → Manager + Finance Review (Dual Checker)
│  ├─ IF amount > $200 → Manager Review Only (Single Checker)
│  └─ ELSE → Auto-Approved (No Checker)
```

**Business Logic**:
- **Low-value** ($0-$200): Auto-approved, no checker needed
- **Medium-value** ($200-$1,000): Manager approval required
- **High-value** (>$1,000): BOTH manager AND finance must approve (Phase 3: Parallel execution)

**Workflow JSON** (Simplified):
```json
{
  "nodes": [
    {
      "id": "submit_expense",
      "type": "USER_TASK",
      "name": "Submit Expense",
      "assignment": { "type": "DYNAMIC", "value": "${Expense.employee}" }
    },
    {
      "id": "decision_amount",
      "type": "DECISION",
      "branches": [
        {
          "condition": "${Expense.amount > 1000}",
          "targetNodeId": "dual_checker_fork"
        },
        {
          "condition": "${Expense.amount > 200}",
          "targetNodeId": "manager_review"
        },
        {
          "condition": "ELSE",
          "targetNodeId": "auto_approved"
        }
      ]
    },
    {
      "id": "manager_review",
      "type": "USER_TASK",
      "assignment": { "type": "DYNAMIC", "value": "${Expense.employee.manager}" }
    },
    {
      "id": "dual_checker_fork",
      "type": "PARALLEL_FORK",
      "targetNodes": ["manager_approval", "finance_approval"]
    },
    {
      "id": "manager_approval",
      "type": "USER_TASK",
      "assignment": { "type": "DYNAMIC", "value": "${Expense.employee.manager}" }
    },
    {
      "id": "finance_approval",
      "type": "USER_TASK",
      "assignment": { "type": "ROLE", "value": "Finance Team" }
    },
    {
      "id": "dual_checker_join",
      "type": "PARALLEL_JOIN",
      "targetNodeId": "approved"
    }
  ]
}
```

**Note**: `PARALLEL_FORK` and `PARALLEL_JOIN` are Phase 3 features. For Phase 1, use sequential approval (manager → finance).

---

## Pattern 3: Multi-Round Maker-Checker

**Use Case**: Data Entry Quality Control

```
Workflow: "Data Entry QA"
├─ USER_TASK: Maker - Data Entry
│  └─ Assignment: Queue (data-entry-team)
│
├─ USER_TASK: Checker 1 - QA Review
│  ├─ Assignment: Queue (qa-team)
│  └─ Form: EDIT mode (can fix minor errors)
│
├─ DECISION: Quality Check
│  ├─ IF errorCount > 5 → Send Back to Maker (major issues)
│  ├─ IF errorCount 1-5 → Escalate to Senior QA (Checker 2)
│  └─ IF errorCount == 0 → Approved (perfect)
```

**Key Features**:
- ✅ **Queue Assignment**: Tasks assigned to team pools, not specific users (Phase 4)
- ✅ **Checker Can Edit**: QA can fix typos/minor errors, not just approve/reject
- ✅ **Multi-Level Checking**: Second-level checker for borderline cases
- ✅ **Loop Back**: Major issues return to maker with feedback

**Workflow JSON**:
```json
{
  "nodes": [
    {
      "id": "maker_entry",
      "type": "USER_TASK",
      "assignment": {
        "type": "QUEUE",
        "value": "data-entry-team"
      },
      "pageConfig": { "mode": "AUTO_GENERATE", "formMode": "CREATE" }
    },
    {
      "id": "checker_1_qa",
      "type": "USER_TASK",
      "assignment": {
        "type": "QUEUE",
        "value": "qa-team"
      },
      "pageConfig": { 
        "mode": "AUTO_GENERATE", 
        "formMode": "EDIT"
      }
    },
    {
      "id": "decision_quality",
      "type": "DECISION",
      "branches": [
        {
          "condition": "${DataRecord.errorCount > 5}",
          "targetNodeId": "maker_entry"
        },
        {
          "condition": "${DataRecord.errorCount > 0}",
          "targetNodeId": "checker_2_senior"
        },
        {
          "condition": "ELSE",
          "targetNodeId": "approved"
        }
      ]
    },
    {
      "id": "checker_2_senior",
      "type": "USER_TASK",
      "assignment": { "type": "ROLE", "value": "Senior QA" }
    }
  ]
}
```

---

## Pattern 4: Cross-Entity Maker-Checker

**Use Case**: Invoice Approval with Vendor Risk Check

```
Workflow: "Invoice Approval with Vendor Risk"
├─ USER_TASK: Maker - Create Invoice
│  └─ Entity: Invoice
│
├─ DECISION: Vendor Risk Check (Cross-Entity)
│  ├─ IF Vendor.riskLevel == 'HIGH' → Senior Finance Checker
│  ├─ IF Invoice.amount > $50,000 → Manager Checker
│  └─ ELSE → Standard Finance Checker
```

**Cross-Entity Condition**:
```json
{
  "id": "decision_vendor_risk",
  "type": "DECISION",
  "entityContext": "Invoice",
  "branches": [
    {
      "label": "High Risk Vendor",
      "condition": "CROSS_ENTITY",
      "crossEntity": {
        "leftEntity": "Vendor",
        "leftField": "riskLevel",
        "operator": "EQUALS",
        "rightEntity": null,
        "rightField": null,
        "value": "HIGH"
      },
      "targetNodeId": "senior_finance_checker"
    },
    {
      "label": "Large Amount",
      "condition": "${Invoice.amount > 50000}",
      "targetNodeId": "manager_checker"
    },
    {
      "label": "Standard",
      "condition": "ELSE",
      "targetNodeId": "standard_checker"
    }
  ]
}
```

**Key Features**:
- ✅ **Cross-Entity Logic**: Routing based on related Vendor record
- ✅ **Risk-Based Approval**: High-risk vendors require senior approval
- ✅ **Dynamic Routing**: Different checkers for different scenarios

---

## Backend Validation & Enforcement

### Self-Approval Prevention

**WorkflowEngine.java**:
```java
public void completeTask(String tokenId, String userId, Map<String, Object> userData) {
    WorkflowToken token = tokenRepository.findById(tokenId);
    WorkflowInstance instance = instanceRepository.findById(token.getInstanceId());
    Map<String, Object> entityData = entityService.getRecord(instance.getEntityType(), instance.getEntityId());
    
    // CRITICAL: Maker-Checker validation
    String makerId = (String) entityData.get("createdBy");
    if (userId.equals(makerId)) {
        throw new WorkflowException(
            "MAKER_CHECKER_VIOLATION",
            "User cannot approve their own submission. Maker: " + makerId + ", Checker: " + userId
        );
    }
    
    // Validate outcome
    String outcome = (String) userData.get("outcome");
    if (!List.of("Approve", "Reject", "SendBack").contains(outcome)) {
        throw new WorkflowException(
            "INVALID_OUTCOME",
            "Outcome must be Approve, Reject, or SendBack"
        );
    }
    
    // Log to audit
    auditService.log(AuditLog.builder()
        .action("WORKFLOW_CHECKER_ACTION")
        .entityType(instance.getEntityType())
        .entityId(instance.getEntityId())
        .userId(userId)
        .metadata(Map.of(
            "workflowId", instance.getWorkflowId(),
            "maker", makerId,
            "checker", userId,
            "outcome", outcome,
            "nodeId", token.getNodeId(),
            "nodeName", getNodeName(token.getNodeId())
        ))
        .build()
    );
    
    // Continue with transition...
    transition(instance.getInstanceId(), tokenId, outcome, userData);
}
```

### Timeout & Escalation

**WorkflowScheduler.java**:
```java
private void checkTimeouts() {
    List<WorkflowToken> overdueTokens = tokenRepository.findOverdue(Instant.now());
    
    for (WorkflowToken token : overdueTokens) {
        WorkflowNode node = getNodeById(token.getNodeId());
        
        if (node instanceof UserTaskNode userTask && userTask.getTimeout() != null) {
            TimeoutConfig timeout = userTask.getTimeout();
            
            // Log escalation
            logger.warn("Task {} overdue, escalating to {}", token.getId(), timeout.getTargetNodeId());
            
            // Audit log
            auditService.log(AuditLog.builder()
                .action("WORKFLOW_SLA_ESCALATION")
                .entityId(token.getInstanceId())
                .metadata(Map.of(
                    "tokenId", token.getId(),
                    "originalAssignee", token.getAssignedUserId(),
                    "dueAt", token.getDueAt(),
                    "escalationTarget", timeout.getTargetNodeId()
                ))
                .build()
            );
            
            // Force transition
            transition(token.getInstanceId(), token.getId(), "ESCALATE", Map.of());
        }
    }
}
```

---

## UI Patterns for Maker-Checker

### My Tasks Inbox (Segregated View)

```
┌─────────────────────────────────────────────────────┐
│ 📥 My Tasks (7 Pending)                             │
├─────────────────────────────────────────────────────┤
│ 🔵 MAKER TASKS (3)                                  │
│                                                      │
│ 1. Payment Request #PR-1042 - SENT BACK ⚠️          │
│    Checker: John Doe (Manager)                      │
│    Comment: "Please add invoice attachment"         │
│    [ Revise & Resubmit ]                            │
│                                                      │
│ 2. Expense Report #EXP-523 - DRAFT                  │
│    Next: Submit for Manager Approval                │
│    [ Continue Editing ]                             │
│                                                      │
│ 3. Data Entry #DE-789 - SENT BACK                   │
│    QA Checker: Sarah (QA Team)                      │
│    Errors: 8 issues found                           │
│    [ Fix Issues ]                                   │
│                                                      │
├─────────────────────────────────────────────────────┤
│ 🟢 CHECKER TASKS (4)                                │
│                                                      │
│ 1. Payment Request #PR-1055 - $15,000              │
│    Maker: Sarah Smith                               │
│    Submitted: 2 hours ago                           │
│    ⏰ Due: 22h remaining                            │
│    [ Review & Approve ]  [ View History ]           │
│                                                      │
│ 2. Invoice #INV-2341 - $75,000 ⚠️ HIGH RISK        │
│    Maker: Tom Johnson                               │
│    Vendor: ACME Corp (Risk: HIGH)                   │
│    ⏰ OVERDUE (escalated to you 2h ago)             │
│    [ Urgent Review ]                                │
│                                                      │
│ 3. Data Entry #DE-790                               │
│    Maker: Data Entry Team (Jane Doe)                │
│    QA Required                                       │
│    [ Review ] [ Edit & Fix ]                        │
│                                                      │
│ 4. Expense #EXP-524 - $350                          │
│    Employee: Mike Brown                             │
│    Standard Review                                   │
│    [ Approve ] [ Reject ] [ Send Back ]             │
└─────────────────────────────────────────────────────┘
```

### Task Detail Page (Checker View)

```
┌──────────────────────────────────────────────────────┐
│ 📄 Payment Request #PR-1055                          │
├──────────────────────────────────────────────────────┤
│ Status: 🟡 Pending Your Approval                     │
│ Workflow: Payment Maker-Checker                      │
│ Current Step: Manager Review                         │
│ Due: December 7, 2025 10:15 AM (22h remaining)       │
│                                                       │
│ ┌────────────────────────────────────────────────┐   │
│ │ 📋 REQUEST DETAILS                             │   │
│ ├────────────────────────────────────────────────┤   │
│ │ Payee: ACME Supplies Inc.                      │   │
│ │ Amount: $15,000.00                             │   │
│ │ Purpose: Office furniture procurement          │   │
│ │ Budget Code: OPS-2025-Q4                       │   │
│ │                                                 │   │
│ │ Created by: Sarah Smith (sarah@company.com)    │   │
│ │ Created at: December 6, 2025 12:15 PM          │   │
│ │ Department: Operations                          │   │
│ └────────────────────────────────────────────────┘   │
│                                                       │
│ ┌────────────────────────────────────────────────┐   │
│ │ 📎 ATTACHMENTS (2)                             │   │
│ │ • invoice_acme_supplies.pdf                    │   │
│ │ • budget_approval.xlsx                         │   │
│ └────────────────────────────────────────────────┘   │
│                                                       │
│ ┌────────────────────────────────────────────────┐   │
│ │ 🕐 WORKFLOW HISTORY                            │   │
│ │                                                 │   │
│ │ Dec 6, 12:15 PM - Created by Sarah Smith       │   │
│ │ Dec 6, 12:20 PM - Submitted for Approval       │   │
│ │ Dec 6, 12:21 PM - Assigned to John Doe         │   │
│ └────────────────────────────────────────────────┘   │
│                                                       │
│ 💬 Comments (Optional)                                │
│ ┌────────────────────────────────────────────────┐   │
│ │ [Add your review comments here...]             │   │
│ └────────────────────────────────────────────────┘   │
│                                                       │
│ [ ✅ Approve ]  [ ❌ Reject ]  [ ↩️ Send Back ]       │
└──────────────────────────────────────────────────────┘
```

---

## Audit Trail & Compliance

### Audit Log Entry Example

```json
{
  "id": "audit-12345",
  "timestamp": "2025-12-06T14:30:00Z",
  "action": "WORKFLOW_CHECKER_ACTION",
  "entityType": "PaymentRequest",
  "entityId": "PR-1055",
  "userId": "user-456",
  "userName": "John Doe",
  "beforeValues": {
    "status": "Pending Review"
  },
  "afterValues": {
    "status": "Approved",
    "approvedBy": "user-456",
    "approvedAt": "2025-12-06T14:30:00Z"
  },
  "metadata": {
    "workflowId": "payment_maker_checker",
    "workflowVersion": 1,
    "instanceId": "wf-inst-789",
    "maker": "user-123",
    "makerName": "Sarah Smith",
    "checker": "user-456",
    "checkerName": "John Doe",
    "checkerRole": "Manager",
    "outcome": "Approve",
    "nodeId": "checker_review",
    "nodeName": "Manager Review",
    "comments": "Budget approved, invoice verified"
  }
}
```

### Compliance Reports

**Maker-Checker Separation Report**:
```sql
-- Verify no self-approvals
SELECT 
  a1.entity_id,
  a1.metadata->>'maker' AS maker,
  a1.metadata->>'checker' AS checker,
  a1.metadata->>'outcome' AS outcome
FROM appbana_audit a1
WHERE a1.action = 'WORKFLOW_CHECKER_ACTION'
  AND a1.metadata->>'maker' = a1.metadata->>'checker'
-- Should return 0 rows
```

**SLA Compliance Report**:
```sql
-- Check escalation rates
SELECT 
  workflow_id,
  COUNT(*) AS total_tasks,
  SUM(CASE WHEN metadata->>'outcome' = 'ESCALATE' THEN 1 ELSE 0 END) AS escalated_tasks,
  ROUND(100.0 * SUM(CASE WHEN metadata->>'outcome' = 'ESCALATE' THEN 1 ELSE 0 END) / COUNT(*), 2) AS escalation_rate
FROM appbana_audit
WHERE action = 'WORKFLOW_SLA_ESCALATION'
GROUP BY workflow_id
```

---

## Summary: Maker-Checker Support

| Feature | Supported | Phase |
|---------|-----------|-------|
| **Separation of Duties** | ✅ Yes | Phase 1 |
| **Dynamic Assignment** (`${createdBy.manager}`) | ✅ Yes | Phase 1 |
| **Three Outcomes** (Approve/Reject/SendBack) | ✅ Yes | Phase 1 |
| **Loop Back** (Send to Maker) | ✅ Yes | Phase 1 |
| **SLA & Escalation** | ✅ Yes | Phase 2 |
| **Read-Only Review Forms** | ✅ Yes | Phase 2 |
| **Audit Trail** | ✅ Yes | Phase 1 |
| **Self-Approval Prevention** | ✅ Yes | Phase 1 (Backend) |
| **Queue Assignment** (Team pools) | ⏳ Phase 4 |
| **Dual Checker** (Parallel approval) | ⏳ Phase 3 |
| **Cross-Entity Routing** | ✅ Yes | Phase 1 |

---

**Document Owner:** Engineering Team  
**Contributors:** Architecture Review, GitHub Copilot  
**Next Steps:** Implement Pattern 1 (Simple Maker-Checker) in Phase 1 backend
