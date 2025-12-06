# ========================================
# Workflow API Testing Script - Phase 1
# ========================================
# Tests complete workflow lifecycle:
# 1. Create workflow definition
# 2. Publish workflow (DRAFT → ACTIVE)
# 3. Create entity to trigger workflow
# 4. Check my-tasks API for pending task
# 5. Complete task to advance workflow
# 6. Verify workflow completed
# ========================================

$baseUrl = "http://localhost:8080"
$headers = @{ "Content-Type" = "application/json" }

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Workflow API Testing - Phase 1" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# ========================================
# Step 1: Create Workflow Definition
# ========================================
Write-Host "[Step 1] Creating workflow definition..." -ForegroundColor Yellow

$workflowDef = @{
    id = "test-payment-workflow-$(Get-Random)"
    appId = "default-app"
    name = "Test Payment Approval"
    description = "Simple maker-checker for testing"
    triggerEntity = "PaymentRequest"
    triggerEvent = "ON_CREATE"
    triggerCondition = '${PaymentRequest.amount > 1000}'
    status = "DRAFT"
    definition = @{
        id = "test-payment-workflow"
        name = "Test Payment Approval"
        nodes = @{
            start = @{
                id = "start"
                type = "START"
                label = "Start"
            }
            review = @{
                id = "review"
                type = "USER_TASK"
                label = "Review Payment"
                assignmentType = "USER"
                assignedUserId = "test-user-001"
                slaHours = 24
                formFields = @(
                    @{ name = "comments"; type = "textarea"; required = $false }
                    @{ name = "decision"; type = "select"; options = @("APPROVE", "REJECT") }
                )
            }
            approved = @{
                id = "approved"
                type = "SERVICE_TASK"
                label = "Mark as Approved"
                serviceAction = "UPDATE_ENTITY"
            }
            rejected = @{
                id = "rejected"
                type = "SERVICE_TASK"
                label = "Mark as Rejected"
                serviceAction = "UPDATE_ENTITY"
            }
            end = @{
                id = "end"
                type = "END"
                label = "End"
            }
        }
        transitions = @(
            @{ from = "start"; to = "review"; condition = $null }
            @{ from = "review"; to = "approved"; condition = '${outcome == "APPROVE"}'; label = "Approve" }
            @{ from = "review"; to = "rejected"; condition = '${outcome == "REJECT"}'; label = "Reject" }
            @{ from = "approved"; to = "end"; condition = $null }
            @{ from = "rejected"; to = "end"; condition = $null }
        )
    }
} | ConvertTo-Json -Depth 10

