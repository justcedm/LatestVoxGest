# VoxGest Current Demo System

## Recognition Hardening v1 Priority

Current development is frozen on phrase-intent expansion. Phrase files may stay
in the repo, but live testing defaults to:

- static alphabet: `A-Z`, `del`, `space`, `nothing`
- dynamic words: `YES`, `NO`, `PLEASE`, `WATER`, `HELLO`, `HELP`, `STOP`, `DOCTOR`, `NAME`, `THANKYOU`
- dynamic negative/no-output class: `NOTHING`

Sentence output is built only from confirmed accepted tokens:

- `A-Z` append characters
- `space` commits the current spelled word boundary
- `del` deletes
- accepted word gestures append full word tokens
- `NOTHING` is ignored and never displayed/spoken as a word

Phrase recognition is opt-in only:

```powershell
$env:VOXGEST_ENABLE_PHRASE='1'
```

This workspace is cleaned for the current defense-ready VoxGest pipeline:

- Static alphabet recognition: `A-Z`, `del`, `space`, `nothing`
- LSTM word recognition demo profile: `YES`, `NO`, `PLEASE`, `WATER`, `HELLO`, `HELP`, `STOP`, `DOCTOR`, `NAME`, `THANKYOU`
- Active negative word class: `NOTHING`

## Main Commands

Run live combined recognition:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
.\voxgest_env\Scripts\python.exe scripts_ml\20_webcam_dual.py
```

Run word-only live mode:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_DOMINANT_HAND='auto'
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\20_webcam_dual.py
```

Run letter-only live mode:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_MODE='LETTERS'
.\voxgest_env\Scripts\python.exe scripts_ml\20_webcam_dual.py
```

Audit the 10-word + `NOTHING` dataset:

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
.\voxgest_env\Scripts\python.exe scripts_ml\30_audit_recognition_dataset.py
```

Run the repeatable live alphabet protocol:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\30_eval_static_alphabet_live.py
```

Record static calibration samples when weak alphabet signs are found:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\31_record_static_calibration.py Q T Z nothing
```

Train static landmark v4 after enough calibration exists:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\32_train_static_landmark_v4.py
```

Record motion-based alphabet letters `J` and `Z` separately from the word
model:

```powershell
$env:VOXGEST_ENABLE_PHRASE='0'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_MOTION_LETTER_SEQUENCES_PER_LABEL='20'
.\voxgest_env\Scripts\python.exe scripts_ml\34_record_motion_letters.py J Z NOTHING
```

Run the recorder three separate times so each class has at least three manual
groups.

Train the optional J/Z motion-letter TCN:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\35_train_motion_letter_tcn.py
```

Log live word tests with gate metrics:

```powershell
$env:VOXGEST_MODE='WORDS'
$env:VOXGEST_DYNAMIC_MODEL='auto'
.\voxgest_env\Scripts\python.exe scripts_ml\33_live_word_test_logger.py
```

Run static alphabet diagnostic:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\21_diagnostic.py
```

Run LSTM word diagnostic:

```powershell
$env:VOXGEST_DOMINANT_HAND='auto'
.\voxgest_env\Scripts\python.exe scripts_ml\22_diagnostic_lstm.py
```

For strict one-hand work, replace `auto` with the actual signing hand:

```powershell
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_SINGLE_HAND_POSE='1'
```

`VOXGEST_DOMINANT_HAND` means the signer physical hand. Webcam scripts mirror
the frame before MediaPipe, so the code maps physical `left`/`right` to the
correct MediaPipe hand label internally.

Record negative/open-hand samples:

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py NOTHING
```

For `NOTHING`, record hard negatives: idle hands, natural transitions,
partial/incomplete signs, aborted signs, and hand entering/leaving frame.
`NOTHING` is no-output data, not a word token.

Record webcam calibration samples for current weak words:

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_MANUAL_SEQUENCES_PER_WORD='60'
.\voxgest_env\Scripts\python.exe scripts_ml\16_record_manual_words.py WATER THANKYOU YES NO
```

Archive contaminated manual samples before re-recording a word:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR --apply
```

Archive only the latest mistaken recording group:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR --latest-groups 1
.\voxgest_env\Scripts\python.exe scripts_ml\23_archive_manual_samples.py DOCTOR --latest-groups 1 --apply
```

Retrain the demo10 word model:

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
.\voxgest_env\Scripts\python.exe scripts_ml\19_train_lstm.py
```

Train the faster TCN motion model on the same 30x162 landmark sequences:

```powershell
$env:VOXGEST_WORD_PROFILE='demo10'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\24_train_tcn.py
```

Run live mode with the best available dynamic model. `auto` chooses the model
with the stronger saved training report and falls back when one model is absent:

```powershell
$env:VOXGEST_DYNAMIC_MODEL='auto'
$env:VOXGEST_MODE='WORDS'
.\voxgest_env\Scripts\python.exe scripts_ml\20_webcam_dual.py
```

Phrase-intent work is frozen during Recognition Hardening v1. The old commands
remain below only for later reference; they are not part of the current pass.

Record endpoint-based one-hand phrase intent samples:

```powershell
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_PHRASE_SEQUENCES_PER_LABEL='60'
.\voxgest_env\Scripts\python.exe scripts_ml\26_record_phrase_intents.py ASK_NAME
.\voxgest_env\Scripts\python.exe scripts_ml\26_record_phrase_intents.py NOTHING
.\voxgest_env\Scripts\python.exe scripts_ml\26_record_phrase_intents.py PARTIAL_ASK_NAME
```

