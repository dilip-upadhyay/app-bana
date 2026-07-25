@echo off
setlocal EnableDelayedExpansion
echo ==========================================
echo Starting All App-Bana Services
echo ==========================================

REM -----------------------------------------------
REM Pre-flight: Check OPENAI_API_KEY
REM -----------------------------------------------
set "OPENAI_API_KEY="
for /f "tokens=2*" %%A in ('reg query HKEY_CURRENT_USER\Environment /v OPENAI_API_KEY 2^>nul') do set "OPENAI_API_KEY=%%B"
if "!OPENAI_API_KEY!"=="" (
    for /f "tokens=2*" %%A in ('reg query "HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment" /v OPENAI_API_KEY 2^>nul') do set "OPENAI_API_KEY=%%B"
)
if "!OPENAI_API_KEY!"=="" (
    if exist "ai-builder\.env" (
        for /f "tokens=1* delims==" %%A in ('findstr /I "OPENAI_API_KEY" ai-builder\.env') do set "OPENAI_API_KEY=%%B"
    )
)
if "!OPENAI_API_KEY!"=="" (
    echo.
    echo ==========================================
    echo  ERROR: OPENAI_API_KEY is not set!
    echo ==========================================
    echo  The AI Builder requires an OpenAI API key.
    echo  Set it permanently by running in a NEW terminal:
    echo.
    echo    setx OPENAI_API_KEY "sk-your-key-here"
    echo.
    echo  Then close ALL terminals and run start-everything.bat again.
    echo  OR create ai-builder\.env with:  OPENAI_API_KEY=sk-your-key-here
    echo ==========================================
    echo.
    pause
    exit /b 1
)
echo   OPENAI_API_KEY: found (starts with !OPENAI_API_KEY:~0,7!...)
echo.

REM -----------------------------------------------
REM Pre-flight: Ensure Docker containers are running
REM -----------------------------------------------
echo Ensuring Docker dependencies are running...

REM PostgreSQL
docker ps --format "{{.Names}}" | findstr /X "appbana-postgres" >nul 2>&1
if !ERRORLEVEL! NEQ 0 (
    docker ps -a --format "{{.Names}}" | findstr /X "appbana-postgres" >nul 2>&1
    if !ERRORLEVEL! EQU 0 (
        echo   Starting PostgreSQL container...
        docker start appbana-postgres >nul
    ) else (
        echo   Creating PostgreSQL container...
        docker run -d --name appbana-postgres -e POSTGRES_DB=appbana -e POSTGRES_USER=appbana -e POSTGRES_PASSWORD=appbana_dev_2026 -p 5432:5432 -v appbana-postgres-data:/var/lib/postgresql/data postgres:16-alpine >nul
    )
    timeout /t 3 /nobreak >nul
)
echo   PostgreSQL: running on port 5432

REM Qdrant
docker ps --format "{{.Names}}" | findstr /X "qdrant" >nul 2>&1
if !ERRORLEVEL! NEQ 0 (
    docker ps -a --format "{{.Names}}" | findstr /X "qdrant" >nul 2>&1
    if !ERRORLEVEL! EQU 0 (
        echo   Starting Qdrant container...
        docker start qdrant >nul
    ) else (
        echo   Creating Qdrant container...
        docker run -d --name qdrant -p 6333:6333 -p 6334:6334 -v "%CD%\qdrant_storage:/qdrant/storage" qdrant/qdrant >nul
    )
    timeout /t 3 /nobreak >nul
)
echo   Qdrant: running on port 6333
echo.

echo [0/3] Stopping existing Java and Node processes to avoid race conditions...
powershell -Command "Stop-Process -Name java -Force -ErrorAction SilentlyContinue; Stop-Process -Name node -Force -ErrorAction SilentlyContinue"

echo Cleaning up orphaned command windows...
taskkill /F /FI "WINDOWTITLE eq AI Builder*" /T >nul 2>&1
taskkill /F /FI "WINDOWTITLE eq AppBana Backend*" /T >nul 2>&1
taskkill /F /FI "WINDOWTITLE eq AppBana UI*" /T >nul 2>&1

ping 127.0.0.1 -n 3 > nul

echo [1/3] Starting AI-Builder... (This will start Qdrant, compile code, and run on port 8081)
start "AI Builder" cmd /c "start-ai-builder.bat"

echo Waiting for the AI Builder (and the Maven build) to finish starting up...
:waitForPort
netstat -ano | findstr :8081 >nul
if %errorlevel% neq 0 (
    ping 127.0.0.1 -n 3 > nul
    goto waitForPort
)
ping 127.0.0.1 -n 3 > nul
echo AI Builder is initialized. Now starting Backend!

echo [2/3] Starting Main App-Bana Backend... (port 8080)
start "AppBana Backend" cmd /c "cd app-bana-service && echo Starting Backend Server... && java -jar target\app-bana-1.0-SNAPSHOT-fat.jar"

echo [3/3] Starting Frontend App-Bana UI... (Vite Dev Server)
start "AppBana UI" cmd /c "cd app-bana-ui && echo Starting Frontend UI... && npm run dev"

echo All windows have been launched! Ensure they start up successfully.
