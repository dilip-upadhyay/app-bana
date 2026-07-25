@echo off
REM Run AppBana UI (Windows)
REM Equivalent of run-ui.sh
REM
REM Usage:
REM   run-ui.bat           - start dev server (default)
REM   run-ui.bat dev       - start dev server
REM   run-ui.bat build     - production build
REM   run-ui.bat preview   - serve production build
REM   run-ui.bat clean     - remove node_modules and dist
REM
REM Environment variables:
REM   UI_PORT=5173         - override dev server port (default 5173)
REM   UI_DIR=app-bana-ui   - override UI directory

setlocal EnableDelayedExpansion

set "ACTION=%~1"
if "%ACTION%"=="" set "ACTION=dev"

if "%UI_DIR%"=="" set "UI_DIR=app-bana-ui"
if "%UI_PORT%"=="" set "UI_PORT=5173"

if not exist "%UI_DIR%" (
    echo ERROR: UI directory not found: %UI_DIR%
    exit /b 1
)

cd "%UI_DIR%"

if "%ACTION%"=="clean" (
    echo Cleaning node_modules and dist...
    if exist node_modules rmdir /s /q node_modules
    if exist dist rmdir /s /q dist
    echo Clean complete.
    goto :end
)

REM Install dependencies
if exist package-lock.json (
    echo Installing dependencies with npm ci...
    call npm ci
) else (
    echo Installing dependencies with npm install...
    call npm install
)
if errorlevel 1 (
    echo ERROR: npm install failed!
    exit /b 1
)

if "%ACTION%"=="build" (
    echo Building production assets...
    call npm run build
    goto :end
)

if "%ACTION%"=="preview" (
    echo Starting production preview server on port %UI_PORT%...
    call npm run preview -- --port %UI_PORT%
    goto :end
)

REM Default: dev
echo Starting Vite dev server on port %UI_PORT%...
call npm run dev -- --port %UI_PORT%

:end
endlocal
