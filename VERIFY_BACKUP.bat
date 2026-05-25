@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "BACKUP_BASE=D:\RB_SSD_CED"
set "MARKER=%BACKUP_BASE%\VoxGest_LAST_BACKUP_PATH.txt"

echo ================================================================
echo VoxGest Backup Verification
echo ================================================================

if not exist "%BACKUP_BASE%" (
    echo ERROR: Backup base does not exist: %BACKUP_BASE%
    goto :fail
)

if not "%~1"=="" goto :usearg
if exist "%MARKER%" goto :usemarker
goto :uselatest

:usearg
set "BACKUP_ROOT=%~1"
goto :havepath

:usemarker
set /p BACKUP_ROOT=<"%MARKER%"
goto :havepath

:uselatest
for /f "delims=" %%D in ('powershell -NoProfile -Command "$d = Get-ChildItem -LiteralPath ''%BACKUP_BASE%'' -Directory -Filter ''VoxGest_FULL_SYSTEM_BACKUP_*'' | Sort-Object Name -Descending | Select-Object -First 1; if ($d) { Join-Path $d.FullName ''New VovGest'' }"') do set "BACKUP_ROOT=%%D"
goto :havepath

:havepath

if "%BACKUP_ROOT%"=="" (
    echo ERROR: No backup folder was found.
    goto :fail
)

echo Verifying:
echo %BACKUP_ROOT%
echo.

if not exist "%BACKUP_ROOT%" (
    echo ERROR: Backup root does not exist.
    goto :fail
)

set "FAILURES=0"

call :check_dir "android_dry_run"
call :check_dir "scripts_ml"
call :check_dir "model"
call :check_dir "reports"
call :check_dir "docs"
call :check_dir "tools"
call :check_dir "external_datasets"
call :check_dir ".git"
call :check_dir "android_dry_run\app"

call :check_file "model\voxgest_tcn_onehand162_phrase_v1.h5"
call :check_file "model\voxgest_tcn_onehand162_phrase_v1.tflite"
call :check_file "model\voxgest_tcn_fullsign225_phrase_v1.h5"
call :check_file "model\voxgest_tcn_fullsign225_phrase_v1.tflite"

dir /b "%BACKUP_ROOT%\reports\*audit*" >nul 2>nul
if errorlevel 1 (
    echo [MISS] reports folder has no audit files
    set /a FAILURES+=1
) else (
    echo [ OK ] reports folder contains audit files
)

echo.
if "%FAILURES%"=="0" goto :verify_ok
echo Verification FAILED with %FAILURES% missing items.
goto :fail

:verify_ok
echo Verification PASSED.
goto :done

:check_dir
if exist "%BACKUP_ROOT%\%~1\" (
    echo [ OK ] %~1
) else (
    echo [MISS] %~1
    set /a FAILURES+=1
)
exit /b 0

:check_file
if exist "%BACKUP_ROOT%\%~1" (
    echo [ OK ] %~1
) else (
    echo [MISS] %~1
    set /a FAILURES+=1
)
exit /b 0

:fail
echo.
echo Backup verification did not pass.
if not "%VOXGEST_BACKUP_NO_PAUSE%"=="1" pause
exit /b 1

:done
if not "%VOXGEST_BACKUP_NO_PAUSE%"=="1" pause
exit /b 0
