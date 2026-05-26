@echo off
cd /d "%~dp0"

echo ============================================================
echo  VoxGest OneHand162 Phrase V1 - WHAT YOUR NAME
echo ============================================================

if not exist recorder_env\Scripts\python.exe (
    echo ERROR: recorder_env not found. Run setup_recorder.bat first.
    pause
    exit /b 1
)

set VOXGEST_SIGNER_ID=CHANGE_THIS
set VOXGEST_ENABLE_PHRASE=0
set VOXGEST_WORD_PROFILE=onehand162_phrase_v1
set VOXGEST_FEATURE_PROFILE=onehand162
set VOXGEST_SINGLE_HAND_POSE=1
set VOXGEST_DOMINANT_HAND=right
set VOXGEST_MANUAL_SEQUENCES_PER_WORD=50
set VOXGEST_RECORDER_OUTPUT_ROOT=recorded_features\onehand162_phrase_v1_features

echo Signer ID       : %VOXGEST_SIGNER_ID%
echo Profile         : onehand162_phrase_v1
echo Feature profile : onehand162
echo Dominant hand   : %VOXGEST_DOMINANT_HAND%
echo Words           : WHAT YOUR NAME
echo Target          : 50 accepted samples per word
echo.

recorder_env\Scripts\python.exe scripts_ml\16_record_manual_words.py WHAT YOUR NAME
if errorlevel 1 (
    echo.
    echo ERROR: Recording failed. Screenshot this window and send it to Ced.
    pause
    exit /b 1
)

echo.
echo Done recording one-handed WHAT YOUR NAME.
pause

