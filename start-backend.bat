@echo off
REM AppBana Backend Start Script
REM Always build from root, run from service directory

echo ==========================================
echo AppBana Backend Start Script
echo ==========================================
echo.

REM Check if we're in the right directory
if not exist "pom.xml" (
    echo ERROR: pom.xml not found!
    echo Please run this script from the project root directory.
    echo Current directory: %CD%
    pause
    exit /b 1
)

echo [1/4] Killing any potentially locking processes...
powershell -Command "Stop-Process -Name java -Force -ErrorAction SilentlyContinue; Stop-Process -Name mvn -Force -ErrorAction SilentlyContinue"
timeout /t 2 /nobreak >nul

echo [2/4] Building project (resilient mode)...
REM If cleaning fails due to locks, we still try to install to ensure correct artifacts
call mvn install -DskipTests
if errorlevel 1 (
    echo.
    echo WARNING: Full build failed, attempting to proceed with existing artifacts...
    if not exist "app-bana-service\target\app-bana-1.0-SNAPSHOT-fat.jar" (
        echo ERROR: Critical artifacts missing. Please close any programs using the target folder and try again.
        pause
        exit /b 1
    )
)

echo.
echo [3/4] Changing to service directory...
cd app-bana-service
if errorlevel 1 (
    echo ERROR: Could not change to app-bana-service directory!
    pause
    exit /b 1
)

echo [4/4] Starting backend server in new terminal...
echo.
echo ==========================================
echo Server will start on http://localhost:8080
echo A new terminal window will open
echo Close that window to stop the server
echo ==========================================
echo.

start "AppBana Backend" cmd /k "cd /d %CD% && java -jar target\app-bana-1.0-SNAPSHOT-fat.jar"

echo.
echo Backend server started in new terminal window.
echo.
