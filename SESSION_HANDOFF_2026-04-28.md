# VoxGest Session Handoff - 2026-04-28

## Purpose

This file is the authoritative handoff for the next Codex chat and for external documentation review.

Use it for:

- continuing the current engineering work without losing context
- forwarding a precise technical summary to Claude
- preserving the current design constraints and next-step plan

## Environment

- project root: `C:\BSIT 3RD YEAR\New VovGest`
- OS: Windows
- shell: PowerShell
- Python environment used in commands: `.\voxgest_env\Scripts\python.exe`
- current date during latest update: `2026-04-29`
- timezone: `Asia/Manila`

## Product Objective

The product goal is a real-time utility app for deaf and hard of hearing users that uses the device camera for sign-to-text communication on Android.

Immediate engineering objective:

- make the current dynamic word recognizer respond clearly and quickly
- keep the runtime offline and device-local
- keep the dynamic recognizer focused on one dominant hand
- stabilize the current 10 communicative words plus `NOTHING` before adding more words or moving to sentence assembly

## Current Scope

Static recognition currently exists for:

- `A-Z`
- `del`
- `space`
- `nothing`

Dynamic recognition currently exists for:

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

Negative or non-word target:

- `NOTHING`
- `NOTHING` is now inside the trained dynamic model
- `NOTHING` should be treated as a negative/no-word class, not as a spoken output token

## Design Constraint: Dominant Hand

The user wants the system to prioritize a dominant hand for sign language use, because the target application is a communication utility app and the immediate goal is reliable live recognition rather than full multi-hand sign understanding.

Current status:

- the dynamic recognizer is already single-hand oriented
- the feature extractor uses pose plus only one hand
- the hand selection policy is configurable with `VOXGEST_DOMINANT_HAND`
- valid values are `auto`, `right`, and `left`
- `right` and `left` mean the signer physical hand
- webcam scripts mirror input before MediaPipe, so physical hand preference is mapped to the correct MediaPipe label internally
- `auto` prefers the physical right hand first when webcam input is mirrored
- `VOXGEST_SINGLE_HAND_POSE=1` masks pose down to head, torso, and the selected arm
- this prevents two-hand movement from leaking through the pose section of the 162-float vector

Exact implementation reference:

- [`scripts_ml/lstm_features.py`](./scripts_ml/lstm_features.py)
- `configured_hand_preference()`
- `select_hand_landmarks()`
- `extract_frame_features()`
- `mask_pose_to_single_hand()`
- `apply_sequence_feature_policy()`

Implications:

- the current dynamic pipeline is already consistent with the dominant-hand objective
- we should not switch to a two-hand dynamic feature architecture yet
- recording, diagnostics, and live testing should use the same `VOXGEST_DOMINANT_HAND` value
- extraction and training should also use that same value
- Android should expose this as a user/app setting when moving past the prototype

## Current Model Contract

Current dynamic model artifacts:

- [`model/voxgest_lstm_v1.h5`](./model/voxgest_lstm_v1.h5)
- [`model/voxgest_lstm_v1.tflite`](./model/voxgest_lstm_v1.tflite)
- [`model/class_labels_lstm_v1.json`](./model/class_labels_lstm_v1.json)
- [`model/lstm_training_report.json`](./model/lstm_training_report.json)

Current LSTM runtime contract:

- input tensor shape: `[1, 30, 162]`
- output tensor shape: `[1, 11]`

Interpretation:

- `30` frames per decision window
- `162` features per frame
- dynamic classes currently total `11`
- `NOTHING` is active as the negative/no-word class

Dynamic label order:

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

## Current Training Report Snapshot

Source:

- [`model/lstm_training_report.json`](./model/lstm_training_report.json)

Current report values:

- `word_profile`: `demo10`
- `best_grouped_val_accuracy`: `66.61952137947083`
- `train_sequences`: `4653`
- `val_sequences`: `707`
- `train_groups`: `195`
- `val_groups`: `41`

Current per-class grouped validation accuracy:

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

Current top confusion mapping:

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

Interpretation:

- the model now includes `NOTHING`
- grouped validation improved from the prior report but is still not strong enough for sentence-level use
- `WATER` and `THANKYOU` are current live weak spots
- `YES` and `NO` are also harder than expected in live use despite moderate grouped validation

## Current Data Status

From the current training report:

- `YES`: `460 sequences`, `23 groups`
- `NO`: `519 sequences`, `23 groups`
- `PLEASE`: `440 sequences`, `21 groups`
- `WATER`: `640 sequences`, `26 groups`
- `HELLO`: `460 sequences`, `23 groups`
- `HELP`: `500 sequences`, `22 groups`
- `STOP`: `460 sequences`, `23 groups`
- `DOCTOR`: `481 sequences`, `21 groups`
- `NAME`: `460 sequences`, `23 groups`
- `THANKYOU`: `700 sequences`, `27 groups`
- `NOTHING`: `240 sequences`, `4 groups`, `used: true`

