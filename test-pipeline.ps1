$ErrorActionPreference = "Stop"
$appId = "pipeline-demo-app"
$baseUrl = "http://localhost:8080/api/apps/$appId"

# Helper to create dummy app files if needed (skipped here, assuming app auto-created on version push or exists)
# In current impl, createVersion checks file system. We need an app first.
# Wait, createVersion reads from AppManager.getApp(). We need to ensure the App exists in memory/files.
# Let's use the API to create the app first (if valid endpoint exists) or just trust "leave-request-app" exists.

$appId = "leave-request-app" # Re-using existing app
$baseUrl = "http://localhost:8080/api/apps/$appId"

Write-Host "--- Pipeline Test Start ---"

# 1. Create Version v1
$v1Label = "v1.0-alpha"
Write-Host "Creating Version 1 ($v1Label)..."
$body = @{ label = $v1Label; description = "Alpha release" } | ConvertTo-Json
$res1 = Invoke-RestMethod -Uri "$baseUrl/versions" -Method Post -Body $body -ContentType "application/json" -Headers @{ "X-User-Id" = "tester" }
$v1Id = $res1.id
Write-Host "V1 Created: $v1Id"

# 2. Deploy v1 to DEV
Write-Host "Deploying v1 to DEV..."
$deployBody = @{ environment = "DEV" } | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/deploy/$v1Id" -Method Post -Body $deployBody -ContentType "application/json" -Headers @{ "X-User-Id" = "tester" }

# 3. Deploy v1 to PROD (Simulate fast track)
Write-Host "Deploying v1 to PROD..."
$deployBody = @{ environment = "PROD" } | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/deploy/$v1Id" -Method Post -Body $deployBody -ContentType "application/json" -Headers @{ "X-User-Id" = "tester" }

# 4. Create Version v2
$v2Label = "v1.1-beta"
Write-Host "Creating Version 2 ($v2Label)..."
$body = @{ label = $v2Label; description = "Beta release" } | ConvertTo-Json
$res2 = Invoke-RestMethod -Uri "$baseUrl/versions" -Method Post -Body $body -ContentType "application/json" -Headers @{ "X-User-Id" = "tester" }
$v2Id = $res2.id
Write-Host "V2 Created: $v2Id"

# 5. Deploy v2 to DEV only
Write-Host "Deploying v2 to DEV..."
$deployBody = @{ environment = "DEV" } | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/deploy/$v2Id" -Method Post -Body $deployBody -ContentType "application/json" -Headers @{ "X-User-Id" = "tester" }

# 6. Check Pipeline Status
Write-Host "Checking Pipeline Status..."
$status = Invoke-RestMethod -Uri "$baseUrl/pipeline" -Method Get

Write-Host "`nPipeline State:"
$status | ConvertTo-Json -Depth 5

# verification logic
if ($status.DEV.versionId -eq $v2Id -and $status.PROD.versionId -eq $v1Id) {
    Write-Host "`n✅ SUCCESS: DEV has v2, PROD has v1. Pipeline working!"
} else {
    Write-Error "❌ FAILURE: Pipeline state incorrect."
}
