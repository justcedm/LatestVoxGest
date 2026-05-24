@echo off
setlocal
cd /d "%~dp0"

echo ============================================================
echo  VoxGest Recorder Pack Setup
echo ============================================================
echo.
echo This setup requires Python 3.11 exactly.
echo It uses: py -3.11
echo.

echo [1/4] Checking Python 3.11...
py -3.11 --version
if errorlevel 1 (
    echo.
    echo ERROR: Python 3.11 was not found.
    echo Install Python 3.11 from https://www.python.org/downloads/
    echo Make sure the Python launcher is installed.
    pause
    exit /b 1
)

echo.
echo [2/4] Creating local recorder_env...
py -3.11 -m venv recorder_env
if errorlevel 1 goto FAIL

if not exist recorder_env\Scripts\python.exe (
    echo ERROR: recorder_env\Scripts\python.exe was not created.
    goto FAIL
)

echo.
echo [3/4] Upgrading pip...
recorder_env\Scripts\python.exe -m pip install --upgrade pip
if errorlevel 1 goto FAIL

echo.
echo [4/4] Installing recorder requirements...
recorder_env\Scripts\python.exe -m pip install -r requirements_recorder.txt
if errorlevel 1 goto FAIL

echo.
echo ============================================================
echo  Setup complete.
echo  Next: run preflight_check.bat.
echo ============================================================
pause
exit /b 0

:FAIL
echo.
echo ============================================================
echo  Setup failed.
echo  Screenshot this window and send it to Ced.
echo ============================================================
pause
exit /b 1
