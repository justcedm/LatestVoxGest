# VoxGest Single-Hand Dominant Motion Update - 2026-04-29

## Purpose

This document summarizes the current single-hand correction work for VoxGest. It is intended for forwarding to the ChatGPT project or any documentation/review assistant.

## Current Product Goal

VoxGest is being shaped into an Android utility app for deaf and hard of hearing users. The recognition target is clear, real-time, on-device sign-to-text communication.

Current recognition direction:

- prioritize one dominant signing hand
- support one-hand motion versions of signs that may have common two-hand variants
- avoid letting non-dominant hand or second-arm movement become part of the learned word pattern
- stabilize the current 10 communicative words before adding sentence-level behavior

## Current Dynamic Classes

The current LSTM model has 11 output classes:

1. `YES`
2. `NO`
3. `PLEASE`
4. `WATER`
5. `HELLO`
6. `HELP`
7. `STOP`
8. `DOCTOR`
9. `NAME`
10. `THANKYOU`
11. `NOTHING`

`NOTHING` is a negative/no-word class and should not be displayed as a spoken output word.

## Problem Discovered

Some manual recordings were performed with two hands even though the project objective is now one dominant hand.

The live symptom was:

- `DOCTOR` did not work when signed with the correct one-hand version
- the model had learned from previous two-hand examples
- the old feature vector included full pose landmarks, so non-dominant arm motion could leak into the model even though the hand feature itself selected only one hand

This means the issue was not only model thresholds. It was a feature-design and data-consistency issue.

## Do We Need To Retrain From The Very Start?

No. The whole project does not need to restart.

Required recovery:

- keep the current static alphabet model
- keep the current LSTM architecture shape `[1, 30, 162]`
- archive contaminated manual samples for affected words
- record clean one-hand replacement samples
- rerun extraction with the single-hand policy
- retrain the LSTM

The only data that must be replaced is data recorded with the wrong signing style or wrong hand policy.

## Technical Fix Applied

### 1. Dominant-hand configuration already existed

The system now supports:

```powershell
$env:VOXGEST_DOMINANT_HAND='auto'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_DOMINANT_HAND='left'
```

Recommendation:

- use `right` or `left` for serious data collection
- avoid `auto` when collecting final calibration data because `auto` can switch if both hands appear
- `right` and `left` mean the signer physical hand
- webcam scripts mirror the input before MediaPipe, so the code maps physical hand preference to the correct MediaPipe label internally

### 2. Single-hand pose masking was added

New policy:

```powershell
$env:VOXGEST_SINGLE_HAND_POSE='1'
```

This is enabled by default.

When enabled, the dynamic feature vector keeps:

- head pose landmarks
- torso pose landmarks
- selected dominant arm landmarks
- selected dominant hand landmarks

It masks out:

- non-dominant arm pose landmarks
- unrelated lower-body pose landmarks

Reason:

- the previous model could learn two-hand body/arm movement from the pose vector
- masking forces the model to learn the selected hand and selected arm motion
- this better matches the one-hand utility-app objective

### 3. Existing saved sequences are normalized during training

The trainer now applies the current single-hand feature policy when loading existing `.npy` sequences.

This helps reduce contamination from older saved arrays, but it does not fully fix signs where the actual performed gesture was different. Those words still need clean re-recording.

## Files Modified Today

### Core feature extraction

File:

- `scripts_ml/lstm_features.py`

Added:

- `configured_hand_preference()`
- `single_hand_pose_enabled()`
- `select_hand_source()`
- `select_hand_landmarks()`
- `hand_is_present()`
- `mask_pose_to_single_hand()`
- `apply_sequence_feature_policy()`

Behavior:

- selects only the configured dominant hand
- masks pose landmarks to head, torso, and selected arm
- keeps tensor size unchanged at `162`

### Manual recorder

File:

- `scripts_ml/16_record_manual_words.py`

Updated behavior:

- prints active hand policy
- prints active pose policy
- counts hand presence using the configured dominant hand only
- records new samples using the same single-hand feature policy

### WLASL/video extractor

File:

- `scripts_ml/18_extract_lstm.py`

Updated behavior:

- prints active hand policy
- prints active pose policy
- extracts videos through the same single-hand feature policy

### LSTM trainer

File:

- `scripts_ml/19_train_lstm.py`

Updated behavior:

- prints active hand policy
- prints active pose policy
- applies `apply_sequence_feature_policy()` to loaded existing `.npy` sequences
- uses each sample's stored `dominant_hand` metadata when available
- keeps the model input shape `[1, 30, 162]`

### Saved-data sanity tester

File:

- `scripts_ml/17_test_word_accuracy.py`

Updated behavior:

- applies the same single-hand feature policy before testing saved `.npy` sequences

