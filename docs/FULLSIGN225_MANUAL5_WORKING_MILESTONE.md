# FullSign225 Manual5 Working Milestone

## Purpose

`fullsign225_manual5` is a practical live-demo milestone for tonight. The goal is not a larger vocabulary count; the goal is a small FullSign225 model that can survive real webcam use.

## Why Manual15 Was Reduced

`fullsign225_manual15` could train, but live recognition was still unstable. The main failure was not just model validation accuracy. Live webcam landmarks were inconsistent: MediaPipe sometimes lost hands, blinked between frames, or produced sudden landmark jumps. That caused `BAD_SEQUENCE`, `UNSTABLE_LANDMARKS`, and wrong accepted labels.

For tonight, forcing 15 labels would make the system look less reliable. A smaller label set gives the model fewer classes to confuse and gives us a clearer pass/fail test.

## Why Validation Alone Was Not Enough

Grouped validation checks whether held-out feature sequences resemble the training distribution. Live signing adds extra variability:

- camera distance changes
- lighting changes
- hand overlap and occlusion happen naturally
- MediaPipe tracking can blink frame to frame
- live motion timing differs from recorded examples

Because VoxGest uses landmarks, the live landmark stream must be stable before the TCN can classify well.

## Dataset

Manual5 reuses the existing webcam-captured FullSign225 dataset:

`external_datasets/fullsign225_manual16_features`

The profile trains only the manual5 labels and ignores all other folders in that dataset.

## Feature Contract

- Feature profile: `fullsign225`
- Input shape: `[1, 30, 225]`
- Per frame: pose `99` + left hand `63` + right hand `63`
- `NOTHING` remains the no-output class

## Target Labels

Training labels:

- `EAT`
- `HELLO`
- `WATER`
- `THANKYOU`
- `NOTHING`

Backup labels if one target fails live:

- `NO`
- `PAIN`
- `DOCTOR`

## Pass/Fail Rule

A label passes only if:

- at least 4 of 5 live trials match the expected label
- there is no dangerous false accept
- there is no repeated confusion with another label

If a label fails, replace it with the strongest backup candidate instead of keeping an unstable word just to reach five labels.

## Safety Position

This model is experimental. It does not replace demo10, onehand162, or any Android default model. Raw predictions must not update sentence output, and `NOTHING` must never be spoken, displayed, saved as a token, or routed to avatar.

## Audit Result

Latest audit result:

- Valid samples: 220
- EAT: 30
- HELLO: 30
- WATER: 30
- THANKYOU: 30
- NOTHING: 100
- Wrong-shape files: 0
- Unreadable files: 0
- Rejected samples logged: 363
- Ready to train TCN: yes

## Training Result

TCN training completed for `fullsign225_manual5`.

- Input shape: `[1, 30, 225]`
- Output shape: `[1, 5]`
- Validation split: random fallback, because each manual label currently has one source group
- Train sequences: 176
- Validation sequences: 44
- Best validation accuracy: 93.18%
- TFLite size: about 309 KB

Per-class validation:

| Label | Correct | Total | Accuracy | Top miss |
| --- | ---: | ---: | ---: | --- |
| EAT | 6 | 6 | 100.0% | - |
| HELLO | 6 | 6 | 100.0% | - |
| WATER | 5 | 6 | 83.3% | NOTHING |
| THANKYOU | 5 | 6 | 83.3% | EAT |
| NOTHING | 19 | 20 | 95.0% | HELLO |

This is a training result only. The profile is not considered demo-ready until the live test passes.

## Commands

Audit:

```powershell
$env:VOXGEST_WORD_PROFILE='fullsign225_manual5'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
.\voxgest_env\Scripts\python.exe scripts_ml\42_audit_fullsign225_manual5_dataset.py
```

Train TCN:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='fullsign225_manual5'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
$env:VOXGEST_SINGLE_HAND_POSE='0'
$env:VOXGEST_LSTM_DATASET='C:\BSIT 3RD YEAR\New VovGest\external_datasets\fullsign225_manual16_features'
$env:VOXGEST_MIN_SEQS_PER_CLASS='30'
$env:VOXGEST_MIN_GROUPS_PER_CLASS='1'
$env:VOXGEST_RANDOM_VAL_FALLBACK='1'
.\voxgest_env\Scripts\python.exe scripts_ml\24_train_tcn.py
```

Live test:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='fullsign225_manual5'
$env:VOXGEST_FEATURE_PROFILE='fullsign225'
$env:VOXGEST_DYNAMIC_MODEL='tcn'
$env:VOXGEST_SINGLE_HAND_POSE='0'
$env:VOXGEST_MODE='WORDS'
$env:VOXGEST_LIVE_TEST_CAPTURE_MODE='hand_trigger_auto'
$env:VOXGEST_LIVE_TEST_LABELS='EAT,NOTHING,HELLO,WATER,THANKYOU'
$env:VOXGEST_LIVE_TEST_TRIALS_PER_LABEL='5'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```
