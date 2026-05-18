# VoxGest

VoxGest is an offline Android accessibility prototype for bidirectional communication between Deaf/mute users and hearing users. The repository keeps the Android UI, ML scripts, runtime manifests, label files, avatar prototype JSON, reviewer-facing documentation, and small demo TFLite artifacts.

## What Is Included

- `android_dry_run/` source code for the Android UI prototype.
- `scripts_ml/` Python source for recording, extraction, training, testing, and reporting.
- `docs/` project documentation and defense notes.
- `avatar/` and `android_dry_run/app/src/main/assets/avatar/` lightweight avatar prototype JSON files.
- `model/runtime_manifest*.json` and `model/class_labels*.json`.
- Small demo `.tflite` files needed for review/reference.
- `README_CURRENT.md` and `ANDROID_DEVELOPER_HANDOFF.md`.

## What Is Not Included

Large or generated assets are intentionally excluded from Git:

- `voxgest_env/`
- `dataset_*`
- `wlasl_videos/`
- WLASL metadata/raw videos
- `.npy` / `.npz` extracted feature arrays
- Android/Gradle build output
- model backups and `.h5` checkpoints
- temporary live logs and generated CSV/JSON reports
- zip archives and crash heap dumps

See `docs/DATA_ASSETS_NOT_INCLUDED.md` for the restore plan.

## Restore Local Python Environment

Create a fresh environment locally instead of committing `voxgest_env/`:

```powershell
python -m venv voxgest_env
.\voxgest_env\Scripts\pip.exe install --upgrade pip
.\voxgest_env\Scripts\pip.exe install -r requirements.txt
```

## Restore Datasets Locally

Place large datasets back into local-only folders:

- `wlasl_videos/`
- `dataset_words_lstm/`
- `dataset_motion_letters/`
- `dataset_phrase_intents/`

These paths are ignored by Git. Regenerate extracted features with the scripts in `scripts_ml/` after restoring videos or manual recordings.

## Android Build

```powershell
cd android_dry_run
.\gradlew.bat assembleDebug
```

The current UI preview build avoids packaging TensorFlow Lite JNI because the available upstream native library is not compatible with 16 KB page-size Android devices/emulators. Python-side training/export is unaffected.

## ML Workflow

Training remains in Python. Android consumes exported TFLite models, labels, and runtime manifests only.

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\19_train_lstm.py
.\voxgest_env\Scripts\python.exe scripts_ml\24_train_tcn.py
```

Keep generated datasets/checkpoints local unless a release process explicitly publishes them elsewhere.
