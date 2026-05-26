@echo off
cd /d "%~dp0"

echo ============================================================
echo  VoxGest FullSign225 Phrase V1 - Core Phrase Words
echo ============================================================

if not exist recorder_env\Scripts\python.exe (
    echo ERROR: recorder_env not found. Run setup_recorder.bat first.
    pause
    exit /b 1
)

set VOXGEST_SIGNER_ID=EARLE
set VOXGEST_ENABLE_PHRASE=0
set VOXGEST_WORD_PROFILE=fullsign225_phrase_v1
set VOXGEST_FEATURE_PROFILE=fullsign225
set VOXGEST_SINGLE_HAND_POSE=0
set VOXGEST_MANUAL_SEQUENCES_PER_WORD=30
set VOXGEST_RECORDER_OUTPUT_ROOT=recorded_features\fullsign225_phrase_v1_features

echo Signer ID       : %VOXGEST_SIGNER_ID%
echo Profile         : fullsign225_phrase_v1
echo Feature profile : fullsign225
echo Words           : WHAT YOUR NAME MY YOU OKAY
echo Target          : 30 accepted samples per word
echo.

recorder_env\Scripts\python.exe scripts_ml\41_record_manual_fullsign225_words.py WHAT YOUR NAME MY YOU OKAY
if errorlevel 1 (
    echo.
    echo ERROR: Recording failed. Screenshot this window and send it to Ced.
    pause
    exit /b 1
)

echo.
echo Done recording FullSign225 phrase core words.
pause