Important trainer limits from [`scripts_ml/19_train_lstm.py`](./scripts_ml/19_train_lstm.py):

- `MIN_SEQS = 80` at line 50
- `MIN_GROUPS = 4` at line 51

Implication:

- `NOTHING` now meets the training minimums and is included in the model
- more `NOTHING` data can still improve false-positive control later

## What Was Done In This Chat

### 1. Repository and runtime validation

We validated that the current project could actually run and was not just a stale training repo.

What was checked:

- Python scripts compiled
- models loaded
- camera access worked
- live recognizer launched

Practical result:

- the system was confirmed usable enough for iterative data collection and retraining

### 2. Manual dynamic-word recording workflow was established

We standardized the current capture workflow around:

- manual recorder: [`scripts_ml/16_record_manual_words.py`](./scripts_ml/16_record_manual_words.py)
- trainer: [`scripts_ml/19_train_lstm.py`](./scripts_ml/19_train_lstm.py)
- diagnostic: [`scripts_ml/22_diagnostic_lstm.py`](./scripts_ml/22_diagnostic_lstm.py)
- live runtime: [`scripts_ml/20_webcam_dual.py`](./scripts_ml/20_webcam_dual.py)

User-facing operating rules clarified during this chat:

- recorder target count matters more than raw repetition count
- repeating the same target word continuously during capture is acceptable
- `NOTHING` should be neutral non-word movement, not total stillness

### 3. The dynamic vocabulary was expanded from 5 words to 10 words

Original active profile:

- `demo5`

Added next 5 words:

- `HELP`
- `STOP`
- `DOCTOR`
- `NAME`
- `THANKYOU`

Exact code references:

- [`scripts_ml/word_config.py`](./scripts_ml/word_config.py)
- `NEXT5_WORDS` at line 48
- `DEMO10_WORDS` at line 56
- `WORD_PROFILES["demo10"]` at line 99

Important shell behavior:

- if `VOXGEST_WORD_PROFILE` is not set, the config falls back to `demo5`
- this caused an early recording error when the user attempted to record `DOCTOR`, `THANKYOU`, and `HELP` under the wrong profile
- correct shell setup for 10-word work requires `VOXGEST_WORD_PROFILE='demo10'`

### 4. Android handoff assets were created and updated

Created and then updated:

- [`ANDROID_DEVELOPER_HANDOFF.md`](./ANDROID_DEVELOPER_HANDOFF.md)
- [`model/runtime_manifest_v1.json`](./model/runtime_manifest_v1.json)

These files were updated to reflect:

- current `demo10` profile
- current 11-class LSTM output
- runtime thresholds for the expanded word set
- dominant-hand policy notes
- Android-side scope for an offline on-device implementation

### 5. Runtime threshold rules were patched for the added words

Problem found:

- `20_webcam_dual.py` and `22_diagnostic_lstm.py` originally had tuned rules mainly for the initial 5 words
- after moving to a 10-word model, the added words were still using generic defaults
- this made the live diagnostic feel inconsistent or too strict for the new words

Fix applied in the main runtime:

- [`scripts_ml/20_webcam_dual.py`](./scripts_ml/20_webcam_dual.py)
- `WORDS_MODE_WORD_RULES` starts at line 60
- added or revised explicit rules for compact and weak live words
- added `NOTHING` as a no-output negative class
- added per-word stable-frame tuning in `WORDS` mode

Fix applied in the diagnostic:

- [`scripts_ml/22_diagnostic_lstm.py`](./scripts_ml/22_diagnostic_lstm.py)
- `WORD_RULES` starts at line 38
- synced diagnostic thresholds with the live recognizer
- added `NOTHING` diagnostic thresholds

Current `WORDS` mode rules:

- `YES`: `conf 0.56`, `margin 0.07`, `motion 0.008`, `path 0.05`, `stable 5`
- `NO`: `conf 0.55`, `margin 0.06`, `motion 0.008`, `path 0.04`, `stable 5`
- `PLEASE`: `conf 0.68`, `margin 0.18`, `motion 0.025`, `path 0.25`
- `WATER`: `conf 0.56`, `margin 0.06`, `motion 0.006`, `path 0.04`, `stable 5`
- `HELLO`: `conf 0.70`, `margin 0.18`, `motion 0.030`, `path 0.30`
- `HELP`: `conf 0.60`, `margin 0.10`, `motion 0.025`, `path 0.20`
- `STOP`: `conf 0.62`, `margin 0.10`, `motion 0.025`, `path 0.20`
- `DOCTOR`: `conf 0.70`, `margin 0.15`, `motion 0.020`, `path 0.20`
- `NAME`: `conf 0.55`, `margin 0.05`, `motion 0.020`, `path 0.20`
- `THANKYOU`: `conf 0.52`, `margin 0.03`, `motion 0.010`, `path 0.08`, `stable 6`
- `NOTHING`: `conf 0.55`, `margin 0.05`, `motion 0.000`, `path 0.00`, `presence 0.00`, `stable 4`

