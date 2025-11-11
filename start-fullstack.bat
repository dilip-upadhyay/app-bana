@echo off
REM AppBana Full Stack Start Script
REM Starts both backend and frontend in separate windows

echo ==========================================
echo AppBana Full Stack Startup
echo ==========================================
echo.

REM Check if we're in the right directory
if not exist "pom.xml" (
    echo ERROR: pom.xml not found!
    echo Please run this script from the project root directory.
    pause
    exit /b 1
)

echo [1/3] Killing any running Java processes...
powershell -Command "Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force"
timeout /t 2 /nobreak >nul

echo [2/3] Building backend...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo.
    echo ERROR: Build failed!
    pause
    exit /b 1
)

echo [3/3] Starting services...
echo.

REM Start backend in new window
start "AppBana Backend" cmd /c "cd app-bana-service && echo Starting Backend Server... && echo http://localhost:8080 && java -jar target\app-bana-1.0-SNAPSHOT-fat.jar"

REM Wait a bit for backend to start
timeout /t 3 /nobreak >nul

REM Start frontend dev server in new window
start "AppBana Frontend" cmd /c "cd app-bana-ui && echo Starting Frontend Dev Server... && echo http://localhost:5173/studio.html && npm run dev"

echo.
echo ==========================================
echo Services Starting:
echo   Backend:  http://localhost:8080
echo   Frontend: http://localhost:5173/studio.html
echo.
echo Check the separate windows for logs.
echo Close those windows to stop the services.
echo ==========================================
echo.
pause
