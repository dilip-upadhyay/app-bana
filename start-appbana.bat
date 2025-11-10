@echo off
REM Stop any running Java backend (app-bana-service)
for /f "tokens=2" %%i in ('netstat -ano ^| findstr :8080') do (
    for /f "tokens=1" %%p in ('tasklist /FI "PID eq %%i" ^| findstr java') do (
        echo Stopping Java process PID %%i
        taskkill /PID %%i /F
    )
)

REM Build backend
call app-bana-service\mvnw -DskipTests package

REM Start backend
start "AppBana Backend" java -jar app-bana-service\target\app-bana-1.0-SNAPSHOT-shaded.jar

REM Start frontend (Vite dev server)
cd app-bana-ui
start "AppBana UI" cmd /c "npm run dev"
cd ..

echo AppBana backend and UI started.
pause