### 6. Angle robustness was improved in the extractor

Problem found:

- the user reported that hand recognition becomes inconsistent at different signing angles
- the practical lever available in this repo is improving the recognizer training data, not retraining MediaPipe itself

Fix applied:

- added `aug_x_rotation()` at line 120
- retained and used `aug_y_rotation()` at line 108
- added `aug_z_rotation()` at line 132
- expanded augmentation combinations in `augment_sequence()` at line 144

Exact reference:

- [`scripts_ml/18_extract_lstm.py`](./scripts_ml/18_extract_lstm.py)

Important constraint:

- this change affects only future extracted data
- it has no benefit until `18_extract_lstm.py` is rerun and the model is retrained

### 7. Multiple manual record / train / test cycles were completed

What happened operationally:

- the user recorded manual data for the original 5 words
- the dynamic model was retrained
- the user then expanded to the next 5 words and retrained again
- the user recorded enough `NOTHING` sessions for it to enter training
- the project now has a current 10-word plus `NOTHING` trained model and TFLite export

Latest export state:

- saved TFLite model: `model\voxgest_lstm_v1.tflite`
- export smoke check succeeded
- current output dimension is `11`

### 8. Live behavior was observed and interpreted

User-reported live findings during this chat included:

- `DOCTOR` confused with `HELP`
- `NO` confused with `HELP`
- `THANKYOU` confused with multiple words
- `HELLO` and `PLEASE` were comparatively better in one test round
- later, the user reported `WATER` and `THANKYOU` as very weak live
- after the latest retrain, the user also reported `YES` and `NO` as harder to recognize

Interpretation:

- the project is past the stage of basic bring-up
- the current bottleneck is not "can the code run"
- the bottleneck is class separation, angle robustness, and live acceptance behavior

### 9. Single-hand pose policy was added after two-hand recording contamination

Problem found:

- some manual samples were recorded with two-hand movement
- the project objective is now one dominant hand
- even though the hand feature used one selected hand, the pose feature still contained full body/arm landmarks
- that allowed non-dominant arm motion to become part of learned word patterns

Fix applied:

- added `VOXGEST_SINGLE_HAND_POSE`, enabled by default
- feature extraction now keeps head, torso, and selected arm pose landmarks
- non-dominant arm and unrelated lower-body pose landmarks are zeroed
- trainer and saved-data tester apply the same feature policy to existing `.npy` sequences at load time
- new manual recordings and video extraction use the same policy automatically

Added recovery tool:

- [`scripts_ml/23_archive_manual_samples.py`](./scripts_ml/23_archive_manual_samples.py)
- dry run by default
- `--apply` moves contaminated manual files to `dataset_words_lstm/_archived_manual`
- metadata entries are removed so training ignores archived samples

New detailed report:

- [`SINGLE_HAND_DOMINANT_UPDATE_2026-04-29.md`](./SINGLE_HAND_DOMINANT_UPDATE_2026-04-29.md)

## Current Documentation Files

Files created or updated during this work:

