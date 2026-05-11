# VoxGest Progress Log

## 2026-05-10 - Recognition Hardening v1 Started

Priority change:

- Freeze phrase-intent expansion for now.
- Make alphabet, 10 dynamic words, and `NOTHING` reliable first.
- Build sentence output from confirmed accepted tokens, not phrase shortcuts.

Engineering changes:

- `scripts_ml/20_webcam_dual.py` now defaults phrase recognition off with
  `VOXGEST_ENABLE_PHRASE='0'`.
- Added `scripts_ml/token_composer.py` so accepted `A-Z`, `space`, `del`, and
  word tokens compose sentence text while `NOTHING` remains no-output.
- Added word dataset audit output:
  - `scripts_ml/30_audit_recognition_dataset.py`
  - `reports/recognition_audit_words.json`
  - `reports/recognition_audit_words.csv`
- Added live/static hardening tools:
  - `scripts_ml/30_eval_static_alphabet_live.py`
  - `scripts_ml/31_record_static_calibration.py`
  - `scripts_ml/32_train_static_landmark_v4.py`
  - `scripts_ml/33_live_word_test_logger.py`
- Added avatar dictionary contract under `avatar/`.

Current gates before vocabulary or phrase expansion:

- repeatable live pass for `A-Z`, `del`, `space`, and `nothing`
- clean 10-word + `NOTHING` dataset audit
- extra hard-negative `NOTHING` samples for idle/partial/transition movement
- retrained LSTM and TCN compared by grouped validation plus live behavior
- sentence strip updates only from confirmed accepted predictions

## 2026-05-08 - Endpoint Phrase-Intent Architecture Added

Observed behavior:

- `ASK_NAME` could be recognized before the full sentence gesture finished.
- The cause was structural: the phrase was being tested like a short word in a
  rolling 30-frame classifier window.

Fix applied:

- Added a separate phrase-intent pathway:
  - `scripts_ml/phrase_config.py`
  - `scripts_ml/gesture_segmenter.py`
  - `scripts_ml/26_record_phrase_intents.py`
  - `scripts_ml/27_train_phrase_tcn.py`
- Updated `scripts_ml/20_webcam_dual.py` so phrase intents are classified only
  after endpoint segmentation returns a completed 60-frame segment.
- Updated `scripts_ml/export_tflite_fixed.py` for phrase TCN export.
- Updated documentation and Android runtime contract.

Current phrase model plan:

- `ASK_NAME` maps to `What is your name?`
- `NOTHING` and `PARTIAL_ASK_NAME` are no-output negative phrase labels.
- The word model remains a fast 30-frame recognizer for short word signs.
- The phrase model uses `[1, 60, 162]` complete motion segments.

Required next action:

- Optionally extract selected phrase videos with
  `scripts_ml/28_extract_phrase_videos.py`.
- Record live calibration samples for `ASK_NAME`, `NOTHING`, and
  `PARTIAL_ASK_NAME` using `scripts_ml/26_record_phrase_intents.py`.
- Train with `scripts_ml/27_train_phrase_tcn.py`.
- Live-test with `scripts_ml/20_webcam_dual.py`.

## 2026-04-29 - Single-Hand Data Regression Identified

Observed behavior:

- `HELP` and `PLEASE` were unrecognized.
- `DOCTOR`, `YES`, `NO`, `WATER`, and `HELLO` were recognized only with the left hand.
- The latest grouped validation dropped to about `55%`.

Cause:

- The dataset was not fully clean for the new strict single-hand policy.
- `DOCTOR`, `YES`, `NO`, `WATER`, and `THANKYOU` had clean right/left metadata samples.
- `HELP`, `PLEASE`, `HELLO`, `STOP`, `NAME`, and `NOTHING` still had old manual samples without `dominant_hand`, `mirrored_input`, or `single_hand_pose` metadata.
- Training mixed older ambiguous manual samples with new single-hand samples.

Fix applied:

- `scripts_ml/19_train_lstm.py` now skips manual samples missing hand/mirror/single-hand metadata when `VOXGEST_SINGLE_HAND_POSE=1`.
- `scripts_ml/17_test_word_accuracy.py` now uses the same skip policy so saved-data tests match training behavior.
- The trainer now reports skipped metadata counts per class and in `lstm_training_report.json`.
- The trainer now stops before export if required classes such as `NOTHING` are missing, preventing accidental output-shape changes.

Required next action:

- Archive metadata-less manual samples for the affected words.
- Re-record clean right-hand and left-hand samples for `HELP`, `PLEASE`, and any other weak words.
- Re-record clean `NOTHING` samples if the 11-class negative class must remain active.
- Retrain only after the dataset is clean.

Clean-data audit after the trainer safeguard:

- `YES`: `520` eligible sequences, `120` clean manual samples.
- `NO`: `519` eligible sequences, `120` clean manual samples.
- `WATER`: `520` eligible sequences, `120` clean manual samples.
- `DOCTOR`: `481` eligible sequences, `120` clean manual samples.
- `THANKYOU`: `520` eligible sequences, `120` clean manual samples.
- `PLEASE`: `380` eligible sequences, `0` clean manual samples.
- `HELP`: `380` eligible sequences, `0` clean manual samples.
- `HELLO`: `400` eligible sequences, `0` clean manual samples.
- `STOP`: `400` eligible sequences, `0` clean manual samples.
- `NAME`: `400` eligible sequences, `0` clean manual samples.
- `NOTHING`: `0` eligible sequences, `0` clean manual samples.

Interpretation:

- The next recording pass must prioritize `HELP`, `PLEASE`, and `NOTHING`.
- `NOTHING` needs enough clean groups before the model can remain an 11-class model.
