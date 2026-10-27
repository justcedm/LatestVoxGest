# VoxGest

VoxGest is a BSIT capstone Android accessibility prototype exploring bidirectional communication between Filipino Sign Language (FSL) users and hearing/non-signing people. It combines a selected-sign recognition pipeline with text/speech output and a separate supported-concept avatar pathway.

> **Start here:** the [Gemini Master Handoff](docs/GEMINI_MASTER_HANDOFF_20261028.md) is the current authoritative project transfer document. The previous root-level `README_CURRENT.md` and `ANDROID_DEVELOPER_HANDOFF.md` described an earlier 10-word / 30-frame / 162-feature implementation; their original contents are preserved in `docs/*_LEGACY_20261028.md`.

## Current status (2026-10-28)

- Current survey profile: **Core20**, an inherited name for a **19-class** FSL-105 V1 subset.
- Standard dynamic input contract: `[1,20,225]` (99 pose + 63 anatomical-left hand + 63 anatomical-right hand values per frame); Core20 output: `[1,19]`.
- Primary dataset restriction: FSL-105 Mendeley Version 1 only for this track.
- Current physical-device blocker: the user reports the main app's front-camera preview still appears mirrored on a Samsung Galaxy A56 5G (SM-A566B, Android 16). This is not resolved until the user confirms the installed build is visually unmirrored.
- The connected repository's last visible commit before this documentation update was dated 2026-10-03. Later local changes may not be present in GitHub. Verify local/remote parity and active model identity before modifying code or quoting metrics.

## Repository map

- `android_dry_run/` — Android prototype source.
- `scripts_ml/` — Python recording, extraction, training, and evaluation scripts for multiple historical/experimental profiles.
- `model/` — model artifacts, labels, and runtime manifests. Multiple generations coexist; never infer the active model from filename alone.
- `docs/` — research, model, avatar, survey, and handoff documents.
- `reports/` — historical audit/evaluation reports; check model/profile/date before relying on a result.
- `avatar/` — avatar prototype assets.

## Build and test (only after verifying the active worktree)

```powershell
cd .\android_dry_run
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Build/test success is necessary but not sufficient: camera orientation, overlay alignment, active model, and recognition behavior require verification on the actual device.

## Data and privacy

Large datasets, raw videos, extracted arrays, local environments, build outputs, and some generated artifacts are intentionally excluded from Git. See [Data Assets Not Included](docs/DATA_ASSETS_NOT_INCLUDED.md). Do not commit participant recordings, identifiable videos, consent forms, credentials, or API keys without an approved process.

## Documentation

- [Current Project Status](README_CURRENT.md)
- [Gemini Master Handoff](docs/GEMINI_MASTER_HANDOFF_20261028.md)
- [Documentation Index](docs/README.md)
