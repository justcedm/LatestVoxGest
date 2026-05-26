@echo off
cd /d "%~dp0"

echo ============================================================
echo  VoxGest Recorder Preflight Check
echo ============================================================
echo.

echo [1/6] Checking Python 3.11 launcher...
py -3.11 --version
if errorlevel 1 (
    echo.
    echo ERROR: Python 3.11 was not found.
    echo Install Python 3.11.9 and make sure py launcher is available.
    pause
    exit /b 1
)

echo.
echo [2/6] Checking recorder_env...
if not exist recorder_env\Scripts\python.exe (
    echo recorder_env not found.
    echo Run setup_recorder.bat first.
    pause
    exit /b 1
)

echo.
echo [3/6] Checking required Python files...
if not exist scripts_ml\16_record_manual_words.py (
    echo ERROR: scripts_ml\16_record_manual_words.py is missing.
    pause
    exit /b 1
)

if not exist scripts_ml\41_record_manual_fullsign225_words.py (
    echo ERROR: scripts_ml\41_record_manual_fullsign225_words.py is missing.
    pause
    exit /b 1
)

if not exist scripts_ml\word_config.py (
    echo ERROR: scripts_ml\word_config.py is missing.
    pause
    exit /b 1
)

echo.
echo [4/6] Checking packages...
recorder_env\Scripts\python.exe -c "import cv2; import numpy; import mediapipe as mp; print('OpenCV OK'); print('NumPy OK'); print('MediaPipe', mp.__version__); print('mp.solutions exists:', hasattr(mp, 'solutions')); import mediapipe.python.solutions.holistic as holistic; print('Holistic OK')"
if errorlevel 1 (
    echo.
    echo ERROR: Python package check failed.
    echo Try deleting recorder_env and running setup_recorder.bat again.
    pause
    exit /b 1
)

echo.
echo [5/6] Checking camera access...
recorder_env\Scripts\python.exe -c "import cv2; cap=cv2.VideoCapture(0); print('Camera opened:', cap.isOpened()); cap.release()"
if errorlevel 1 (
    echo.
    echo WARNING: Camera check failed.
    echo Close Zoom/Teams/Camera app and try again.
)

echo.
echo [6/6] Checking batch scripts...
if not exist run_record_onehand_defense_core.bat echo WARNING: run_record_onehand_defense_core.bat missing.
if not exist run_record_onehand_phrase_nothing.bat echo WARNING: run_record_onehand_phrase_nothing.bat missing.
if not exist run_record_fullsign_phrase_core.bat echo WARNING: run_record_fullsign_phrase_core.bat missing.
if not exist run_record_fullsign_phrase_extra.bat echo WARNING: run_record_fullsign_phrase_extra.bat missing.
if not exist run_record_fullsign_phrase_nothing.bat echo WARNING: run_record_fullsign_phrase_nothing.bat missing.

echo.
echo ============================================================
echo  Preflight complete.
echo  If there are no ERROR messages, you can start recording.
echo ============================================================
pause
