@echo off
setlocal
cd /d "%~dp0"

echo ============================================================
echo  VoxGest Recorder Pack Setup
echo ============================================================
echo.

where py >nul 2>nul
if %ERRORLEVEL%==0 (
    set "PYTHON_CMD=py -3.11"
) else (
    set "PYTHON_CMD=py -3.11"
)

echo Checking Python...
%PYTHON_CMD% --version
if errorlevel 1 (
    echo.
    echo ERROR: Python was not found.
    echo Install Python 3.11 from https://www.python.org/downloads/
    echo Make sure "Add Python to PATH" is checked during installation.
    pause
    exit /b 1
)

echo.
echo Creating local recorder_env...
%PYTHON_CMD% -m venv recorder_env
if errorlevel 1 goto FAIL

echo.
echo Activating recorder_env...
call recorder_env\Scripts\activate.bat
if errorlevel 1 goto FAIL

echo.
echo Upgrading pip...
python -m pip install --upgrade pip
if errorlevel 1 goto FAIL

echo.
echo Installing recorder requirements...
python -m pip install -r requirements_recorder.txt
if errorlevel 1 goto FAIL

echo.
echo ============================================================
echo  Setup complete.
echo  You can now run run_record_core_words.bat or run_record_nothing.bat.
echo ============================================================
pause
exit /b 0

:FAIL
echo.
echo ============================================================
echo  Setup failed.
echo  Check your Python installation and internet connection, then try again.
echo ============================================================
pause
exit /b 1