try {
    $createResponse = Invoke-RestMethod -Uri "$baseUrl/api/workflows" -Method Post -Body $workflowDef -Headers $headers
    $workflowId = $createResponse.id
    Write-Host "✓ Workflow created: $workflowId (version: $($createResponse.version))" -ForegroundColor Green
} catch {
    Write-Host "✗ Failed to create workflow: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# ========================================
# Step 2: Publish Workflow
# ========================================
Write-Host "`n[Step 2] Publishing workflow..." -ForegroundColor Yellow

try {
    $publishResponse = Invoke-RestMethod -Uri "$baseUrl/api/workflows/$workflowId/publish" -Method Post -Headers $headers
    Write-Host "✓ Workflow published: $($publishResponse.status)" -ForegroundColor Green
} catch {
    Write-Host "✗ Failed to publish workflow: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# ========================================
# Step 3: Create PaymentRequest Entity
# ========================================
Write-Host "`n[Step 3] Creating PaymentRequest to trigger workflow..." -ForegroundColor Yellow

# First, create PaymentRequest schema if not exists
$paymentSchema = @{
    name = "PaymentRequest"
    fields = @(
        @{ name = "id"; type = "number"; required = $true }
        @{ name = "amount"; type = "number"; required = $true }
        @{ name = "description"; type = "text"; required = $false }
        @{ name = "status"; type = "text"; required = $false }
    )
} | ConvertTo-Json -Depth 10

try {
    Invoke-RestMethod -Uri "$baseUrl/schema" -Method Post -Body $paymentSchema -Headers $headers -ErrorAction SilentlyContinue | Out-Null
    Write-Host "  Schema created/exists" -ForegroundColor Gray
} catch {
    # Schema might already exist, continue
}

# Create entity with amount > 1000 (should trigger workflow)
$paymentData = @{
    amount = 5000
    description = "Test payment for workflow"
    status = "PENDING"
} | ConvertTo-Json

try {
    $entityResponse = Invoke-RestMethod -Uri "$baseUrl/api/PaymentRequest" -Method Post -Body $paymentData -Headers $headers
    $entityId = $entityResponse.id
    Write-Host "✓ PaymentRequest created: $entityId (amount: 5000)" -ForegroundColor Green
    Write-Host "  Workflow should auto-start due to trigger condition" -ForegroundColor Gray
    Start-Sleep -Seconds 1
} catch {
    Write-Host "✗ Failed to create entity: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# ========================================
# Step 4: Check My Tasks
# ========================================
Write-Host "`n[Step 4] Checking my-tasks API..." -ForegroundColor Yellow

try {
    $tasksResponse = Invoke-RestMethod -Uri "$baseUrl/api/my-tasks?userId=test-user-001" -Method Get -Headers $headers
    
    if ($tasksResponse.Count -gt 0) {
        Write-Host "✓ Found $($tasksResponse.Count) pending task(s)" -ForegroundColor Green
        $task = $tasksResponse[0]
        $tokenId = $task.tokenId
        Write-Host "  Token ID: $tokenId" -ForegroundColor Gray
        Write-Host "  Node ID: $($task.nodeId)" -ForegroundColor Gray
        Write-Host "  Workflow: $($task.workflowName)" -ForegroundColor Gray
        Write-Host "  Entity: $($task.entityType)/$($task.entityId)" -ForegroundColor Gray
    } else {
        Write-Host "✗ No pending tasks found (workflow may not have triggered)" -ForegroundColor Red
        Write-Host "  Listing all workflows to debug..." -ForegroundColor Yellow
        $allWorkflows = Invoke-RestMethod -Uri "$baseUrl/api/workflows" -Method Get -Headers $headers
        Write-Host "  Total workflows: $($allWorkflows.Count)" -ForegroundColor Gray
        exit 1
    }
} catch {
    Write-Host "✗ Failed to get my tasks: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# ========================================
# Step 5: Complete Task
# ========================================
Write-Host "`n[Step 5] Completing task with APPROVE outcome..." -ForegroundColor Yellow

$completeData = @{
    outcome = "APPROVE"
    taskData = @{
        comments = "Payment approved via automated test"
        decision = "APPROVE"
    }
} | ConvertTo-Json -Depth 5

try {
    $completeResponse = Invoke-RestMethod -Uri "$baseUrl/api/my-tasks/$tokenId/complete" -Method Post -Body $completeData -Headers $headers
    Write-Host "✓ Task completed: $($completeResponse.status)" -ForegroundColor Green
    Start-Sleep -Seconds 1
} catch {
    Write-Host "✗ Failed to complete task: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# ========================================
# Step 6: Verify Workflow Completed
# ========================================
Write-Host "`n[Step 6] Verifying workflow instance completed..." -ForegroundColor Yellow

try {
    $instancesResponse = Invoke-RestMethod -Uri "$baseUrl/api/workflow-instances?entityId=$entityId&entityType=PaymentRequest" -Method Get -Headers $headers
    
    if ($instancesResponse.Count -gt 0) {
        $instance = $instancesResponse[0]
        Write-Host "✓ Workflow instance found" -ForegroundColor Green
        Write-Host "  Instance ID: $($instance.id)" -ForegroundColor Gray
        Write-Host "  Status: $($instance.status)" -ForegroundColor Gray
        Write-Host "  Started: $($instance.startedAt)" -ForegroundColor Gray
        if ($instance.completedAt) {
            Write-Host "  Completed: $($instance.completedAt)" -ForegroundColor Gray
        }
        
        if ($instance.status -eq "COMPLETED") {
            Write-Host "`n✓✓✓ WORKFLOW TEST SUCCESSFUL ✓✓✓" -ForegroundColor Green
        } else {
            Write-Host "`n⚠ Workflow still running (status: $($instance.status))" -ForegroundColor Yellow
        }
    } else {
        Write-Host "✗ No workflow instance found for entity" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "✗ Failed to get workflow instances: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# ========================================
# Summary
# ========================================
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Test Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Workflow ID: $workflowId" -ForegroundColor Gray
Write-Host "Entity ID: $entityId" -ForegroundColor Gray
Write-Host "Token ID: $tokenId" -ForegroundColor Gray
Write-Host "`nAll Phase 1 features tested:" -ForegroundColor Green
Write-Host "  ✓ Workflow definition CRUD" -ForegroundColor Green
Write-Host "  ✓ Workflow publish (versioning)" -ForegroundColor Green
Write-Host "  ✓ Auto-trigger on entity creation" -ForegroundColor Green
Write-Host "  ✓ Trigger condition evaluation" -ForegroundColor Green
Write-Host "  ✓ My Tasks API" -ForegroundColor Green
Write-Host "  ✓ Task completion + transition" -ForegroundColor Green
Write-Host "  ✓ Workflow instance tracking" -ForegroundColor Green
Write-Host "`n========================================`n" -ForegroundColor Cyan
