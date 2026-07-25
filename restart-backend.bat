@echo off
setlocal EnableDelayedExpansion

REM AppBana Backend Restart Script (Windows)
REM Manages Docker for PostgreSQL, stops the running backend, rebuilds, and restarts it

echo ==========================================
echo Restarting AppBana Backend...
echo ==========================================
echo.

set POSTGRES_CONTAINER_NAME=appbana-postgres
set POSTGRES_VERSION=16-alpine
set POSTGRES_PORT=5432
set POSTGRES_DB=appbana
set POSTGRES_USER=appbana
set POSTGRES_PASSWORD=appbana_dev_2026

REM -----------------------------------------------
REM Step 1: Check/Start PostgreSQL Docker container
REM -----------------------------------------------
echo [1/4] Checking PostgreSQL Docker container...

docker ps --format "{{.Names}}" | findstr /X "%POSTGRES_CONTAINER_NAME%" >nul 2>&1
if !ERRORLEVEL! EQU 0 (
    echo   PostgreSQL container already running.
) else (
    docker ps -a --format "{{.Names}}" | findstr /X "%POSTGRES_CONTAINER_NAME%" >nul 2>&1
    if !ERRORLEVEL! EQU 0 (
        echo   Starting existing PostgreSQL container...
        docker start %POSTGRES_CONTAINER_NAME%
        timeout /t 3 /nobreak >nul
        echo   PostgreSQL container started.
    ) else (
        echo   Creating new PostgreSQL container...
        docker run -d ^
            --name %POSTGRES_CONTAINER_NAME% ^
            -e POSTGRES_DB=%POSTGRES_DB% ^
            -e POSTGRES_USER=%POSTGRES_USER% ^
            -e POSTGRES_PASSWORD=%POSTGRES_PASSWORD% ^
            -p %POSTGRES_PORT%:5432 ^
            -v appbana-postgres-data:/var/lib/postgresql/data ^
            postgres:%POSTGRES_VERSION%
        echo   Waiting for PostgreSQL to be ready...
        timeout /t 5 /nobreak >nul
        set pg_ready=0
        for /l %%i in (1, 1, 30) do (
            docker exec %POSTGRES_CONTAINER_NAME% pg_isready -U %POSTGRES_USER% >nul 2>&1
            if !ERRORLEVEL! EQU 0 (
                echo   PostgreSQL is ready!
                set pg_ready=1
                goto :pg_done
            )
            timeout /t 1 /nobreak >nul
        )
        :pg_done
        if !pg_ready! EQU 0 (
            echo   ERROR: PostgreSQL failed to start within 30 seconds!
            exit /b 1
        )
    )
)
echo   PostgreSQL: localhost:%POSTGRES_PORT% / DB: %POSTGRES_DB% / User: %POSTGRES_USER%
echo.

REM -----------------------------------------------
REM Step 2: Stop running backend
REM -----------------------------------------------
echo [2/4] Stopping any running backend on port 8080...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8080 "') do (
    tasklist /FI "PID eq %%a" | findstr java >nul 2>&1
    if !ERRORLEVEL! EQU 0 (
        echo   Stopping Java process PID %%a...
        taskkill /PID %%a /F >nul 2>&1
    )
)
timeout /t 2 /nobreak >nul
echo   Backend stopped (or was not running).
echo.

REM -----------------------------------------------
REM Step 3: Build JAR
REM -----------------------------------------------
echo [3/4] Building backend JAR...
cd app-bana-service
call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo   ERROR: Build failed!
    cd ..
    exit /b 1
)
cd ..

if not exist "app-bana-service\target\app-bana-1.0-SNAPSHOT-fat.jar" (
    echo   ERROR: Build failed - JAR not found!
    exit /b 1
)
echo   Build successful.
echo.

REM -----------------------------------------------
REM Step 4: Start backend in a new window
REM -----------------------------------------------
echo [4/4] Starting backend service...
start "AppBana Backend" cmd /c "cd app-bana-service && echo Starting backend on http://localhost:8080 && java -jar target\app-bana-1.0-SNAPSHOT-fat.jar"
timeout /t 3 /nobreak >nul

echo   Backend starting in new window.
echo   URL: http://localhost:8080
echo   Health: http://localhost:8080/health
echo.
echo Backend restart complete!
echo.
endlocal