Train the separate 60-frame phrase-intent TCN:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\27_train_phrase_tcn.py
```

Phrase trainer readiness uses separate recording groups. If you already have
one 60-sample session for `ASK_NAME` and `NOTHING`, use two shorter sessions per
label instead of one long tiring session:

```powershell
$env:VOXGEST_PHRASE_SEQUENCES_PER_LABEL='30'
.\voxgest_env\Scripts\python.exe scripts_ml\26_record_phrase_intents.py ASK_NAME
.\voxgest_env\Scripts\python.exe scripts_ml\26_record_phrase_intents.py NOTHING
.\voxgest_env\Scripts\python.exe scripts_ml\26_record_phrase_intents.py ASK_NAME
.\voxgest_env\Scripts\python.exe scripts_ml\26_record_phrase_intents.py NOTHING
```

Extract selected phrase-intent videos before webcam calibration:

```powershell
# Place selected videos under phrase_videos\ASK_NAME, phrase_videos\NOTHING,
# and phrase_videos\PARTIAL_ASK_NAME.
.\voxgest_env\Scripts\python.exe scripts_ml\28_extract_phrase_videos.py ASK_NAME NOTHING PARTIAL_ASK_NAME
```

Check expansion readiness for the 25-word sprint:

```powershell
$env:VOXGEST_WORD_PROFILE='sprint25'
.\voxgest_env\Scripts\python.exe scripts_ml\25_sprint_status.py
```

## Current Structure

- `scripts_ml/word_config.py` - active word profile configuration
- `scripts_ml/lstm_features.py` - shared feature extraction and sequence gates
- `scripts_ml/16_record_manual_words.py` - manual webcam sequence recorder
- `scripts_ml/18_extract_lstm.py` - WLASL video sequence extractor
- `scripts_ml/19_train_lstm.py` - LSTM word trainer
- `scripts_ml/20_webcam_dual.py` - main live recognizer
- `scripts_ml/21_diagnostic.py` - static alphabet diagnostic
- `scripts_ml/22_diagnostic_lstm.py` - word model diagnostic
- `scripts_ml/26_record_phrase_intents.py` - endpoint phrase-intent recorder
- `scripts_ml/27_train_phrase_tcn.py` - separate 60-frame phrase TCN trainer
- `scripts_ml/28_extract_phrase_videos.py` - selected phrase video extractor
- `scripts_ml/34_record_motion_letters.py` - J/Z motion-letter recorder
- `scripts_ml/35_train_motion_letter_tcn.py` - optional J/Z motion-letter trainer
- `scripts_ml/gesture_segmenter.py` - phrase start/end state machine
- `scripts_ml/motion_letter_config.py` - dynamic alphabet letter labels
- `scripts_ml/phrase_config.py` - phrase labels and output mapping
- `dataset_words_lstm/` - current LSTM sequence data
- `dataset_phrase_intents/` - endpoint phrase-intent sequence data
- `phrase_videos/` - optional selected phrase source videos
- `wlasl_videos/` - current demo5 source videos
- `model/` - current runtime models, labels, and reports

## Current Status

The saved LSTM model is trained for `YES`, `NO`, `PLEASE`, `WATER`, `HELLO`,
`HELP`, `STOP`, `DOCTOR`, `NAME`, `THANKYOU`, and `NOTHING`.

`AUTO` mode now protects detected letters first. A word such as `HELLO` only
overrides a letter when confidence, margin, and wrist movement are all strong.
Use `LETTERS` mode or `21_diagnostic.py` when checking difficult letters such
as `Q`, `T`, and `Z`.

For word-sign testing, use `WORDS` mode. Live recognition quality now depends
heavily on your recorded manual calibration samples. If a word still feels weak,
record another clean manual session for that word and retrain the LSTM.

The live word gate is word-specific: compact signs `YES`, `NO`, and `WATER`
are allowed with smaller wrist travel and faster stable-frame acceptance.
`THANKYOU` also has a lighter live gate because it was weak after expansion.
The dynamic feature extractor supports `VOXGEST_DOMINANT_HAND` values of
`auto`, `right`, and `left`; use the same value for recording, extraction,
training, diagnostics, and live testing. By default, `VOXGEST_SINGLE_HAND_POSE`
masks pose landmarks down to head, torso, and the selected arm so two-hand
movement does not leak into the word model through pose.

Phrase intents are now separate from short words. A full sentence shortcut such
as `ASK_NAME -> What is your name?` uses endpoint segmentation and a 60-frame
phrase TCN, so it waits for complete motion plus a final hold instead of firing
from the first 30-frame prefix. During Recognition Hardening v1 this path is
disabled by default with `VOXGEST_ENABLE_PHRASE='0'`.

## Android Handoff

Use [ANDROID_DEVELOPER_HANDOFF.md](./ANDROID_DEVELOPER_HANDOFF.md) as the exact
handoff for the Android app developer. The machine-readable runtime contract is
in [model/runtime_manifest_v1.json](./model/runtime_manifest_v1.json).

## Stronger Next Move

The current best next step is Recognition Hardening v1:

- audit the word dataset with `30_audit_recognition_dataset.py`
- live-test `A-Z`, `del`, `space`, and `nothing` with
  `30_eval_static_alphabet_live.py`
- record static calibration only for weak alphabet signs
- live-test all 10 words in `WORDS` mode
- record extra manual sessions for weak words such as `WATER`, `THANKYOU`,
  `YES`, `NO`, `DOCTOR`, `PLEASE`, and `HELLO`
- keep `NOTHING` in the model as the negative/no-word class
- save partial/incomplete/transition movements as `NOTHING`
- retrain both `19_train_lstm.py` and `24_train_tcn.py`
- compare validation with live behavior before choosing the active model
- tune per-word live thresholds from diagnostic results

Do not add vocabulary or phrase-level sentence gestures until these gates pass.
