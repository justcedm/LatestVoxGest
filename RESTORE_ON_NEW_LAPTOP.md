# Restore VoxGest On A New Laptop

This backup is intended to be a portable copy of the full VoxGest working system. It includes the Git repository, Android app, ML scripts, model artifacts, reports, datasets, recorder packs, papers, and presentation files.

## 1. Copy The Project

1. Connect the external SSD.
2. Open the latest backup folder:
   `D:\RB_SSD_CED\VoxGest_FULL_SYSTEM_BACKUP_YYYY_MM_DD_HHMM\New VovGest`
3. Copy `New VovGest` to the new laptop, for example:
   `C:\BSIT 3RD YEAR\New VovGest`
4. Open that folder in VS Code or Codex.

The `.git` folder is included, so the restored copy remains a Git repository.

## 2. Recreate The Python Environment

Run these commands from PowerShell:

```powershell
cd "PATH_TO_NEW_VovGest"
py -3.11 -m venv voxgest_env
.\voxgest_env\Scripts\activate
python -m pip install --upgrade pip
pip install -r requirements.txt
```

If MediaPipe or TensorFlow packages fail on a different Python version, install Python 3.11 and repeat the commands.

## 3. Open Android Project

1. Install Android Studio.
2. Open:
   `PATH_TO_NEW_VovGest\android_dry_run`
3. Let Gradle sync.
4. Make sure the selected run module is `app`.

## 4. Build Android APK

```powershell
cd "PATH_TO_NEW_VovGest\android_dry_run"
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug
```

APK output:

```text
PATH_TO_NEW_VovGest\android_dry_run\app\build\outputs\apk\debug\app-debug.apk
```

## 5. Install APK To Phone

Connect the phone with USB debugging enabled, then run:

```powershell
adb devices -l
adb install -r "PATH_TO_NEW_VovGest\android_dry_run\app\build\outputs\apk\debug\app-debug.apk"
```

If multiple devices are connected, use:

```powershell
adb -s DEVICE_ID install -r "PATH_TO_NEW_VovGest\android_dry_run\app\build\outputs\apk\debug\app-debug.apk"
adb -s DEVICE_ID shell am start -n com.voxgest.dryrun/com.voxgest.app.MainActivity
```

## 6. Run Model And Live-Test Scripts

Activate the Python environment first:

```powershell
cd "PATH_TO_NEW_VovGest"
.\voxgest_env\Scripts\activate
```

Example one-hand phrase live test:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='onehand162_phrase_v1'
$env:VOXGEST_FEATURE_PROFILE='onehand162'
$env:VOXGEST_DYNAMIC_MODEL='tcn'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

Example FullSign225 phrase live test:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='fullsign225_phrase_v1'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
$env:VOXGEST_DYNAMIC_MODEL='tcn'
$env:VOXGEST_SINGLE_HAND_POSE='0'
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

## 7. Important Restore Notes

- Do not copy `voxgest_env` from the old laptop; recreate it.
- Android `.gradle` and `build` folders are intentionally excluded because they can be rebuilt.
- Model files, reports, datasets, and recorder packs are included.
- If GitHub remote access is needed, run `git remote -v` and verify credentials on the new laptop.
