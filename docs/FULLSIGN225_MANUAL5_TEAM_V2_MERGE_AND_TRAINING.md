# FullSign225 Manual5 Team v2 Merge And Training

## Purpose

This pass creates a clean v2 dataset from the available old and new teammate recorder outputs without replacing the previous `fullsign225_manual5_team` dataset.

The goal remains a Basic TCN model for:

- EAT
- WATER
- HELLO
- THANKYOU
- NOTHING

This profile is experimental and does not replace demo10, onehand162, or the previous FullSign225 team model.

## Why Merge Instead Of Replace

The earlier team recordings are still useful, and the new Mariella and Anastacia samples should strengthen the same five-label vocabulary instead of starting from zero.

The merge script copies valid accepted samples into a separate dataset:

`external_datasets/fullsign225_manual5_team_features_v2`

Original incoming folders are not deleted or modified.

## Source Folders Used

The merge script found five `fullsign225_manual5_team_features` folders under:

`external_datasets/team_incoming_recorded_features`

Current signer contribution after merge:

| Signer | EAT | WATER | HELLO | THANKYOU | NOTHING | Total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| ANASTACIA | 31 | 30 | 30 | 30 | 50 | 171 |
| EARLE | 1 | 0 | 0 | 0 | 0 | 1 |
| MARIELLA | 41 | 40 | 40 | 40 | 0 | 161 |

CED data was not present in the scanned incoming root during this pass.

## Audit Result

Audit reports:

- `reports/fullsign225_manual5_team_v2_merge.md`
- `reports/fullsign225_manual5_team_v2_audit.md`
- `reports/fullsign225_manual5_team_v2_merge.csv`
- `reports/fullsign225_manual5_team_v2_audit.csv`
- `reports/fullsign225_manual5_team_v2_merge.json`
- `reports/fullsign225_manual5_team_v2_audit.json`

Current v2 counts:

| Label | Count | Minimum Target | Preferred Target | Status |
| --- | ---: | ---: | ---: | --- |
| EAT | 73 | 80 | 120 | Under minimum |
| WATER | 70 | 80 | 120 | Under minimum |
| HELLO | 70 | 80 | 120 | Under minimum |
| THANKYOU | 70 | 80 | 120 | Under minimum |
| NOTHING | 50 | 120 | 180 | Under minimum |

Shape check:

- Expected shape: `(30, 225)`
- Wrong-shape files: `0`
- Unreadable files: `0`

## Training Result

Training was intentionally not run because the v2 dataset did not pass the minimum data gate.

This avoids exporting a weak model that could look successful in validation but fail live due to low class coverage, especially for NOTHING.

## Training Command For Later

Run this only after the v2 audit passes:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='fullsign225_manual5_team_v2'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
$env:VOXGEST_DYNAMIC_MODEL='tcn'
$env:VOXGEST_SINGLE_HAND_POSE='0'
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\24_train_tcn.py
```

Expected artifact paths after a passing training run:

- `model/voxgest_tcn_fullsign225_manual5_team_v2.h5`
- `model/voxgest_tcn_fullsign225_manual5_team_v2.tflite`
- `model/class_labels_tcn_fullsign225_manual5_team_v2.json`
- `model/tcn_training_report_fullsign225_manual5_team_v2.json`
- `model/runtime_manifest_fullsign225_manual5_team_v2.json`

## Live Test Command

Use this only after the Basic TCN model exists:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='fullsign225_manual5_team_v2'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
$env:VOXGEST_DYNAMIC_MODEL='tcn'
$env:VOXGEST_SINGLE_HAND_POSE='0'
$env:VOXGEST_MODE='WORDS'
$env:VOXGEST_LIVE_TEST_CAPTURE_MODE='hand_trigger_auto'
$env:VOXGEST_LIVE_TEST_LABELS='EAT,WATER,HELLO,THANKYOU,NOTHING'
$env:VOXGEST_LIVE_TEST_TRIALS_PER_LABEL='5'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

## Next Recording Need

Minimum remaining samples needed before training:

- EAT: at least 7 more valid samples
- WATER: at least 10 more valid samples
- HELLO: at least 10 more valid samples
- THANKYOU: at least 10 more valid samples
- NOTHING: at least 70 more valid samples

Practical recommendation: prioritize NOTHING first, then collect another small balanced set for EAT, WATER, HELLO, and THANKYOU from CED and EARLE.

## Safety Statement

`fullsign225_manual5_team_v2` is experimental until live validation passes. It is not the Android default and does not replace demo10, onehand162, or the previous FullSign225 artifacts.
