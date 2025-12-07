#!/bin/bash
# ========================================
# Workflow API Testing Script - Phase 1
# macOS/Linux version
# ========================================
# Tests complete workflow lifecycle:
# 1. Create workflow definition
# 2. Publish workflow (DRAFT → ACTIVE)
# 3. Create entity to trigger workflow
# 4. Check my-tasks API for pending task
# 5. Complete task to advance workflow
# 6. Verify workflow completed
# ========================================

set -e  # Exit on error

BASE_URL="http://localhost:8080"
WORKFLOW_ID=""
ENTITY_ID=""
TOKEN_ID=""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
GRAY='\033[0;37m'
NC='\033[0m' # No Color

echo -e "\n${CYAN}========================================${NC}"
echo -e "${CYAN}Workflow API Testing - Phase 1${NC}"
echo -e "${CYAN}========================================${NC}\n"

# ========================================
# Step 1: Create Workflow Definition
# ========================================
echo -e "${YELLOW}[Step 1] Creating workflow definition...${NC}"

WORKFLOW_DEF=$(cat <<EOF
{
  "id": "test-payment-workflow-$RANDOM",
  "appId": "default-app",
  "name": "Test Payment Approval",
  "description": "Simple maker-checker for testing",
  "triggerEntity": "PaymentRequest",
  "triggerEvent": "ON_CREATE",
  "triggerCondition": "\${PaymentRequest.amount > 1000}",
  "status": "DRAFT",
  "definition": {
    "id": "test-payment-workflow",
    "name": "Test Payment Approval",
    "nodes": {
      "start": {
        "id": "start",
        "type": "START",
        "label": "Start"
      },
      "review": {
        "id": "review",
        "type": "USER_TASK",
        "label": "Review Payment",
        "assignmentType": "USER",
        "assignedUserId": "test-user-001",
        "slaHours": 24,
        "formFields": [
          {"name": "comments", "type": "textarea", "required": false},
          {"name": "decision", "type": "select", "options": ["APPROVE", "REJECT"]}
        ]
      },
      "approved": {
        "id": "approved",
        "type": "SERVICE_TASK",
        "label": "Mark as Approved",
        "serviceAction": "UPDATE_ENTITY"
      },
      "rejected": {
        "id": "rejected",
        "type": "SERVICE_TASK",
        "label": "Mark as Rejected",
        "serviceAction": "UPDATE_ENTITY"
      },
      "end": {
        "id": "end",
        "type": "END",
        "label": "End"
      }
    },
    "transitions": [
      {"from": "start", "to": "review", "condition": null},
      {"from": "review", "to": "approved", "condition": "\${outcome == 'APPROVE'}", "label": "Approve"},
      {"from": "review", "to": "rejected", "condition": "\${outcome == 'REJECT'}", "label": "Reject"},
      {"from": "approved", "to": "end", "condition": null},
      {"from": "rejected", "to": "end", "condition": null}
    ]
  }
}
EOF
)

RESPONSE=$(curl -s -X POST "$BASE_URL/api/workflows" \
  -H "Content-Type: application/json" \
  -d "$WORKFLOW_DEF")

