@echo off
REM =====================================================================
REM start-ui.bat  -  Restart the AppBana Studio UI on port 5173
REM
REM What it does:
REM   1. Stops any Vite dev server already running on port 5173
REM   2. Ensures Node dependencies are installed (npm install if missing)
REM   3. Launches the Vite dev server
REM =====================================================================
setlocal EnableDelayedExpansion

REM Always run from repo root, regardless of where the script is invoked
cd /d "%~dp0.."

set "UI_PORT=5173"
set "UI_DIR=app-bana-ui"

echo ==========================================
echo [ui] Restarting on port %UI_PORT%
echo ==========================================

REM --- Step 1: stop any existing process on UI_PORT --------------------
echo [1/3] Stopping any existing UI process on port %UI_PORT%...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":%UI_PORT% " ^| findstr "LISTENING"') do (
    echo    Killing PID %%a
    taskkill /F /PID %%a >nul 2>&1
)

REM --- Step 2: ensure node dependencies --------------------------------
echo [2/3] Ensuring Node dependencies are installed...
where node >nul 2>&1
if !ERRORLEVEL! NEQ 0 (
    echo    ERROR: Node.js is not installed or not on PATH.
    exit /b 1
)
where npm >nul 2>&1
if !ERRORLEVEL! NEQ 0 (
    echo    ERROR: npm is not installed or not on PATH.
    exit /b 1
)
if not exist "%UI_DIR%\node_modules" (
    echo    Installing dependencies -- this may take a minute...
    pushd "%UI_DIR%"
    if exist package-lock.json (
        call npm ci
    ) else (
        call npm install
    )
    popd
    if !ERRORLEVEL! NEQ 0 (
        echo    ERROR: npm install failed.
        exit /b 1
    )
)
echo    node_modules: present

REM --- Step 3: launch --------------------------------------------------
if not exist "logs" mkdir logs
set "LOG_FILE=%CD%\logs\ui.log"
echo [3/3] Launching Vite dev server on port %UI_PORT%...
echo    URL: http://localhost:%UI_PORT%
echo    Log: %LOG_FILE%
echo    Press Ctrl+C to stop.
echo ==========================================

cd "%UI_DIR%"
powershell -NoProfile -Command "& { & npm run dev 2>&1 | Tee-Object -FilePath '%LOG_FILE%' }"
endlocal
