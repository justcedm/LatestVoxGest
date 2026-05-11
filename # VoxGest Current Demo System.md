# VoxGest Current Demo System

This workspace is cleaned for the current defense-ready VoxGest pipeline:

- Static alphabet recognition: `A-Z`, `del`, `space`, `nothing`
- LSTM word recognition demo profile: `YES`, `NO`, `PLEASE`, `WATER`, `HELLO`, `HELP`, `STOP`, `DOCTOR`, `NAME`, `THANKYOU`
- Active negative word class: `NOTHING`

## Main Commands

Run live combined recognition:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\20_webcam_dual.py
```

Run word-only live mode:

```powershell
$env:VOXGEST_DOMINANT_HAND='auto'
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\20_webcam_dual.py
```

Run LSTM word diagnostic:

```powershell
$env:VOXGEST_DOMINANT_HAND='auto'
.\voxgest_env\Scripts\python.exe scripts_ml\22_diagnostic_lstm.py
```

For strict one-hand recognition, use the same dominant-hand setting across
recording, training, diagnostics, and live testing:

```powershell
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_SINGLE_HAND_POSE='1'
```

`VOXGEST_DOMINANT_HAND` means the signer physical hand. Webcam input is mirrored
before MediaPipe, and the code maps that physical hand to the correct MediaPipe
label internally.

Record negative/open-hand samples:

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py NOTHING
```

Archive contaminated manual samples before re-recording:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR --apply
```

Retrain the demo10 word model:

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
.\voxgest_env\Scripts\python.exe scripts_ml\19_train_lstm.py
```

## Current Structure

- `scripts_ml/word_config.py` - active word profile configuration
- `scripts_ml/lstm_features.py` - shared feature extraction and sequence gates
- `scripts_ml/16_record_manual_words.py` - manual webcam sequence recorder
- `scripts_ml/18_extract_lstm.py` - WLASL video sequence extractor
- `scripts_ml/19_train_lstm.py` - LSTM word trainer
- `scripts_ml/20_webcam_dual.py` - main live recognizer
- `scripts_ml/22_diagnostic_lstm.py` - word model diagnostic
- `dataset_words_lstm/` - current LSTM sequence data
- `wlasl_videos/` - current demo5 source videos
- `model/` - current runtime models, labels, and reports

## Current Status

The saved LSTM model is trained for `YES`, `NO`, `PLEASE`, `WATER`, `HELLO`,
`HELP`, `STOP`, `DOCTOR`, `NAME`, `THANKYOU`, and `NOTHING`.

For word-sign testing, use `WORDS` mode. Live recognition quality depends
heavily on your recorded manual calibration samples. If a word still feels weak,
record another clean manual session for that word and retrain the LSTM.

Current weak focus words are `WATER`, `THANKYOU`, `YES`, and `NO`. The dynamic
feature extractor supports `VOXGEST_DOMINANT_HAND` values of `auto`, `right`,
and `left`; use the same value for recording, extraction, training, diagnostics,
and live testing. `VOXGEST_SINGLE_HAND_POSE=1` masks pose to head, torso, and
the selected arm so two-hand movement does not become part of the learned word.
