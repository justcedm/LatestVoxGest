# FullSign225 Team16 Training Report

Generated: 2026-05-19T12:38:04

## Purpose

This experiment trains a separate FullSign225 Team16 dynamic-word TCN using the cleaned team video dataset. It tests whether fixed left-hand and right-hand landmark slots can improve recognizability for two-hand or full-body signing videos without replacing the current demo10 or onehand162 pipeline.

## Dataset Source

- Normalized videos: `external_datasets/fullsign225_team_dataset_normalized`
- Extracted features: `external_datasets/fullsign225_team16_features`
- Audit inputs: `reports/fullsign225_team16_extraction_audit.json`
- Feature extraction report: `reports/fullsign225_team16_feature_extraction.json`

## Labels Used

DOCTOR, EAT, HELLO, HELP, NAME, NO, PAIN, PLEASE, SORRY, STOP, THANKYOU, TIME, WANT, WATER, YES, NOTHING

## Successful Videos Per Label

| Label | Passing Videos | Sequences | Groups |
| --- | ---: | ---: | ---: |
| DOCTOR | 31 | 279 | 31 |
| EAT | 21 | 189 | 21 |
| HELLO | 27 | 243 | 27 |
| HELP | 26 | 234 | 26 |
| NAME | 25 | 225 | 25 |
| NO | 24 | 216 | 24 |
| PAIN | 19 | 171 | 19 |
| PLEASE | 25 | 225 | 25 |
| SORRY | 31 | 279 | 31 |
| STOP | 22 | 198 | 22 |
| THANKYOU | 22 | 198 | 22 |
| TIME | 20 | 180 | 20 |
| WANT | 21 | 189 | 21 |
| WATER | 30 | 270 | 30 |
| YES | 19 | 171 | 19 |
| NOTHING | 17 | 153 | 17 |

## Failed Or Skipped Videos

- `NOTHING_fullsign225_011.mp4` skipped: low_hand_ratio<0.20
- `NOTHING_fullsign225_018.mp4` skipped: low_hand_ratio<0.20

## Feature Contract

- Input shape: `[1, 30, 225]`
- Per frame: pose 99 + left hand 63 + right hand 63
- Fixed left-hand and right-hand slots; missing hands are zero-filled by the feature extractor.
- This does not touch the onehand162 dataset or model contract.

## Files Changed

- `scripts_ml/word_config.py`
- `scripts_ml/19_train_lstm.py`
- `scripts_ml/24_train_tcn.py`
- `scripts_ml/33_live_word_test_logger.py`
- `scripts_ml/40_extract_fullsign225_team16_features.py`
- `external_datasets/fullsign225_team16_features/metadata_lstm_v2.json` and extracted `.npy` feature files
- `reports/fullsign225_team16_feature_extraction.csv`
- `reports/fullsign225_team16_feature_extraction.json`
- `reports/fullsign225_team16_feature_extraction_summary.md`
- `model/voxgest_tcn_fullsign225_team16.h5`
- `model/voxgest_tcn_fullsign225_team16.tflite`
- `model/class_labels_tcn_fullsign225_team16.json`
- `model/tcn_training_report_fullsign225_team16.json`
- `model/runtime_manifest_fullsign225_team16.json`
- `docs/FULLSIGN225_TEAM16_TRAINING_REPORT.md`

## Commands Run

```powershell
$env:VOXGEST_WORD_PROFILE='fullsign225_team16'; $env:VOXGEST_FEATURE_PROFILE='fullsign225'; $env:VOXGEST_SINGLE_HAND_POSE='0'; $env:VOXGEST_CLEAR_FULLSIGN225_TEAM16_FEATURES='1'; .\voxgest_env\Scripts\python.exe scripts_ml\40_extract_fullsign225_team16_features.py
```
```powershell
$env:VOXGEST_ENABLE_PHRASE='0'; $env:VOXGEST_WORD_PROFILE='fullsign225_team16'; $env:VOXGEST_FEATURE_PROFILE='fullsign225'; $env:VOXGEST_SINGLE_HAND_POSE='0'; $env:VOXGEST_LSTM_DATASET='C:\BSIT 3RD YEAR\New VovGest\external_datasets\fullsign225_team16_features'; .\voxgest_env\Scripts\python.exe scripts_ml\24_train_tcn.py
```

