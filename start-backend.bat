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

echo [1/4] Killing any running Java processes...
powershell -Command "Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force"
timeout /t 2 /nobreak >nul

echo [2/4] Building project from root...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo.
    echo ERROR: Build failed!
    pause
    exit /b 1
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
