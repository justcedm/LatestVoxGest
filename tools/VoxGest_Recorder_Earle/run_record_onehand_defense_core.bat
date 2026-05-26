@echo off
cd /d "%~dp0"

echo ============================================================
echo  VoxGest OneHand162 Defense Core - WHAT YOUR NAME MY
echo ============================================================

if not exist recorder_env\Scripts\python.exe (
    echo ERROR: recorder_env not found. Run setup_recorder.bat first.
    pause
    exit /b 1
)

set VOXGEST_SIGNER_ID=EARLE
set VOXGEST_ENABLE_PHRASE=0
set VOXGEST_WORD_PROFILE=onehand162_phrase_v1
set VOXGEST_FEATURE_PROFILE=onehand162
set VOXGEST_SINGLE_HAND_POSE=1
set VOXGEST_DOMINANT_HAND=right
set VOXGEST_MANUAL_SEQUENCES_PER_WORD=40
set VOXGEST_RECORDER_OUTPUT_ROOT=recorded_features\onehand162_phrase_v1_features

echo Signer ID       : %VOXGEST_SIGNER_ID%
echo Profile         : onehand162_phrase_v1
echo Feature profile : onehand162
echo Words           : WHAT YOUR NAME MY
echo Target          : 40 accepted samples per word
echo.

recorder_env\Scripts\python.exe scripts_ml\16_record_manual_words.py WHAT YOUR NAME MY
if errorlevel 1 (
    echo.
    echo ERROR: Recording failed. Screenshot this window and send it to Ced.
    pause
    exit /b 1
)

echo.
echo Done recording one-handed defense core.
pause
