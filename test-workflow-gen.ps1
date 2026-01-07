$ErrorActionPreference = "Stop"

$url = "http://localhost:8080/api/ai/generate"
$payload = @{
    description = "Create a Leave Request app where employees submit leave requests and managers approve them if the duration is more than 3 days."
    userId = "verifier-bot"
} | ConvertTo-Json

Write-Host "Sending request to $url..."
try {
    $response = Invoke-RestMethod -Uri $url -Method Post -Body $payload -ContentType "application/json"
    
    Write-Host "Response received!"
    $appName = $response.appName
    $appId = $response.payload.appId
    
    Write-Host "App Name: $appName"
    Write-Host "App ID: $appId"
    
    if ($response.workflows) {
        Write-Host "Workflows found: $($response.workflows.Count)"
        foreach ($wf in $response.workflows) {
            Write-Host " - Name: $($wf.name)"
            Write-Host "   Trigger: $($wf.triggerEntity) ($($wf.triggerEvent))"
            Write-Host "   Condition: $($wf.triggerCondition)"
        }
    } else {
        Write-Error "No workflows returned in the response!"
    }

    # Verify via Workflows API if possible
    # $wfUrl = "http://localhost:8080/api/workflows"
    # $wfs = Invoke-RestMethod -Uri $wfUrl
    # Write-Host "Total workflows in system: $($wfs.Count)"

} catch {
    Write-Error "Request failed: $_"
    exit 1
}
