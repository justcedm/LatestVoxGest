# Data Assets Not Included In Git

This repository is cleaned for GitHub review. Large local data and generated artifacts are intentionally excluded so the project remains cloneable and safe to push.

## Excluded Local Folders

- `voxgest_env/`
- `dataset_words_lstm/`
- `dataset_motion_letters/`
- `dataset_phrase_intents/`
- `wlasl_videos/`
- `model/backups/`
- Android build folders such as `android_dry_run/.gradle/` and `android_dry_run/app/build/`

## Excluded File Types

- Raw videos: `.mp4`, `.avi`, `.mov`, `.mkv`, `.webm`
- Extracted arrays: `.npy`, `.npz`
- Training checkpoints: `.h5`, `.keras`, `.ckpt`, `.pb`
- Temporary logs/reports: generated `.csv` and `.json` files under `reports/`
- Archives: `.zip`, `.7z`, `.rar`, `.tar`, `.tar.gz`
- Crash heap dumps: `.hprof`

## Kept Model Metadata

The repository keeps lightweight review/runtime files:

- `model/runtime_manifest_v1.json`
- `model/runtime_manifest_sprint30.json` when present
- `model/class_labels*.json`
- small demo `.tflite` files when size-safe

## Restoring WLASL Locally

The WLASL metadata and videos should be restored locally, not committed.

Recommended local paths:

```text
scripts_ml/WLASL_v0.3.json
wlasl_videos/
dataset_words_lstm/
```

Use the target-word downloader/extractor only for the active profile:

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
.\voxgest_env\Scripts\python.exe scripts_ml\13_download_target_words.py

$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\18_extract_lstm.py
```

For Sprint30, replace `demo10` with `sprint30`.

## Restoring Manual Recordings

Manual recordings are regenerated with controlled hand-mode commands. Example:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py YES NO WATER NOTHING
```

Do not use `VOXGEST_DOMINANT_HAND='auto'` for controlled recording.

## Why These Assets Are Excluded

Large datasets, raw videos, and extracted feature arrays make Git history heavy and difficult to clone. They also may carry dataset licensing requirements. VoxGest keeps the reproducible source, manifests, labels, and documentation in Git while large data remains in local storage or a separate approved artifact store.
