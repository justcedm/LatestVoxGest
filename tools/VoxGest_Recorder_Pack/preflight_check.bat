@echo off
setlocal
cd /d "%~dp0"

echo ============================================================
echo  VoxGest Recorder Preflight Check
echo ============================================================
echo.

echo [1/7] Checking Python 3.11 launcher...
py -3.11 --version
if errorlevel 1 (
    echo.
    echo ERROR: Python 3.11 was not found.
    echo Install Python 3.11 and run setup_recorder.bat again.
    pause
    exit /b 1
)

echo.
echo [2/7] Checking recorder_env...
if not exist recorder_env\Scripts\python.exe (
    echo ERROR: recorder_env not found.
    echo Run setup_recorder.bat first.
    pause
    exit /b 1
)

echo.
echo [3/7] Checking required Python scripts...
if not exist scripts_ml\16_record_manual_words.py goto MISSING_SCRIPT
if not exist scripts_ml\41_record_manual_fullsign225_words.py goto MISSING_SCRIPT
if not exist scripts_ml\word_config.py goto MISSING_SCRIPT
if not exist scripts_ml\lstm_features.py goto MISSING_SCRIPT
if not exist scripts_ml\frame_quality_gate.py goto MISSING_SCRIPT

echo.
echo [4/7] Checking Python packages...
recorder_env\Scripts\python.exe -c "import cv2; import numpy; import mediapipe as mp; print('OpenCV OK'); print('NumPy OK'); print('MediaPipe', mp.__version__); assert hasattr(mp, 'solutions'); print('mp.solutions OK'); import mediapipe.python.solutions.holistic as holistic; print('MediaPipe Holistic OK')"
if errorlevel 1 (
    echo.
    echo ERROR: Python package check failed.
    echo Delete recorder_env, run setup_recorder.bat again, then rerun preflight.
    pause
    exit /b 1
)

echo.
echo [5/7] Checking camera access...
recorder_env\Scripts\python.exe -c "import cv2, sys; cap=cv2.VideoCapture(0); ok=cap.isOpened(); print('Camera opened:', ok); cap.release(); sys.exit(0 if ok else 1)"
if errorlevel 1 (
    echo.
    echo ERROR: Camera did not open.
    echo Close Zoom, Teams, browser camera tabs, or the Windows Camera app, then rerun preflight.
    pause
    exit /b 1
)

echo.
echo [6/7] Checking required batch files...
if not exist run_record_onehand_defense_core.bat goto MISSING_BATCH
if not exist run_record_onehand_phrase_nothing.bat goto MISSING_BATCH
if not exist run_record_fullsign_phrase_core.bat goto MISSING_BATCH
if not exist run_record_fullsign_phrase_extra.bat goto MISSING_BATCH
if not exist run_record_fullsign_phrase_nothing.bat goto MISSING_BATCH

echo.
echo [7/7] Checking output folders...
if not exist recorded_features mkdir recorded_features

echo.
echo ============================================================
echo  Preflight complete.
echo  If you see this message, recording is ready.
echo ============================================================
pause
exit /b 0

:MISSING_SCRIPT
echo.
echo ERROR: A required script is missing from scripts_ml.
echo Screenshot this window and send it to Ced.
pause
exit /b 1

:MISSING_BATCH
echo.
echo ERROR: A required run_record batch file is missing.
echo Screenshot this window and send it to Ced.
pause
exit /b 1
