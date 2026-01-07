
# Loan Approval System - End-to-End Verification Script

$baseUrl = "http://localhost:8080"
$entity = "LoanApplication"

function Test-LoanFlow {
    param (
        [string]$ApplicantName,
        [int]$Amount,
        [string]$Purpose,
        [string]$Action
    )

    Write-Host "---------------------------------------------------"
    Write-Host "Testing Scenario: Apply -> Review -> $Action"
    Write-Host "---------------------------------------------------"

    # 1. Apply (Create Report)
    $body = @{
        applicantName = $ApplicantName
        amount = $Amount
        purpose = $Purpose
        creditScore = 750
        status = "PENDING"
    } | ConvertTo-Json

    Write-Host "1. Submitting Application for $ApplicantName..."
    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/api/$entity" -Method Post -Body $body -ContentType "application/json" -ErrorAction Stop
        $id = $response.id
        Write-Host "   -> Application Created with ID: $id"
    } catch {
        Write-Error "Failed to create application: $_"
        return
    }

    # 2. Verify Initial State
    Write-Host "2. Verifying Initial Status (PENDING)..."
    $record = Invoke-RestMethod -Uri "$baseUrl/api/$entity/$id"
    if ($record.status -eq "PENDING") {
        Write-Host "   -> Success: Status is PENDING"
    } else {
        Write-Error "   -> Failure: Status is $($record.status)"
        return
    }

    # 3. Simulate Review & Action
    $targetStatus = if ($Action -eq "Approve") { "APPROVED" } else { "REJECTED" }
    $comments = "$Action by QA Script"
    
    $updateBody = @{
        status = $targetStatus
        managerComments = $comments
    } | ConvertTo-Json

    Write-Host "3. Performing Action: $Action..."
    try {
        Invoke-RestMethod -Uri "$baseUrl/api/$entity/$id" -Method Put -Body $updateBody -ContentType "application/json" -ErrorAction Stop | Out-Null
        Write-Host "   -> Action Submitted"
    } catch {
        Write-Error "Failed to update application: $_"
        return
    }

    # 4. Final Verification
    Write-Host "4. Verifying Final Status ($targetStatus)..."
    $finalRecord = Invoke-RestMethod -Uri "$baseUrl/api/$entity/$id"
    if ($finalRecord.status -eq $targetStatus) {
        Write-Host "   -> TEST PASSED: Loan was successfully $targetStatus"
        Write-Host "   -> Comments: $($finalRecord.managerComments)"
    } else {
        Write-Error "   -> TEST FAILED: Status is $($finalRecord.status)"
    }
}

# Run Scenario 1: Approve
Test-LoanFlow -ApplicantName "John Approve" -Amount 50000 -Purpose "Home Reno" -Action "Approve"

# Run Scenario 2: Reject
Test-LoanFlow -ApplicantName "Jane Reject" -Amount 1000000 -Purpose "Private Island" -Action "Reject"
