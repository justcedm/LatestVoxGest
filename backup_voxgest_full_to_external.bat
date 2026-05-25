@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "SOURCE=C:\BSIT 3RD YEAR\New VovGest"
set "BACKUP_BASE=D:\RB_SSD_CED"

echo ================================================================
echo VoxGest Full System External Backup
echo ================================================================
echo Source      : %SOURCE%
echo Destination : %BACKUP_BASE%
echo.

if not exist "D:\" (
    echo ERROR: Drive D:\ was not found. Connect the external SSD and try again.
    goto :fail
)

if not exist "%BACKUP_BASE%" (
    echo Backup root does not exist. Creating: %BACKUP_BASE%
    mkdir "%BACKUP_BASE%"
    if errorlevel 1 (
        echo ERROR: Could not create %BACKUP_BASE%.
        goto :fail
    )
)

if not exist "%SOURCE%" (
    echo ERROR: Source project folder was not found.
    goto :fail
)

for /f %%I in ('powershell -NoProfile -Command "Get-Date -Format yyyy_MM_dd_HHmm"') do set "STAMP=%%I"
set "BACKUP_PARENT=%BACKUP_BASE%\VoxGest_FULL_SYSTEM_BACKUP_%STAMP%"
set "DEST=%BACKUP_PARENT%\New VovGest"

echo Timestamped backup folder:
echo %DEST%
echo.

mkdir "%DEST%" 2>nul
if errorlevel 1 (
    echo ERROR: Could not create backup folder.
    goto :fail
)

echo Running robocopy...
echo Excluding only disposable/generated folders and files:
echo   voxgest_env recorder_env .gradle build __pycache__ .pytest_cache
echo   *.pyc *.hprof workspace.xml
echo.

robocopy "%SOURCE%" "%DEST%" /E /Z /R:2 /W:2 /MT:8 /XD voxgest_env recorder_env .gradle build __pycache__ .pytest_cache /XF *.pyc *.hprof workspace.xml
set "ROBOCOPY_CODE=%ERRORLEVEL%"

echo %DEST%>"%BACKUP_BASE%\VoxGest_LAST_BACKUP_PATH.txt"

echo.
echo ================================================================
echo Robocopy finished with code: %ROBOCOPY_CODE%
echo ================================================================
echo Source copied from:
echo %SOURCE%
echo.
echo Backup copied to:
echo %DEST%
echo.
echo Last backup marker:
echo %BACKUP_BASE%\VoxGest_LAST_BACKUP_PATH.txt
echo.

if %ROBOCOPY_CODE% GEQ 8 (
    echo ERROR: Robocopy reported a failure. Review the output above.
    goto :fail
)

echo Backup completed successfully.
echo Robocopy codes 0-7 are success/warning states; 8 or higher means failure.
goto :done

:fail
echo.
echo Backup did not complete successfully.
if not "%VOXGEST_BACKUP_NO_PAUSE%"=="1" pause
exit /b 1

:done
if not "%VOXGEST_BACKUP_NO_PAUSE%"=="1" pause
exit /b 0