## Feature Extraction Result

- Videos extracted: 380 / 380
- Total sequences: 3420
- Wrong-shape arrays found during sanity check: 0

## TCN Grouped Validation Accuracy

- Best grouped validation accuracy: 89.04%
- Train sequences: 2754
- Validation sequences: 666
- Train groups: 306
- Validation groups: 74

## Per-Class Accuracy

| Label | Correct | Total | Accuracy | Top Miss |
| --- | ---: | ---: | ---: | --- |
| DOCTOR | 44 | 54 | 81.5% | STOP |
| EAT | 36 | 36 | 100.0% | - |
| HELLO | 45 | 45 | 100.0% | - |
| HELP | 35 | 45 | 77.8% | STOP |
| NAME | 44 | 45 | 97.8% | DOCTOR |
| NO | 44 | 45 | 97.8% | YES |
| PAIN | 36 | 36 | 100.0% | - |
| PLEASE | 36 | 45 | 80.0% | THANKYOU |
| SORRY | 53 | 54 | 98.1% | PLEASE |
| STOP | 35 | 36 | 97.2% | HELP |
| THANKYOU | 27 | 36 | 75.0% | PLEASE |
| TIME | 36 | 36 | 100.0% | - |
| WANT | 34 | 36 | 94.4% | HELP |
| WATER | 43 | 54 | 79.6% | YES |
| YES | 36 | 36 | 100.0% | - |
| NOTHING | 9 | 27 | 33.3% | HELLO |

## Top Confusion Map

- DOCTOR -> STOP
- HELP -> STOP
- NAME -> DOCTOR
- NO -> YES
- PLEASE -> THANKYOU
- SORRY -> PLEASE
- STOP -> HELP
- THANKYOU -> PLEASE
- WANT -> HELP
- WATER -> YES
- NOTHING -> HELLO

## Weakest Labels

- NOTHING: 33.3% (9/27), top miss HELLO
- THANKYOU: 75.0% (27/36), top miss PLEASE
- HELP: 77.8% (35/45), top miss STOP
- WATER: 79.6% (43/54), top miss YES
- PLEASE: 80.0% (36/45), top miss THANKYOU
- DOCTOR: 81.5% (44/54), top miss STOP

## Model Size

- Keras H5: 3,558,928 bytes (3.39 MB)
- TFLite: 316,136 bytes (309 KB)

## NOTHING Warning

NOTHING has only 17 passing videos after skipping `NOTHING_fullsign225_011.mp4` and `NOTHING_fullsign225_018.mp4`. Validation accuracy for NOTHING was 33.3%, so this class needs more hard-negative capture before this model can be trusted live.

## Manual Capture Recommendations

- Priority 1: record more NOTHING hard negatives: idle hand, open hand, both hands visible, hand entering/leaving frame, partial signs, aborted signs, and transition movement.
- Priority 2: strengthen validation-weak labels: THANKYOU, HELP, WATER, and PLEASE contrast samples.
- Priority 3: expand labels under 30 passing videos: EAT, HELLO, HELP, NAME, NO, NOTHING, PAIN, PLEASE, STOP, THANKYOU, TIME, WANT, YES.
- Keep capture in FullSign225 mode with both hands visible when the sign needs both hands; do not mix these videos into onehand162.

## Live Test Command

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='fullsign225_team16'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
$env:VOXGEST_DYNAMIC_MODEL='tcn'
$env:VOXGEST_SINGLE_HAND_POSE='0'
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

## Recommendation

Continue the FullSign225 Team16 experiment, but do not promote it yet. Grouped validation is promising at 89.04%, yet NOTHING is weak and live testing has not confirmed stability. demo10 remains the safe baseline, onehand162 remains the active app-compatible hardening path, and this model is experimental and does not replace demo10 or onehand162.
