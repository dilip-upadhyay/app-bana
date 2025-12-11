$ErrorActionPreference = "Stop"

$appId = "leave-request-app"
$url = "http://localhost:8080/api/apps/$appId/versions"

# 1. Create a Version
$body = @{
    label = "v1.0.0-beta"
    description = "First automated release"
} | ConvertTo-Json

Write-Host "Creating version..."
try {
    $response = Invoke-RestMethod -Uri $url -Method Post -Body $body -ContentType "application/json" -Headers @{ "X-User-Id" = "verifier" }
    Write-Host "Success! Created Version ID: $($response.id)"
} catch {
    Write-Error "Failed to create version: $_"
    exit 1
}

# 2. List Versions
Write-Host "Listing versions..."
try {
    $versions = Invoke-RestMethod -Uri $url -Method Get
    Write-Host "Found $($versions.Count) versions:"
    $versions | Format-Table versionNumber, label, createdAt, isLive
} catch {
    Write-Error "Failed to list versions: $_"
    exit 1
}
