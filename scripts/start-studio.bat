@echo off
REM =====================================================================
REM start-studio.bat  -  Start the AI-native Studio on port 5174
REM =====================================================================
setlocal EnableDelayedExpansion
cd /d "%~dp0.."

set "STUDIO_PORT=5174"

echo ==========================================
echo [studio] Starting on port %STUDIO_PORT%
echo ==========================================

REM Ensure pnpm is available
where pnpm >nul 2>&1 || (
    echo Installing pnpm...
    npm install -g pnpm >nul 2>&1
)

REM Kill any existing process on port 5174
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":%STUDIO_PORT% " ^| findstr "LISTENING"') do (
    echo Stopping existing process on port %STUDIO_PORT% (PID %%a)
    taskkill /F /PID %%a >nul 2>&1
)

REM Install workspace deps if needed
if not exist "node_modules" (
    echo Installing dependencies...
    pnpm install --ignore-scripts
)

echo Starting AppBana Studio at http://localhost:%STUDIO_PORT%
cd app-bana-studio && npx vite --port %STUDIO_PORT%
