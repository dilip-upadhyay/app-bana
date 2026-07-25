@echo off
REM Start AppBana UI dev server on port 5190 (Windows)
REM Equivalent of start-dev.sh (which runs: UI_PORT=5190 ./run-ui.sh dev)

set UI_PORT=5190
call run-ui.bat dev
