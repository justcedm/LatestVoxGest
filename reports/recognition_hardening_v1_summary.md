# Recognition Hardening v1 Summary

Status: baseline protected, dataset audit complete, first alphabet live pass complete.

## Baseline

- Branch: `recognition-hardening-v1`
- Baseline commit: `d1d176db`
- Baseline tag: `rh-v1-baseline`
- Phrase recognition default: disabled with `VOXGEST_ENABLE_PHRASE='0'`

## Active Scope

Static alphabet:

```text
A-Z, del, space, nothing
```

Dynamic words:

```text
YES, NO, PLEASE, WATER, HELLO, HELP, STOP, DOCTOR, NAME, THANKYOU
```

Dynamic negative class:

```text
NOTHING
```

No new words and no phrase-intent expansion are part of this pass.

## Dataset Audit

Generated:

```text
reports/recognition_audit_words.json
reports/recognition_audit_words.csv
```

| Class | Sequences | Groups | Manual Samples | Manual Groups | Minimum Met | Current Guidance |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| YES | 580 | 25 | 180 | 3 | Yes | Focus live calibration |
| NO | 519 | 23 | 120 | 2 | Yes | Focus live calibration |
| PLEASE | 440 | 21 | 60 | 1 | Yes | Focus live calibration |
| WATER | 580 | 25 | 180 | 3 | Yes | Focus live calibration |
| HELLO | 460 | 23 | 60 | 1 | Yes | Focus live calibration |
| HELP | 440 | 21 | 60 | 1 | Yes | Ready for retraining/live test |
| STOP | 460 | 23 | 60 | 1 | Yes | Ready for retraining/live test |
| DOCTOR | 541 | 22 | 180 | 3 | Yes | Focus live calibration |
| NAME | 460 | 23 | 60 | 1 | Yes | Ready for retraining/live test |
| THANKYOU | 580 | 25 | 180 | 3 | Yes | Focus live calibration |
| NOTHING | 360 | 6 | 360 | 6 | Yes | Add hard negatives if false positives appear |

Focus classes:

```text
WATER, THANKYOU, YES, NO, DOCTOR, PLEASE, HELLO, NOTHING
```

## Alphabet Live Results

First live pass generated:

```text
reports/static_alphabet_live_eval.json
reports/static_alphabet_live_eval.csv
reports/static_alphabet_live_confusion.csv
```

Summary:

| Class | Accuracy | Result |
| --- | ---: | --- |
| A | 100% | pass |
| B | 100% | pass |
| C | 100% | pass |
| D | 90% | pass but watch space confusion |
| E | 100% | pass |
| F | 100% | pass |
| G | 70% | weak |
| H | 80% | weak |
| I | 100% | pass |
| J | 0% | must move to dynamic motion-letter model |
| K | 90% | pass |
| L | 100% | pass |
| M | 100% | pass |
| N | 90% | pass but watch M confusion |
| O | 100% | pass |
| P | 10% | weak |
| Q | 80% | weak |
| R | 100% | pass |
| S | 100% | pass |
| T | 90% | pass |
| U | 100% | pass |
| V | 100% | pass |
| W | 100% | pass |
| X | 0% | weak |
| Y | 100% | pass |
| Z | 90% | usable in first pass, but should be treated as a motion letter |
| del | not tested | control gesture still undefined for user |
| space | not tested | control gesture still undefined for user |
| nothing | not tested | must be tested as idle/no-hand/relaxed-hand |

Interpretation:

- Static alphabet is already strong for most letters.
- J cannot be solved reliably as a one-frame static pose because the sign is motion-based.
- Z should also be supported by the same motion-letter path even though this first run scored 90%.
- G, H, P, Q, and X need targeted static calibration and/or signing/framing correction.
- `nothing` must be tested because idle suppression is part of the alphabet success gate.

Next alphabet commands:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MOTION_LETTER_SEQUENCES_PER_LABEL='20'
.\voxgest_env\Scripts\python.exe scripts_ml\34_record_motion_letters.py J Z NOTHING
.\voxgest_env\Scripts\python.exe scripts_ml\34_record_motion_letters.py J Z NOTHING
.\voxgest_env\Scripts\python.exe scripts_ml\34_record_motion_letters.py J Z NOTHING
.\voxgest_env\Scripts\python.exe scripts_ml\35_train_motion_letter_tcn.py
```

Then, only for weak static classes:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\31_record_static_calibration.py G H P Q X nothing
.\voxgest_env\Scripts\python.exe scripts_ml\32_train_static_landmark_v4.py
```

Alphabet success gate remains:

- average alphabet accuracy 95% or higher
- each letter/control 85% or higher
- `nothing` does not hallucinate letters
- `space` and `del` are usable

## Motion Letters

New optional motion-letter path has been prepared for J/Z:

```text
scripts_ml/motion_letter_config.py
scripts_ml/34_record_motion_letters.py
scripts_ml/35_train_motion_letter_tcn.py
```

Contract:

```text
input:  [1, 30, 162]
output: [1, 3]
labels: J, Z, NOTHING
```

This model is separate from the 10-word dynamic model. Accepted `J` and `Z` predictions enter the token composer as alphabet letters. `NOTHING` is ignored.

## Word Live Results

Pending. Run:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DYNAMIC_MODEL='auto'
$env:VOXGEST_MODE='WORDS'
$env:VOXGEST_DOMINANT_HAND='right'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

The logger now writes both:

```text
reports/live_word_test_log_<timestamp>.csv
reports/live_word_test_log_<timestamp>.json
```

## Current Model Comparison

| Model | Profile | Classes | Grouped Validation | Contract Status |
| --- | --- | --- | ---: | --- |
| LSTM | demo10 | 11 | 50.79% | Usable current fallback |
| TCN | sentence11 | 12 | 57.93% | Not trusted for hardening because it includes `ASK_NAME` |

Current active dynamic decision:

```text
Use LSTM until TCN is retrained with VOXGEST_WORD_PROFILE='demo10' and live-tested.
```

Do not choose TCN just because its old validation score is higher. It still has the wrong label contract.

## Retraining Gate

Retrain only after targeted recording or after confirming current live failure modes.

LSTM:

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\19_train_lstm.py
```

TCN:

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\24_train_tcn.py
```

## Android Handoff Status

Stable enough for Android shell work to continue:

- CameraX preview
- MediaPipe landmarks
- static TFLite inference
- optional motion-letter inference for J/Z
- token composer
- text-to-speech
- dynamic 10-word inference
- speech-to-text reply path
- avatar playback contract
- debug/conversation log

ML hardening continues in Python while Android app shell and inference plumbing are built.

## Remaining Issues

- Dynamic motion-letter model still needs recorded J/Z/NOTHING data and training.
- Static weak letters G, H, P, Q, and X need targeted calibration or signing/framing correction.
- `del`, `space`, and `nothing` controls still need live testing/gesture definition.
- Live word evaluation has not been performed in this pass.
- Dynamic TCN must be retrained under `demo10` before it can be active.
- `NOTHING` must be live-tested for idle, transition, and partial-sign suppression.
