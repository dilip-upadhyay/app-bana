$t = (curl.exe -s -X POST "http://localhost:8080/api/auth/login" -H "Content-Type: application/json" -d "@c:\Users\dilip\git\app-bana\scratch\auth.json" | ConvertFrom-Json).token
$base = "http://localhost:8080/appbana-studio/t-cfe77e13/apps/c51105a0-dd6c-453b-870c-af0e1da896bd/pages"
$h = "Authorization: Bearer $t"
foreach ($p in @("observationlist","addobservation","celestialbodylist","celestialbodydetail","searchcelestialbody")) {
    $code = curl.exe -s -o NUL -w "%{http_code}" -X DELETE "$base/$p" -H $h
    Write-Host "DELETE $p -> HTTP $code"
}
Write-Host "Done"