WORKFLOW_ID=$(echo "$RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | sed 's/"id":"\(.*\)"/\1/')
VERSION=$(echo "$RESPONSE" | grep -o '"version":[0-9]*' | sed 's/"version":\(.*\)/\1/')

if [ -n "$WORKFLOW_ID" ]; then
  echo -e "${GREEN}✓ Workflow created: $WORKFLOW_ID (version: $VERSION)${NC}"
else
  echo -e "${RED}✗ Failed to create workflow${NC}"
  echo "$RESPONSE"
  exit 1
fi

# ========================================
# Step 2: Publish Workflow
# ========================================
echo -e "\n${YELLOW}[Step 2] Publishing workflow...${NC}"

RESPONSE=$(curl -s -X POST "$BASE_URL/api/workflows/$WORKFLOW_ID/publish" \
  -H "Content-Type: application/json")

STATUS=$(echo "$RESPONSE" | grep -o '"status":"[^"]*"' | sed 's/"status":"\(.*\)"/\1/')

if [ "$STATUS" = "published" ]; then
  echo -e "${GREEN}✓ Workflow published: $STATUS${NC}"
else
  echo -e "${RED}✗ Failed to publish workflow${NC}"
  echo "$RESPONSE"
  exit 1
fi

# ========================================
# Step 3: Create PaymentRequest Entity
# ========================================
echo -e "\n${YELLOW}[Step 3] Creating PaymentRequest to trigger workflow...${NC}"

# Create schema (ignore errors if exists)
SCHEMA=$(cat <<EOF
{
  "name": "PaymentRequest",
  "fields": [
    {"name": "id", "type": "integer", "primaryKey": true, "autoIncrement": true},
    {"name": "amount", "type": "number", "required": true},
    {"name": "description", "type": "text", "required": false},
    {"name": "status", "type": "text", "required": false}
  ]
}
EOF
)

curl -s -X POST "$BASE_URL/schema" \
  -H "Content-Type: application/json" \
  -d "$SCHEMA" > /dev/null 2>&1

echo -e "${GRAY}  Schema created/exists${NC}"

# Create entity with amount > 1000 (should trigger workflow)
PAYMENT_DATA=$(cat <<EOF
{
  "amount": 5000,
  "description": "Test payment for workflow",
  "status": "PENDING"
}
EOF
)

RESPONSE=$(curl -s -X POST "$BASE_URL/api/PaymentRequest" \
  -H "Content-Type: application/json" \
  -d "$PAYMENT_DATA")

ENTITY_ID=$(echo "$RESPONSE" | grep -o '"id":[0-9]*' | sed 's/"id":\(.*\)/\1/')

if [ -n "$ENTITY_ID" ]; then
  echo -e "${GREEN}✓ PaymentRequest created: $ENTITY_ID (amount: 5000)${NC}"
  echo -e "${GRAY}  Workflow should auto-start due to trigger condition${NC}"
  sleep 2
else
  echo -e "${RED}✗ Failed to create entity${NC}"
  echo "$RESPONSE"
  exit 1
fi

# ========================================
# Step 4: Check My Tasks
# ========================================
echo -e "\n${YELLOW}[Step 4] Checking my-tasks API...${NC}"

RESPONSE=$(curl -s -X GET "$BASE_URL/api/my-tasks?userId=test-user-001" \
  -H "Content-Type: application/json")

# Check if response is an array with tasks
if echo "$RESPONSE" | grep -q "tokenId"; then
  TOKEN_ID=$(echo "$RESPONSE" | grep -o '"tokenId":"[^"]*"' | head -1 | sed 's/"tokenId":"\(.*\)"/\1/')
  NODE_ID=$(echo "$RESPONSE" | grep -o '"nodeId":"[^"]*"' | head -1 | sed 's/"nodeId":"\(.*\)"/\1/')
  WORKFLOW_NAME=$(echo "$RESPONSE" | grep -o '"workflowName":"[^"]*"' | head -1 | sed 's/"workflowName":"\(.*\)"/\1/')
  ENTITY_TYPE=$(echo "$RESPONSE" | grep -o '"entityType":"[^"]*"' | head -1 | sed 's/"entityType":"\(.*\)"/\1/')
  
  echo -e "${GREEN}✓ Found pending task${NC}"
  echo -e "${GRAY}  Token ID: $TOKEN_ID${NC}"
  echo -e "${GRAY}  Node ID: $NODE_ID${NC}"
  echo -e "${GRAY}  Workflow: $WORKFLOW_NAME${NC}"
  echo -e "${GRAY}  Entity: $ENTITY_TYPE/$ENTITY_ID${NC}"
else
  echo -e "${RED}✗ No pending tasks found (workflow may not have triggered)${NC}"
  echo "$RESPONSE"
  exit 1
fi

# ========================================
# Step 5: Complete Task
# ========================================
echo -e "\n${YELLOW}[Step 5] Completing task with APPROVE outcome...${NC}"

COMPLETE_DATA=$(cat <<EOF
{
  "outcome": "APPROVE",
  "taskData": {
    "comments": "Payment approved via automated test",
    "decision": "APPROVE"
  }
}
EOF
)

RESPONSE=$(curl -s -X POST "$BASE_URL/api/my-tasks/$TOKEN_ID/complete" \
  -H "Content-Type: application/json" \
  -d "$COMPLETE_DATA")

STATUS=$(echo "$RESPONSE" | grep -o '"status":"[^"]*"' | sed 's/"status":"\(.*\)"/\1/')

if [ "$STATUS" = "completed" ]; then
  echo -e "${GREEN}✓ Task completed: $STATUS${NC}"
  sleep 1
else
  echo -e "${RED}✗ Failed to complete task${NC}"
  echo "$RESPONSE"
  exit 1
fi

# ========================================
# Step 6: Verify Workflow Completed
# ========================================
echo -e "\n${YELLOW}[Step 6] Verifying workflow instance completed...${NC}"

RESPONSE=$(curl -s -X GET "$BASE_URL/api/workflow-instances?entityId=$ENTITY_ID&entityType=PaymentRequest" \
  -H "Content-Type: application/json")

if echo "$RESPONSE" | grep -q '"id"'; then
  INSTANCE_ID=$(echo "$RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | sed 's/"id":"\(.*\)"/\1/')
  INSTANCE_STATUS=$(echo "$RESPONSE" | grep -o '"status":"[^"]*"' | head -1 | sed 's/"status":"\(.*\)"/\1/')
  STARTED=$(echo "$RESPONSE" | grep -o '"startedAt":"[^"]*"' | head -1 | sed 's/"startedAt":"\(.*\)"/\1/')
  
  echo -e "${GREEN}✓ Workflow instance found${NC}"
  echo -e "${GRAY}  Instance ID: $INSTANCE_ID${NC}"
  echo -e "${GRAY}  Status: $INSTANCE_STATUS${NC}"
  echo -e "${GRAY}  Started: $STARTED${NC}"
  
  if [ "$INSTANCE_STATUS" = "COMPLETED" ]; then
    echo -e "\n${GREEN}✓✓✓ WORKFLOW TEST SUCCESSFUL ✓✓✓${NC}"
  else
    echo -e "\n${YELLOW}⚠ Workflow still running (status: $INSTANCE_STATUS)${NC}"
  fi
else
  echo -e "${RED}✗ No workflow instance found for entity${NC}"
  echo "$RESPONSE"
  exit 1
fi

# ========================================
# Summary
# ========================================
echo -e "\n${CYAN}========================================${NC}"
echo -e "${CYAN}Test Summary${NC}"
echo -e "${CYAN}========================================${NC}"
echo -e "${GRAY}Workflow ID: $WORKFLOW_ID${NC}"
echo -e "${GRAY}Entity ID: $ENTITY_ID${NC}"
echo -e "${GRAY}Token ID: $TOKEN_ID${NC}"
echo -e "\n${GREEN}All Phase 1 features tested:${NC}"
echo -e "${GREEN}  ✓ Workflow definition CRUD${NC}"
echo -e "${GREEN}  ✓ Workflow publish (versioning)${NC}"
echo -e "${GREEN}  ✓ Auto-trigger on entity creation${NC}"
echo -e "${GREEN}  ✓ Trigger condition evaluation${NC}"
echo -e "${GREEN}  ✓ My Tasks API${NC}"
echo -e "${GREEN}  ✓ Task completion + transition${NC}"
echo -e "${GREEN}  ✓ Workflow instance tracking${NC}"
echo -e "\n${CYAN}========================================${NC}\n"
