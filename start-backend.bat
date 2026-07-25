@echo off
REM =====================================================================
REM start-backend.bat  -  Restart the AppBana core API on port 8080
REM
REM What it does:
REM   1. Stops any backend process already running on port 8080
REM   2. Ensures PostgreSQL is up
REM   3. Builds the app-bana-service module (with its parent deps)
REM   4. Launches the service on port 8080
REM =====================================================================
setlocal EnableDelayedExpansion

set "BE_PORT=8080"
set "PG_PORT=5432"

echo ==========================================
echo [backend] Restarting on port %BE_PORT%
echo ==========================================

REM --- Step 1: stop any existing backend process on port 8080 ----------
echo [1/4] Stopping any existing backend process on port %BE_PORT%...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":%BE_PORT% " ^| findstr "LISTENING"') do (
    echo    Killing PID %%a
    taskkill /F /PID %%a >nul 2>&1
)

REM --- Step 2: ensure PostgreSQL is running ----------------------------
echo [2/4] Ensuring PostgreSQL is running...
docker --version >nul 2>&1
if !ERRORLEVEL! NEQ 0 (
    echo    ERROR: Docker is not installed or not on PATH.
    exit /b 1
)
docker ps --format "{{.Names}}" | findstr /X "appbana-postgres" >nul 2>&1
if !ERRORLEVEL! NEQ 0 (
    docker ps -a --format "{{.Names}}" | findstr /X "appbana-postgres" >nul 2>&1
    if !ERRORLEVEL! EQU 0 (
        echo    Starting existing PostgreSQL container...
        docker start appbana-postgres >nul
    ) else (
        echo    Creating PostgreSQL container...
        docker run -d --name appbana-postgres -e POSTGRES_DB=appbana -e POSTGRES_USER=appbana -e POSTGRES_PASSWORD=appbana_dev_2026 -p %PG_PORT%:5432 -v appbana-postgres-data:/var/lib/postgresql/data postgres:16-alpine >nul
    )
    timeout /t 3 /nobreak >nul
)
echo    PostgreSQL: running on port %PG_PORT%

REM --- Step 3: build the module ----------------------------------------
echo [3/4] Building app-bana-service module...
call mvn -q -pl app-bana-service -am -DskipTests install
if !ERRORLEVEL! NEQ 0 (
    if not exist "app-bana-service\target\app-bana-1.0-SNAPSHOT-fat.jar" (
        echo    ERROR: Build failed and no existing jar found.
        exit /b 1
    )
    echo    WARNING: Build failed, using existing jar.
)

REM --- Step 4: launch --------------------------------------------------
echo [4/4] Launching Backend on port %BE_PORT%...
echo    Health: http://localhost:%BE_PORT%/health
echo    Press Ctrl+C to stop.
echo ==========================================

cd app-bana-service
java -jar target\app-bana-1.0-SNAPSHOT-fat.jar
endlocal
