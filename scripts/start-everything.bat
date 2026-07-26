@echo off
REM =====================================================================
REM start-everything.bat  -  Restart all AppBana services
REM
REM Orchestrates the four per-module scripts in the correct order.
REM Each module script is fully self-contained (stops old, ensures deps,
REM builds if needed, launches). This script simply chains them and
REM waits for each service to be reachable before starting the next.
REM
REM Order:
REM   1. AI Builder  (port 8081)  <- also brings up Qdrant + PostgreSQL
REM   2. Backend     (port 8080)
REM   3. Studio      (port 5174)
REM   4. Runtime     (port 5175)
REM =====================================================================
setlocal EnableDelayedExpansion

REM Resolve scripts folder and repo root regardless of where invoked
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%.."

if not exist "pom.xml" (
    echo ERROR: could not locate repo root -- pom.xml missing.
    exit /b 1
)

REM --- Pre-flight: verify all required tools before spawning windows --
echo Checking required tools...
set "MISSING="
where java   >nul 2>&1 || set "MISSING=!MISSING! java"
where mvn    >nul 2>&1 || set "MISSING=!MISSING! mvn"
where docker >nul 2>&1 || set "MISSING=!MISSING! docker"
where node   >nul 2>&1 || set "MISSING=!MISSING! node"
where npm    >nul 2>&1 || set "MISSING=!MISSING! npm"
if not "!MISSING!"=="" (
    echo ERROR: missing required tools on PATH:!MISSING!
    echo Install them and retry. See docs/guides/02-DEVELOPMENT_GUIDE.md.
    exit /b 1
)
docker info >nul 2>&1
if !ERRORLEVEL! NEQ 0 (
    echo ERROR: Docker daemon is not running. Start Docker Desktop and retry.
    exit /b 1
)
echo    All required tools present. Docker daemon is running.

echo ==========================================
echo Starting All AppBana Services
echo ==========================================

echo [1/4] Launching AI Builder in a new window...
start "AI Builder" cmd /c ""%SCRIPT_DIR%start-ai-builder.bat""

echo    Waiting for AI Builder to be ready on port 8081...
:waitAiPort
netstat -ano | findstr ":8081 " | findstr "LISTENING" >nul
if !ERRORLEVEL! NEQ 0 (
    ping 127.0.0.1 -n 3 >nul
    goto waitAiPort
)
echo    AI Builder is up.

echo [2/4] Launching Backend in a new window...
start "AppBana Backend" cmd /c ""%SCRIPT_DIR%start-backend.bat""

echo    Waiting for Backend to be ready on port 8080...
:waitBePort
netstat -ano | findstr ":8080 " | findstr "LISTENING" >nul
if !ERRORLEVEL! NEQ 0 (
    ping 127.0.0.1 -n 3 >nul
    goto waitBePort
)
echo    Backend is up.

echo [3/4] Launching Studio in a new window...
start "AppBana Studio" cmd /c ""%SCRIPT_DIR%start-studio.bat""

echo    Waiting for Studio to be ready on port 5174...
:waitStudioPort
netstat -ano | findstr ":5174 " | findstr "LISTENING" >nul
if !ERRORLEVEL! NEQ 0 (
    ping 127.0.0.1 -n 3 >nul
    goto waitStudioPort
)
echo    Studio is up.

echo [4/4] Launching Runtime in a new window...
start "AppBana Runtime" cmd /c ""%SCRIPT_DIR%start-runtime.bat""

echo    Waiting for Runtime to be ready on port 5175...
:waitRuntimePort
netstat -ano | findstr ":5175 " | findstr "LISTENING" >nul
if !ERRORLEVEL! NEQ 0 (
    ping 127.0.0.1 -n 3 >nul
    goto waitRuntimePort
)
echo    Runtime is up.

echo ==========================================
echo All services launched:
echo    AI Builder: http://localhost:8081/health
echo    Backend:    http://localhost:8080/health
echo    Studio:     http://localhost:5174
echo    Runtime:    http://localhost:5175
echo ==========================================
endlocal
