@echo off
echo ==========================================
echo Starting All App-Bana Services
echo ==========================================

echo [1/3] Starting AI-Builder... (This will kill old java processes, start Qdrant, compile code, and run on port 8081)
start "AI Builder" cmd /k "start-ai-builder.bat"

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
start "AppBana Backend" cmd /k "cd app-bana-service && echo Starting Backend Server... && java -jar target\app-bana-1.0-SNAPSHOT-fat.jar"

echo [3/3] Starting Frontend App-Bana UI... (Vite Dev Server)
start "AppBana UI" cmd /k "cd app-bana-ui && echo Starting Frontend UI... && npm run dev"

echo All windows have been launched! Ensure they start up successfully.
