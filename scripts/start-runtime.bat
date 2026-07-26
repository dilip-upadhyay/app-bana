@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "ROOT_DIR=%SCRIPT_DIR%.."
set "PORT=5175"

echo [start-runtime] Starting AppBana Runtime on port %PORT%...

:: Kill anything on the port
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":%PORT% "') do (
    taskkill /PID %%a /F >nul 2>&1
)

cd /d "%ROOT_DIR%\app-bana-runtime"

:: Ensure node_modules
if not exist "node_modules" (
    echo [start-runtime] Installing dependencies...
    cd /d "%ROOT_DIR%"
    call pnpm install --ignore-scripts
    cd /d "%ROOT_DIR%\app-bana-runtime"
)

echo [start-runtime] Launching Vite dev server on port %PORT%...
cd /d "%ROOT_DIR%"
call pnpm -C app-bana-runtime exec vite --port %PORT%