- [`README_CURRENT.md`](./README_CURRENT.md)
- [`# VoxGest Current Demo System.md`](./#%20VoxGest%20Current%20Demo%20System.md)
- [`ANDROID_DEVELOPER_HANDOFF.md`](./ANDROID_DEVELOPER_HANDOFF.md)
- [`model/runtime_manifest_v1.json`](./model/runtime_manifest_v1.json)
- [`SESSION_HANDOFF_2026-04-28.md`](./SESSION_HANDOFF_2026-04-28.md)

## Known Problems And Risks

Current engineering risks:

- grouped validation is not yet strong enough for sentence-level deployment
- live recognition is still sensitive to signer angle and motion style
- `NOTHING` is now active but still new
- threshold tuning alone will not fully solve weak separation if the data distribution is still narrow

Specific weak spots right now:

- `WATER` is weak in both report data and user live feedback
- `THANKYOU` remains a live weak spot and is confused with `YES`
- `YES` and `NO` are harder live than expected
- `HELLO` and `PLEASE` remain weak enough to watch closely

## What Should Not Be Done Next

Do not do these next:

- do not add more words immediately
- do not redesign the dynamic model into a two-hand feature pipeline yet
- do not change the tensor shape again unless the model is retrained and the manifest is updated
- do not start sentence generation before the 10 current words are much more stable

Reason:

- vocabulary count is not the current blocker
- data quality and live robustness are the current blocker

## Exact Next Steps

### Priority

Keep the dominant-hand design and strengthen the current 10 communicative words before expanding.

Primary targets:

- `WATER`
- `THANKYOU`
- `YES`
- `NO`

Secondary watch list:

- `HELLO`
- `PLEASE`
- `NOTHING`

### Step 1: record more manual data

Use this command:

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
$env:VOXGEST_DOMINANT_HAND='auto'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py WATER THANKYOU YES NO
```

Recording requirements:

- do at least `2` more separate sessions for `WATER`
- do at least `2` more separate sessions for `THANKYOU`
- do at least `1` more separate session each for `YES` and `NO`
- vary angle, distance, and speed between sessions
- keep the upper body and hands visible
- use the same `VOXGEST_DOMINANT_HAND` value for recording and live testing

### Step 2: rebuild extracted data

Because the augmentation logic changed in `18_extract_lstm.py`, rerun extraction before the next retraining cycle:

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
.\voxgest_env\Scripts\python.exe scripts_ml\18_extract_lstm.py
```

### Step 3: retrain

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
.\voxgest_env\Scripts\python.exe scripts_ml\19_train_lstm.py
```

### Step 4: retest

```powershell
$env:VOXGEST_DOMINANT_HAND='auto'
.\voxgest_env\Scripts\python.exe scripts_ml\22_diagnostic_lstm.py
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\20_webcam_dual.py
```

For each word, log:

- recognized or not
- top confusion
- whether it reached `ACCEPT`
- whether failure was caused by pose loss, low confidence, low motion, or low margin

## Direction For The Next Codex Chat

The next chat should continue with this exact priority order:

1. preserve dominant-hand single-hand dynamic recognition
2. strengthen `WATER`, `THANKYOU`, `YES`, and `NO`
3. rerun extraction with the new angle augmentation
4. retrain and retest
5. only after the current 10 communicative words are stable, consider more words or sentence assembly

## Paste-Ready Brief For The Next Codex Chat

Paste this into the next chat:

```text
Continue from the VoxGest dominant-hand utility-app work.

Current goal:
- keep dynamic recognition focused on one dominant hand
- do not move to a two-hand architecture yet
- make the current 10 communicative dynamic words very strong before adding more words

Current dynamic classes:
YES, NO, PLEASE, WATER, HELLO, HELP, STOP, DOCTOR, NAME, THANKYOU, NOTHING

Current negative class status:
- NOTHING is now trained into the model as class index 10
- current model output shape is [1, 11]

Important files:
- scripts_ml/lstm_features.py
- scripts_ml/18_extract_lstm.py
- scripts_ml/19_train_lstm.py
- scripts_ml/20_webcam_dual.py
- scripts_ml/22_diagnostic_lstm.py
- scripts_ml/word_config.py
- model/lstm_training_report.json
- model/runtime_manifest_v1.json
- ANDROID_DEVELOPER_HANDOFF.md
- SESSION_HANDOFF_2026-04-28.md

Already changed:
- dynamic feature extraction is single-hand with VOXGEST_DOMINANT_HAND support
- added demo10 profile with HELP, STOP, DOCTOR, NAME, THANKYOU
- updated Android handoff and runtime manifest to the 11-class model
- added per-word WORDS thresholds for HELP, STOP, DOCTOR, NAME, THANKYOU, NOTHING
- made YES, NO, WATER, and THANKYOU easier to accept in WORDS mode with lighter gates and faster stable-frame requirements
- added angle augmentation in 18_extract_lstm.py with x/y/z rotations

Current report summary:
- best_grouped_val_accuracy = 66.61952137947083
- weak classes include WATER, THANKYOU, HELLO, PLEASE, STOP
- live concern from user is WATER, THANKYOU, YES, and NO

Next task:
- help me harden WATER, THANKYOU, YES, and NO first
- keep dominant-hand behavior explicit
- tell me exactly how to collect the next manual sessions, rerun extraction, retrain, and verify improvements
```

## Notes For Claude

If this is forwarded to Claude, the useful review targets are:

- whether the dominant-hand design is coherent with the stated utility-app objective
- whether the current data-augmentation strategy is a sufficient next step before larger architecture changes
- whether the current threshold strategy is too fragmented or appropriate for the current word set
- whether the immediate next step should remain data and augmentation focused instead of moving to a more invasive feature redesign

The most important recommendation to preserve is this:

- do not expand vocabulary again until the current 10 words are significantly more stable in live use
