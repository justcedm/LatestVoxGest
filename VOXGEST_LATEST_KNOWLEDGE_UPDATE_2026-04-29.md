# VoxGest Latest Knowledge Update - 2026-04-29

## How To Read This Note

This document explains what VoxGest currently is, what was changed today, why the single-hand issue happened, and what the next correct engineering steps are.

Use this note as a structured project explanation for ChatGPT or another reviewer. It is written as a knowledge update, not as a task handoff.

## Project Objective

VoxGest is being developed as a real-time Android utility app for deaf and hard of hearing users.

The target experience is:

- open the device camera
- sign with one dominant hand
- recognize signs in real time
- display clear text output
- run locally on-device without a backend

The current engineering direction is not full ASL sentence fluency yet. The immediate target is a small, reliable, one-hand dynamic recognition system that can later grow into sentence assembly.

## Current Recognition Scope

Static recognition:

- `A-Z`
- `del`
- `space`
- `nothing`

Dynamic word recognition:

- `YES`
- `NO`
- `PLEASE`
- `WATER`
- `HELLO`
- `HELP`
- `STOP`
- `DOCTOR`
- `NAME`
- `THANKYOU`

Negative dynamic class:

- `NOTHING`

`NOTHING` is used to reduce false recognition during idle, neutral, or transition movement. It should not be shown as a spoken output word.

## Current Model Contract

Current LSTM model files:

- `model/voxgest_lstm_v1.h5`
- `model/voxgest_lstm_v1.tflite`
- `model/class_labels_lstm_v1.json`
- `model/lstm_training_report.json`

Current LSTM input:

- shape: `[1, 30, 162]`
- sequence length: `30` frames
- feature size: `162`
- pose portion: `99`
- hand portion: `63`

Current LSTM output:

- shape: `[1, 11]`
- class count: `11`

Current label order:

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

## Latest Training Report Snapshot

Source:

- `model/lstm_training_report.json`

Latest report values currently on disk:

- `word_profile`: `demo10`
- `best_grouped_val_accuracy`: `66.61952137947083`
- `train_sequences`: `4653`
- `val_sequences`: `707`
- `train_groups`: `195`
- `val_groups`: `41`

Per-class grouped validation accuracy:

- `YES`: `75.32467532467533`
- `NO`: `75.0`
- `PLEASE`: `50.0`
- `WATER`: `50.0`
- `HELLO`: `34.48275862068966`
- `HELP`: `80.26315789473685`
- `STOP`: `67.53246753246754`
- `DOCTOR`: `73.68421052631578`
- `NAME`: `91.37931034482759`
- `THANKYOU`: `66.66666666666666`

Top miss map:

- `YES -> WATER`
- `NO -> PLEASE`
- `PLEASE -> HELLO`
- `WATER -> PLEASE`
- `HELLO -> PLEASE`
- `HELP -> STOP`
- `STOP -> PLEASE`
- `DOCTOR -> HELLO`
- `NAME -> STOP`
- `THANKYOU -> YES`

Important interpretation:

- The model is functional but not yet robust enough for production sentence-level use.
- `WATER`, `THANKYOU`, `YES`, `NO`, and `DOCTOR` are current practical focus words.
- The report above was generated before the latest single-hand mirror mapping fix and before any future clean re-record/retrain cycle.

## Main Technical Problem Found Today

The project goal became one dominant hand, but some manual samples were recorded with two-hand signing style.

This caused a mismatch:

- the model learned two-hand movement for some words
- the user later signed the correct one-hand version
- `DOCTOR` failed because its learned training pattern did not match the desired one-hand live gesture

There was a second technical issue:

- the dynamic model hand vector used one selected hand
- but the pose vector still contained full body pose, including the non-dominant arm
- this allowed two-hand movement to leak into the model through pose landmarks

So this was not just a threshold issue. It was a data and feature-policy issue.

## Single-Hand Feature Policy Added

The system now supports a strict single-hand motion feature policy.

Environment variable:

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

This keeps the tensor shape at `[1, 30, 162]`, so Android and TFLite compatibility are preserved.

## Dominant-Hand Policy

Environment variable:

```powershell
$env:VOXGEST_DOMINANT_HAND='right'
```

Valid values:

- `auto`
- `right`
- `left`

Current meaning:

- `right` means the signer physical right hand
- `left` means the signer physical left hand
- `auto` allows automatic selection, but should not be used for final clean data collection if both hands appear

Recommended for controlled data:

- use `right` when collecting right-hand samples
- use `left` when collecting left-hand samples
- keep `VOXGEST_SINGLE_HAND_POSE='1'`

## Mirrored Webcam Fix

The webcam scripts mirror the frame before MediaPipe:

```python
frame = cv2.flip(frame, 1)
```

Because of this, MediaPipe hand labels can appear reversed relative to the user's physical hand.

Problem observed:

