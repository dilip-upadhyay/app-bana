@echo off
setlocal EnableDelayedExpansion

REM AI Builder Development Script (Windows) - with notes on hot reload
REM Equivalent of dev-ai-builder.sh

echo ==========================================
echo Starting AI Builder in development mode...
echo ==========================================
echo.

REM Load environment from .env if it exists
if exist "ai-builder\.env" (
    echo Loading environment from ai-builder\.env
    for /f "tokens=* delims=" %%A in (ai-builder\.env) do (
        set "line=%%A"
        if not "!line:~0,1!"=="#" (
            set "!line!"
        )
    )
)

REM Check for OPENAI_API_KEY (env, registry fallbacks)
if "%OPENAI_API_KEY%"=="" (
    for /f "tokens=2*" %%A in ('reg query HKEY_CURRENT_USER\Environment /v OPENAI_API_KEY 2^>nul') do set "OPENAI_API_KEY=%%B"
)
if "%OPENAI_API_KEY%"=="" (
    for /f "tokens=2*" %%A in ('reg query "HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment" /v OPENAI_API_KEY 2^>nul') do set "OPENAI_API_KEY=%%B"
)
if "%OPENAI_API_KEY%"=="" (
    echo ERROR: OPENAI_API_KEY environment variable is not set.
    echo Set it with:  setx OPENAI_API_KEY "sk-your-key-here"
    echo Or create ai-builder\.env with: OPENAI_API_KEY=sk-your-key-here
    exit /b 1
)

REM Start Qdrant if not running
set QDRANT_HOST=localhost
set QDRANT_HTTP_PORT=6333
set QDRANT_PORT=6334

echo Checking Qdrant status...
docker ps --format "{{.Names}}" | findstr /X "qdrant" >nul 2>&1
if !ERRORLEVEL! EQU 0 (
    echo Qdrant already running.
) else (
    docker ps -a --format "{{.Names}}" | findstr /X "qdrant" >nul 2>&1
    if !ERRORLEVEL! EQU 0 (
        echo Starting existing Qdrant container...
        docker start qdrant
    ) else (
        echo Creating new Qdrant container...
        docker run -d --name qdrant -p %QDRANT_HTTP_PORT%:6333 -p %QDRANT_PORT%:6334 -v "%CD%\qdrant_storage:/qdrant/storage" qdrant/qdrant
    )
    timeout /t 3 /nobreak >nul
)
echo.

REM Database config
set DATABASE_URL=jdbc:postgresql://localhost:5432/appbana
set DATABASE_USER=appbana
set DATABASE_PASSWORD=appbana_dev_2026

echo Database: %DATABASE_URL% (user: %DATABASE_USER%)
echo Starting in development mode (code changes require restart)...
echo Press Ctrl+C to stop.
echo.

cd ai-builder
mvn compile exec:java -Dexec.mainClass="com.appbana.ai.AiBuilderMain"

endlocal
