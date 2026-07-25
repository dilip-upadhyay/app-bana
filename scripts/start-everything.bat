@echo off
REM =====================================================================
REM start-everything.bat  -  Restart all AppBana services
REM
REM Orchestrates the three per-module scripts in the correct order.
REM Each module script is fully self-contained (stops old, ensures deps,
REM builds if needed, launches). This script simply chains them and
REM waits for each service to be reachable before starting the next.
REM
REM Order:
REM   1. AI Builder  (port 8081)  <- also brings up Qdrant + PostgreSQL
REM   2. Backend     (port 8080)
REM   3. UI          (port 5173)
REM =====================================================================
setlocal EnableDelayedExpansion

REM Resolve scripts folder and repo root regardless of where invoked
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%.."

if not exist "pom.xml" (
    echo ERROR: could not locate repo root (pom.xml missing).
    exit /b 1
)

echo ==========================================
echo Starting All AppBana Services
echo ==========================================

echo [1/3] Launching AI Builder in a new window...
start "AI Builder" cmd /c ""%SCRIPT_DIR%start-ai-builder.bat""

echo    Waiting for AI Builder to be ready on port 8081...
:waitAiPort
netstat -ano | findstr ":8081 " | findstr "LISTENING" >nul
if !ERRORLEVEL! NEQ 0 (
    ping 127.0.0.1 -n 3 >nul
    goto waitAiPort
)
echo    AI Builder is up.

echo [2/3] Launching Backend in a new window...
start "AppBana Backend" cmd /c ""%SCRIPT_DIR%start-backend.bat""

echo    Waiting for Backend to be ready on port 8080...
:waitBePort
netstat -ano | findstr ":8080 " | findstr "LISTENING" >nul
if !ERRORLEVEL! NEQ 0 (
    ping 127.0.0.1 -n 3 >nul
    goto waitBePort
)
echo    Backend is up.

echo [3/3] Launching UI in a new window...
start "AppBana UI" cmd /c ""%SCRIPT_DIR%start-ui.bat""

echo ==========================================
echo All services launched:
echo    AI Builder: http://localhost:8081/health
echo    Backend:    http://localhost:8080/health
echo    UI:         http://localhost:5173
echo ==========================================
endlocal