- user set `VOXGEST_DOMINANT_HAND='left'`
- the recorder appeared to recognize the right hand

Fix applied:

- `VOXGEST_DOMINANT_HAND` now means the user's physical hand
- webcam scripts pass `mirrored_input=True`
- video extraction passes `mirrored_input=False`
- the code maps physical left/right to the correct MediaPipe label internally

Mapping behavior:

- physical right hand on mirrored webcam maps to MediaPipe `left`
- physical left hand on mirrored webcam maps to MediaPipe `right`
- physical right hand on normal video maps to MediaPipe `right`
- physical left hand on normal video maps to MediaPipe `left`

## Files Changed For Single-Hand Robustness

### `scripts_ml/lstm_features.py`

Added or updated:

- `configured_hand_preference()`
- `configured_mirror_input()`
- `single_hand_pose_enabled()`
- `mediapipe_side_for_preference()`
- `select_hand_source()`
- `select_hand_landmarks()`
- `hand_is_present()`
- `mask_pose_to_single_hand()`
- `apply_sequence_feature_policy()`
- `extract_frame_features()`

Purpose:

- make hand preference explicit
- fix mirrored webcam left/right behavior
- mask non-dominant pose movement
- keep training, testing, extraction, and live runtime aligned

### `scripts_ml/16_record_manual_words.py`

Updated:

- records `dominant_hand` metadata
- records `mirrored_input` metadata
- records `single_hand_pose` metadata
- uses mirrored webcam mapping
- prints active hand, pose, and mirror settings

Purpose:

- new samples carry enough metadata for correct training later

### `scripts_ml/18_extract_lstm.py`

Updated:

- uses `mirrored_input=False`
- records metadata for extracted video samples
- prints active hand, pose, and mirror settings

Purpose:

- external videos are not treated like mirrored webcam input

### `scripts_ml/19_train_lstm.py`

Updated:

- applies single-hand feature policy when loading `.npy` data
- uses each sample's stored `dominant_hand` metadata when available
- uses each sample's stored `mirrored_input` metadata when available
- falls back to sensible defaults for older files
- skips manual samples missing hand/mirror/single-hand metadata when `VOXGEST_SINGLE_HAND_POSE=1`
- reports skipped metadata counts per class

Purpose:

- right-hand and left-hand samples can coexist in training
- old saved arrays can be normalized through the current feature policy

### `scripts_ml/17_test_word_accuracy.py`

Updated:

- applies the same sample-aware feature policy as training
- skips the same metadata-less manual samples as training

Purpose:

- saved-data sanity testing now matches the training feature pipeline

### `scripts_ml/20_webcam_dual.py`

Updated:

- uses physical-hand mirror mapping
- prints active hand, pose, and mirror settings
- keeps `NOTHING` as a no-output dynamic class
- uses lighter live gates for compact/weak signs

Purpose:

- live recognition now follows the same single-hand rules

### `scripts_ml/22_diagnostic_lstm.py`

Updated:

- uses physical-hand mirror mapping
- prints active hand, pose, and mirror settings
- uses the same feature extraction as live mode

Purpose:

- diagnostic output now reflects the actual live pipeline

### `scripts_ml/23_archive_manual_samples.py`

Added:

- dry-run archiving tool
- `--apply` mode to move samples
- `--latest-groups N` option

Purpose:

- contaminated manual samples can be removed from training without deletion
- latest mistaken recording sessions can be targeted safely

## Archive Tool Usage

Preview all manual samples for words:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR WATER THANKYOU YES NO
```

Archive all manual samples for words:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR WATER THANKYOU YES NO --apply
```

Preview only latest recording group per word:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR WATER THANKYOU YES NO --latest-groups 1
```

Archive latest two recording groups per word:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR WATER THANKYOU YES NO --latest-groups 2 --apply
```

Preview old manual samples that do not have hand/mirror metadata:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR WATER THANKYOU YES NO --missing-hand-metadata
```

Archive old manual samples that do not have hand/mirror metadata:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR WATER THANKYOU YES NO --missing-hand-metadata --apply
```

Observed dry-run result:

- latest group only found `251` samples across `DOCTOR`, `WATER`, `THANKYOU`, `YES`, and `NO`
- latest two groups found `600` samples across those words
- missing-hand-metadata mode found `1320` old samples across those words

Interpretation:

- the latest one or two recording passes likely include the questionable right/left samples recorded before the mirror fix
- archiving latest groups is safer than deleting all historical manual samples if only recent passes are wrong
- archiving missing-hand-metadata samples is the cleanest option when moving to the strict single-hand pipeline

## Current Correct Recovery Workflow

### Step 1: Decide the target recording policy

For right hand:

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_SINGLE_HAND_POSE='1'
```

For left hand:

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_DOMINANT_HAND='left'
$env:VOXGEST_SINGLE_HAND_POSE='1'
```

