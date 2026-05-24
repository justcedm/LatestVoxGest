@echo off
setlocal
cd /d "%~dp0"

rem Edit this before recording. Use the same ID as run_record_core_words.bat.
set "VOXGEST_SIGNER_ID=TEAMMATE_A"

set "VOXGEST_WORD_PROFILE=fullsign225_manual5_team"
set "VOXGEST_FEATURE_PROFILE=fullsign225"
set "VOXGEST_SINGLE_HAND_POSE=0"
set "VOXGEST_MANUAL_SEQUENCES_PER_WORD=50"
set "VOXGEST_RECORDER_OUTPUT_ROOT=recorded_features\fullsign225_manual5_team_features"

echo ============================================================
echo  VoxGest Recorder - NOTHING
echo ============================================================
echo Signer ID: %VOXGEST_SIGNER_ID%
echo Words    : NOTHING
echo Output   : %VOXGEST_RECORDER_OUTPUT_ROOT%
echo.

if not exist recorder_env\Scripts\python.exe (
    echo ERROR: recorder_env not found. Run setup_recorder.bat first.
    pause
    exit /b 1
)

recorder_env\Scripts\python.exe scripts_ml\41_record_manual_fullsign225_words.py NOTHING
if errorlevel 1 (
    echo.
    echo ERROR: Recording failed. Screenshot this window and send it to Ced.
    pause
    exit /b 1
)

echo.
echo Recording finished. Zip the recorded_features folder and send it to Ced.
pause
exit /b 0
