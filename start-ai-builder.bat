@echo off
setlocal EnableDelayedExpansion

echo ======================================================
echo Starting AI Builder Service...
echo ======================================================

:: Load environment variables from .env if it exists
if exist "ai-builder\.env" (
    echo Loading environment from ai-builder\.env
    for /f "tokens=* delims=" %%A in (ai-builder\.env) do (
        set "line=%%A"
        if not "!line:~0,1!"=="#" (
            set "!line!"
        )
    )
)

:: Check if OPENAI_API_KEY is set (fetch from local/system registry if missing in short-lived cmd session)
if "%OPENAI_API_KEY%"=="" (
    for /f "tokens=2*" %%A in ('reg query HKEY_CURRENT_USER\Environment /v OPENAI_API_KEY 2^>nul') do set "OPENAI_API_KEY=%%B"
)
if "%OPENAI_API_KEY%"=="" (
    for /f "tokens=2*" %%A in ('reg query "HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment" /v OPENAI_API_KEY 2^>nul') do set "OPENAI_API_KEY=%%B"
)

:: Fallback if user named variable 'OPEN_API_KEY' instead of 'OPENAI_API_KEY'
if "%OPENAI_API_KEY%"=="" (
    if not "%OPEN_API_KEY%"=="" set "OPENAI_API_KEY=%OPEN_API_KEY%"
)
if "%OPENAI_API_KEY%"=="" (
    for /f "tokens=2*" %%A in ('reg query HKEY_CURRENT_USER\Environment /v OPEN_API_KEY 2^>nul') do set "OPENAI_API_KEY=%%B"
)
if "%OPENAI_API_KEY%"=="" (
    for /f "tokens=2*" %%A in ('reg query "HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment" /v OPEN_API_KEY 2^>nul') do set "OPENAI_API_KEY=%%B"
)

if "%OPENAI_API_KEY%"=="" (
    echo ERROR: OPENAI_API_KEY environment variable is not set
    echo Please set it globally or in ai-builder\.env
    exit /b 1
)

:: Define port
if "%AI_PORT%"=="" set AI_PORT=8081

:: Check if port is in use and kill process
echo Checking if port %AI_PORT% is in use...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":%AI_PORT% "') do (
    echo Found process %%a running on port %AI_PORT%. Stopping it...
    taskkill /F /PID %%a >nul 2>&1
)

:: Check if Qdrant is running
echo Checking Qdrant status...
if "%QDRANT_HOST%"=="" set "QDRANT_HOST=localhost"
if "%QDRANT_HTTP_PORT%"=="" set "QDRANT_HTTP_PORT=6333"
if "%QDRANT_PORT%"=="" set "QDRANT_PORT=6334"

curl -s "http://%QDRANT_HOST%:%QDRANT_HTTP_PORT%/health" >nul 2>&1
if !ERRORLEVEL! EQU 0 (
    echo Qdrant is already running on %QDRANT_HOST%:%QDRANT_HTTP_PORT%
) else (
    echo Qdrant is not running. Starting Qdrant container...
    
    :: Check if Docker is installed
    docker --version >nul 2>&1
    if !ERRORLEVEL! NEQ 0 (
        echo ERROR: Docker is not installed. Please install Docker first.
        exit /b 1
    )
    
    :: Check if Qdrant container already exists
    docker ps -a --format "{{.Names}}" | findstr "^qdrant$" >nul 2>&1
    if !ERRORLEVEL! EQU 0 (
        echo Qdrant container exists. Starting it...
        docker start qdrant
    ) else (
        echo Creating and starting new Qdrant container...
        docker run -d --name qdrant -p %QDRANT_HTTP_PORT%:6333 -p %QDRANT_PORT%:6334 -v "%CD%\qdrant_storage:/qdrant/storage" qdrant/qdrant
    )
    
    :: Wait for Qdrant to be ready
    echo Waiting for Qdrant to be ready...
    set ready=0
    for /l %%i in (1, 1, 30) do (
        curl -s "http://%QDRANT_HOST%:%QDRANT_HTTP_PORT%/health" >nul 2>&1
        if !ERRORLEVEL! EQU 0 (
            echo Qdrant is ready!
            set ready=1
            goto break_loop
        )
        timeout /t 1 /nobreak >nul
    )
    :break_loop
    if !ready! EQU 0 (
        echo ERROR: Qdrant failed to start after 30 seconds
        exit /b 1
    )
)

echo [1/3] Killing any potentially locking processes...
powershell -Command "Stop-Process -Name java -Force -ErrorAction SilentlyContinue; Stop-Process -Name mvn -Force -ErrorAction SilentlyContinue"
timeout /t 2 /nobreak >nul

echo [2/3] Building all modules (resilient mode)...
REM We build from root to ensure both app-bana-service and ai-builder are updated correctly
call mvn install -DskipTests
if !ERRORLEVEL! NEQ 0 (
    echo.
    echo WARNING: Full build failed, checking for existing artifacts...
    if not exist "ai-builder\target\ai-builder-1.0-SNAPSHOT-fat.jar" (
        echo ERROR: Critical artifacts missing. Please close any programs using the target folder and try again.
        exit /b 1
    )
)

echo [3/3] Build successful or artifacts verified!

:: Database configuration
set "DATABASE_URL=jdbc:postgresql://localhost:5432/appbana"
set "DATABASE_USER=appbana"
set "DATABASE_PASSWORD=appbana_dev_2026"

:: Run the service
echo Starting AI Builder server on port %AI_PORT%...
echo Health check: http://localhost:%AI_PORT%/health
echo Chat API: http://localhost:%AI_PORT%/api/ai/chat
echo Database: %DATABASE_URL% (user: %DATABASE_USER%)
echo.
echo Press Ctrl+C to stop the service
echo ======================================================

set "DATABASE_URL=%DATABASE_URL%"
set "DATABASE_USER=%DATABASE_USER%"
set "DATABASE_PASSWORD=%DATABASE_PASSWORD%"

cd ai-builder
java -jar target\ai-builder-1.0-SNAPSHOT-fat.jar

endlocal