### Step 2: Archive contaminated samples

If only the latest mistaken passes are suspect:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR WATER THANKYOU YES NO --latest-groups 2 --apply
```

If the entire manual set for those words is suspect:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR WATER THANKYOU YES NO --apply
```

### Step 3: Record clean right-hand samples

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py DOCTOR WATER THANKYOU YES NO
```

### Step 4: Record clean left-hand samples

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_DOMINANT_HAND='left'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py DOCTOR WATER THANKYOU YES NO
```

### Step 5: Rebuild extracted video data

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\18_extract_lstm.py
```

### Step 6: Retrain

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\19_train_lstm.py
```

### Step 7: Test saved data

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\17_test_word_accuracy.py
```

### Step 8: Test live diagnostic

For right hand:

```powershell
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\22_diagnostic_lstm.py
```

For left hand:

```powershell
$env:VOXGEST_DOMINANT_HAND='left'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\22_diagnostic_lstm.py
```

### Step 9: Test live word mode

```powershell
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\20_webcam_dual.py
```

## Runtime Threshold State

The live runtime has per-word `WORDS` mode rules.

Compact signs currently have lighter gates:

- `YES`
- `NO`
- `WATER`
- `THANKYOU`

`NOTHING` has a no-output rule.

This helps live responsiveness, but it does not replace clean data. If a word was trained with the wrong motion style, threshold tuning cannot fully solve it.

## Android Implications

Android must match the Python feature policy.

Required Android behavior:

- use front camera by default
- mirror the frame if matching Python webcam behavior
- treat selected dominant hand as the user's physical hand
- if the frame is mirrored before landmarks, map physical left/right to the opposite MediaPipe label
- extract pose landmarks
- anchor pose and hand to nose
- keep head, torso, and selected arm pose landmarks
- zero non-dominant arm and unrelated lower-body pose landmarks
- append selected hand landmarks
- send `[1, 30, 162]` into the LSTM
- read `[1, 11]` output
- treat `NOTHING` as no output

Current Android contract source:

- `model/runtime_manifest_v1.json`
- `ANDROID_DEVELOPER_HANDOFF.md`

## What Not To Do Next

Do not add more words yet.

Reason:

- current model quality is limited by data consistency and word separation
- adding vocabulary before fixing `DOCTOR`, `WATER`, `THANKYOU`, `YES`, and `NO` will make confusion worse

Do not retrain using questionable samples if those samples were recorded before the mirror mapping fix.

Reason:

- older left/right recordings may have selected the wrong MediaPipe hand
- the archive tool exists specifically to remove those samples safely

Do not switch to a two-hand dynamic architecture now.

Reason:

- current product objective is one dominant hand
- the latest feature policy now directly supports that objective

## Current Best Engineering Sequence

1. Archive the latest questionable manual groups for `DOCTOR`, `WATER`, `THANKYOU`, `YES`, and `NO`.
2. Record clean right-hand samples using `VOXGEST_DOMINANT_HAND='right'`.
3. Record clean left-hand samples using `VOXGEST_DOMINANT_HAND='left'`.
4. Re-extract video data.
5. Retrain the LSTM.
6. Run saved-data sanity test.
7. Run live diagnostic separately for right and left.
8. Only after the current 10 words are stable, add more words.
9. After word quality is stable, build sentence assembly from accepted word tokens.

## 2026-04-29 Regression Note

Observed after one retrain:

- `HELP` and `PLEASE` were unrecognized.
- `DOCTOR`, `YES`, `NO`, `WATER`, and `HELLO` worked only with the left hand.

Dataset audit showed:

- clean right/left metadata existed for `DOCTOR`, `YES`, `NO`, `WATER`, and `THANKYOU`
- old metadata-less manual samples still existed for `HELP`, `PLEASE`, `HELLO`, `STOP`, `NAME`, and `NOTHING`

Correction added:

- strict single-hand training now ignores old manual samples missing hand-policy metadata
- the progress log is kept in `VOXGEST_PROGRESS_LOG.md`

## Simple Explanation For Non-Technical Review

The system used to know which hand landmarks to read, but the body pose data still included both arms. That meant if a word was recorded using two hands, the model could accidentally learn the second arm as part of the word. The current fix makes the model focus on one selected hand and its matching arm. It also fixes mirrored webcam left/right confusion, so choosing `left` now means the user's real left hand.

## Current Status In One Paragraph

VoxGest now has an 11-class dynamic LSTM model with 10 communication words plus `NOTHING`, a single-hand dominant feature policy, mirrored-webcam hand correction, sample-aware training metadata, and a safe archiving workflow for contaminated manual recordings. The next required work is data cleanup and clean re-recording for `DOCTOR`, `WATER`, `THANKYOU`, `YES`, and `NO`, followed by extraction, retraining, saved-data testing, and live right/left-hand testing.
