@echo off
REM =====================================================================
REM start-ai-builder.bat  -  Restart the AI Builder service on port 8081
REM
REM What it does:
REM   1. Stops any AI Builder / Qdrant process already running
REM   2. Ensures Docker dependencies (Qdrant, PostgreSQL) are up
REM   3. Ensures OPENAI_API_KEY is set
REM   4. Builds the ai-builder module (with its parent deps)
REM   5. Launches the service on port 8081
REM =====================================================================
setlocal EnableDelayedExpansion

REM Always run from repo root, regardless of where the script is invoked
cd /d "%~dp0.."

set "AI_PORT=8081"
set "QDRANT_HTTP_PORT=6333"
set "QDRANT_GRPC_PORT=6334"
set "PG_PORT=5432"

echo ==========================================
echo [ai-builder] Restarting on port %AI_PORT%
echo ==========================================

REM --- Pre-flight: required tools -------------------------------------
where java >nul 2>&1 || (echo ERROR: Java JDK not found on PATH. Install JDK 21+ and retry. & exit /b 1)
where mvn  >nul 2>&1 || (echo ERROR: Maven not found on PATH. Install Apache Maven 3.9+ and retry. & exit /b 1)

REM --- Step 1: stop any existing AI Builder process on port 8081 -------
echo [1/5] Stopping any existing AI Builder process on port %AI_PORT%...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":%AI_PORT% " ^| findstr "LISTENING"') do (
    echo    Killing PID %%a
    taskkill /F /PID %%a >nul 2>&1
)

REM --- Step 2: ensure OPENAI_API_KEY is set ----------------------------
echo [2/5] Checking OPENAI_API_KEY...
if "%OPENAI_API_KEY%"=="" (
    for /f "tokens=2*" %%A in ('reg query HKEY_CURRENT_USER\Environment /v OPENAI_API_KEY 2^>nul') do set "OPENAI_API_KEY=%%B"
)
if "!OPENAI_API_KEY!"=="" (
    if exist "ai-builder\.env" (
        for /f "tokens=1* delims==" %%A in ('findstr /I "^OPENAI_API_KEY" ai-builder\.env') do set "OPENAI_API_KEY=%%B"
    )
)
if "!OPENAI_API_KEY!"=="" (
    echo    ERROR: OPENAI_API_KEY is not set.
    echo    Set it with:  setx OPENAI_API_KEY "sk-your-key-here"
    echo    Or create ai-builder\.env with: OPENAI_API_KEY=sk-your-key-here
    exit /b 1
)
echo    OPENAI_API_KEY found (starts with !OPENAI_API_KEY:~0,7!...)

REM --- Step 3: ensure Docker dependencies (PostgreSQL + Qdrant) --------
echo [3/5] Ensuring Docker dependencies are running...
docker --version >nul 2>&1
if !ERRORLEVEL! NEQ 0 (
    echo    ERROR: Docker is not installed or not on PATH.
    exit /b 1
)
docker info >nul 2>&1
if !ERRORLEVEL! NEQ 0 (
    echo    ERROR: Docker daemon is not running. Start Docker Desktop and retry.
    exit /b 1
)

REM PostgreSQL -- try to start; if container missing, create it
docker start appbana-postgres >nul 2>&1
if !ERRORLEVEL! NEQ 0 (
    echo    Creating PostgreSQL container...
    docker run -d --name appbana-postgres -e POSTGRES_DB=appbana -e POSTGRES_USER=appbana -e POSTGRES_PASSWORD=appbana_dev_2026 -p %PG_PORT%:5432 -v appbana-postgres-data:/var/lib/postgresql/data postgres:16-alpine >nul
    timeout /t 3 /nobreak >nul
)
echo    PostgreSQL: running on port %PG_PORT%

REM Qdrant -- try to start; if container missing, create it
docker start qdrant >nul 2>&1
if !ERRORLEVEL! NEQ 0 (
    echo    Creating Qdrant container...
    docker run -d --name qdrant -p %QDRANT_HTTP_PORT%:6333 -p %QDRANT_GRPC_PORT%:6334 -v "%CD%\qdrant_storage:/qdrant/storage" qdrant/qdrant >nul
    timeout /t 3 /nobreak >nul
)
echo    Qdrant: running on port %QDRANT_HTTP_PORT%

REM --- Step 4: build the module ----------------------------------------
echo [4/5] Building ai-builder module...
call mvn -q -pl ai-builder -am -DskipTests install
if !ERRORLEVEL! NEQ 0 (
    if not exist "ai-builder\target\ai-builder-1.0-SNAPSHOT-fat.jar" (
        echo    ERROR: Build failed and no existing jar found.
        exit /b 1
    )
    echo    WARNING: Build failed, using existing jar.
)

REM --- Step 5: launch --------------------------------------------------
echo [5/5] Launching AI Builder on port %AI_PORT%...
echo    Health: http://localhost:%AI_PORT%/health
echo    Chat:   http://localhost:%AI_PORT%/api/ai/chat
echo    Press Ctrl+C to stop.
echo ==========================================

set "DATABASE_URL=jdbc:postgresql://localhost:%PG_PORT%/appbana"
set "DATABASE_USER=appbana"
set "DATABASE_PASSWORD=appbana_dev_2026"

cd ai-builder
java -jar target\ai-builder-1.0-SNAPSHOT-fat.jar
endlocal