### Live recognizer

File:

- `scripts_ml/20_webcam_dual.py`

Updated behavior:

- uses configured dominant hand
- prints active hand policy
- prints active pose policy
- supports `NOTHING` as a no-output class
- keeps lighter `WORDS` mode gates for compact signs

### LSTM diagnostic

File:

- `scripts_ml/22_diagnostic_lstm.py`

Updated behavior:

- prints active dominant-hand policy
- prints active pose policy
- uses the same single-hand feature extraction as live mode

### Manual sample archiver

File:

- `scripts_ml/23_archive_manual_samples.py`

Purpose:

- safely move contaminated manual samples out of training
- remove their metadata entries
- preserve them under `dataset_words_lstm/_archived_manual`

This script does a dry run unless `--apply` is used.

### Runtime manifest

File:

- `model/runtime_manifest_v1.json`

Updated:

- current LSTM output is `[1, 11]`
- `NOTHING` is included
- dominant-hand env var documented
- single-hand pose env var documented
- pose policy documented for Android

### Documentation

Updated:

- `README_CURRENT.md`
- `# VoxGest Current Demo System.md`
- `ANDROID_DEVELOPER_HANDOFF.md`
- `SESSION_HANDOFF_2026-04-28.md`
- `SINGLE_HAND_DOMINANT_UPDATE_2026-04-29.md`

## Current Recommended Recovery Workflow

### Step 1: Choose the real dominant hand

Use the actual hand that will sign in the app.

For right hand:

```powershell
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_SINGLE_HAND_POSE='1'
```

For left hand:

```powershell
$env:VOXGEST_DOMINANT_HAND='left'
$env:VOXGEST_SINGLE_HAND_POSE='1'
```

Use the same value for:

- manual recording
- video extraction
- training
- diagnostic testing
- live testing

### Step 2: Archive contaminated manual samples

Preview first:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR
```

Apply archive:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR --apply
```

If other words were also recorded with two hands, include them:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR WATER THANKYOU YES NO --apply
```

If only the latest recording pass was wrong, archive only the newest manual
group for each word:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR WATER THANKYOU YES NO --latest-groups 1
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR WATER THANKYOU YES NO --latest-groups 1 --apply
```

### Step 3: Record clean one-hand samples

Start with the broken/weak words:

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py DOCTOR WATER THANKYOU YES NO
```

If using left hand, replace `right` with `left`.

Important:

- after the mirror-mapping fix, `VOXGEST_DOMINANT_HAND='left'` means your actual left hand
- if left-hand samples were recorded before this fix and the recorder selected the wrong hand, archive those samples and record them again

Recording requirements:

- sign with the selected dominant hand only
- keep the non-dominant hand neutral and out of the sign motion
- keep the selected hand visible
- vary distance, angle, speed, and position
- record at least 2 separate sessions for `DOCTOR`
- record at least 1 to 2 separate sessions for `WATER`, `THANKYOU`, `YES`, and `NO`

### Step 4: Re-extract video data with the same hand policy

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\18_extract_lstm.py
```

This rebuilds non-manual extracted data under the same single-hand policy.

### Step 5: Retrain

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\19_train_lstm.py
```

Expected result:

- same input shape: `[1, 30, 162]`
- output shape remains `[1, 11]`
- labels remain the 10 words plus `NOTHING`

### Step 6: Test saved data

```powershell
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\17_test_word_accuracy.py
```

### Step 7: Test live diagnostic

```powershell
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\22_diagnostic_lstm.py
```

### Step 8: Test live word mode

```powershell
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\20_webcam_dual.py
```

## Why This Approach Is Better Than Starting Over

This keeps what already works:

- existing scripts
- Android runtime architecture
- model shape
- static alphabet model
- class labels
- trained data that is still valid

It changes what was actually wrong:

- old two-hand manual examples can be archived
- non-dominant pose leakage is masked
- dominant-hand policy becomes explicit
- new training uses one-hand-consistent features

## Current Engineering Recommendation

Do not add new words yet.

Next target:

- make `DOCTOR`, `WATER`, `THANKYOU`, `YES`, and `NO` reliable under the selected dominant-hand policy

Only after those are stable:

- continue expanding vocabulary
- then build sentence assembly from accepted word tokens

## Android Implication

Android must reproduce the same feature policy:

- mirror camera frame like Python
- run MediaPipe Holistic or equivalent
- select one configured dominant hand
- anchor pose and hand to the nose point
- keep head, torso, and selected arm pose landmarks
- zero non-dominant arm/lower-body landmarks when single-hand pose mode is enabled
- append selected hand landmarks
- send `[1, 30, 162]` into the LSTM

The Android developer should treat `runtime_manifest_v1.json` as the current source of truth.
