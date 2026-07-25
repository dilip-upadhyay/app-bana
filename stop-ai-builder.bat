@echo off
setlocal EnableDelayedExpansion

REM Stop AI Builder Service (Windows)
REM Stops the Qdrant container and kills the AI Builder Java process

echo ==========================================
echo Stopping AI Builder Services...
echo ==========================================
echo.

REM Stop Qdrant container
docker ps --format "{{.Names}}" | findstr /X "qdrant" >nul 2>&1
if !ERRORLEVEL! EQU 0 (
    echo Stopping Qdrant container...
    docker stop qdrant
    echo Qdrant stopped.
) else (
    echo Qdrant container is not running.
)
echo.

REM Kill AI Builder Java process (port 8081)
echo Stopping AI Builder process on port 8081...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8081 "') do (
    tasklist /FI "PID eq %%a" | findstr java >nul 2>&1
    if !ERRORLEVEL! EQU 0 (
        echo   Stopping Java process PID %%a...
        taskkill /PID %%a /F >nul 2>&1
        echo   AI Builder stopped.
        goto :ai_done
    )
)
echo   AI Builder is not running.
:ai_done
echo.
echo All AI Builder services stopped.
echo.
endlocal
