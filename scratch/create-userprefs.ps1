$t = (curl.exe -s -X POST "http://localhost:8080/api/auth/login" -H "Content-Type: application/json" -d "@c:\Users\dilip\git\app-bana\scratch\auth.json" | ConvertFrom-Json).token
$appId = "c51105a0-dd6c-453b-870c-af0e1da896bd"
$tid = "t-cfe77e13"

# 1. Create UserPreference entity
$schema = @{
  name = "UserPreference"
  displayName = "User Preferences"
  appId = $appId
  fields = @(
    @{id="id"; name="id"; type="integer"; label="ID"; required=$true; primaryKey=$true; autoIncrement=$true},
    @{id="created_at"; name="created_at"; type="datetime"; label="Created At"; required=$false},
    @{id="updated_at"; name="updated_at"; type="datetime"; label="Updated At"; required=$false},
    @{id="user_email"; name="user_email"; type="email"; label="User Email"; required=$true},
    @{id="city_name"; name="city_name"; type="text"; label="Preferred City"; required=$false},
    @{id="lat"; name="lat"; type="decimal"; label="Latitude"; required=$false},
    @{id="lon"; name="lon"; type="decimal"; label="Longitude"; required=$false},
    @{id="tz_offset"; name="tz_offset"; type="decimal"; label="Timezone Offset"; required=$false},
    @{id="tracked_stars"; name="tracked_stars"; type="longtext"; label="Tracked Stars (JSON)"; required=$false},
    @{id="mag_limit"; name="mag_limit"; type="decimal"; label="Magnitude Limit"; required=$false},
    @{id="show_labels"; name="show_labels"; type="boolean"; label="Show Labels"; required=$false}
  )
} | ConvertTo-Json -Depth 5

$r = curl.exe -s -w "`nHTTP:%{http_code}" -X POST "http://localhost:8080/schema" -H "Content-Type: application/json" -H "Authorization: Bearer $t" -d $schema
Write-Host "Schema create: $r"

# 2. Link entity to app
$link = @{appId=$appId; entityName="UserPreference"} | ConvertTo-Json
$r2 = curl.exe -s -w "`nHTTP:%{http_code}" -X POST "http://localhost:8080/appbana-studio/$tid/apps/$appId/entities" -H "Content-Type: application/json" -H "Authorization: Bearer $t" -d $link
Write-Host "Entity link: $r2"

Write-Host "Done"
