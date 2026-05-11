# VoxGest Android Developer Handoff

## Recognition Hardening v1 Priority

The Android build should follow this order:

1. Recognition first: static alphabet, 10 dynamic words, and `NOTHING`.
2. Sentence composer second: build text only from confirmed accepted tokens.
3. Avatar playback third: known words use word animations; unknown words
   fingerspell.
4. Phrase intents later: phrase-level sentence gestures are disabled by default
   and are not part of the current Android priority.

Phrase model files may exist in the repo, but they must not interfere with
alphabet or 10-word testing. Match Python default behavior:

```text
VOXGEST_ENABLE_PHRASE=0
```

## Current Scope

This project is ready for an Android prototype app with on-device inference.
No backend is required for recognition.

Current supported recognition:

- Static alphabet: `A-Z`, `del`, `space`, `nothing`
- Dynamic words: `YES`, `NO`, `PLEASE`, `WATER`, `HELLO`, `HELP`, `STOP`, `DOCTOR`, `NAME`, `THANKYOU`
- Dynamic negative class: `NOTHING`
- Token-based sentence output from confirmed accepted predictions only

Current runtime assets live in [`model/`](./model):

- `voxgest_v3.tflite`
- `class_labels_v3.json`
- `voxgest_lstm_v1.tflite`
- `class_labels_lstm_v1.json`
- `voxgest_tcn_v1.tflite` after retraining with the 10-word hardening profile
- `class_labels_tcn_v1.json`
- `runtime_manifest_v1.json`

## Recommended Android Stack

- Kotlin
- CameraX for camera preview and frame analysis
- MediaPipe Holistic, or an equivalent Pose + Hands landmark pipeline
- TensorFlow Lite Interpreter for both models
- Entirely on-device inference

## Exact Runtime Contract

The Android build should mirror the Python runtime in [`scripts_ml/20_webcam_dual.py`](./scripts_ml/20_webcam_dual.py) and [`scripts_ml/lstm_features.py`](./scripts_ml/lstm_features.py).

Camera and landmarks:

- Use front camera by default
- Match Python behavior with a mirrored frame before feature extraction
- Use one configured dynamic hand: `auto`, `right`, or `left`
- Current Python env var for this is `VOXGEST_DOMINANT_HAND`
- Interpret `VOXGEST_DOMINANT_HAND` as the signer physical hand
- If Android mirrors the camera before landmark extraction, map physical `left`/`right` to the opposite MediaPipe hand label
- Current dynamic pose policy masks non-dominant pose landmarks by default
- Python env var for full-pose fallback is `VOXGEST_SINGLE_HAND_POSE=0`
- Frame size target: `640x480`
- Holistic settings:
  - `min_detection_confidence = 0.45`
  - `min_tracking_confidence = 0.40`
  - `model_complexity = 1`

Static letter model:

- Asset: `model/voxgest_v3.tflite`
- Input shape: `[1, 63]`
- Output shape: `[1, 29]`
- Feature rule:
  - take one hand only
  - use the configured dominant hand when set
  - use right hand if present, else left hand when hand policy is `auto`
  - 21 landmarks x 3 floats
  - subtract wrist landmark from every point
  - divide by the norm of landmark `9` if scale > 0
- Threshold:
  - accept only if confidence `>= 0.55`

Dynamic word model:

- Asset: `model/voxgest_lstm_v1.tflite`
- Preferred asset when available: `model/voxgest_tcn_v1.tflite`
- Input shape: `[1, 30, 162]`
- Output shape: `[1, 11]`
- Feature rule per frame:
  - require pose landmarks
  - pose contribution: `33 x 3 = 99` floats
  - hand contribution: `21 x 3 = 63` floats
  - anchor both pose and hand to the pose nose point
  - use the configured dominant hand when set
  - use right hand if present, else left hand when hand policy is `auto`
  - keep pose landmarks for head, torso, and the selected arm
  - zero non-dominant arm and unrelated lower-body pose landmarks when single-hand pose is enabled
  - if no hand is present, use `63` zeros
  - concatenate into one `162`-float frame vector
  - keep the first `3` floats fixed at `0.0`
- Sequence window:
  - `30` frames
  - keep a rolling buffer
  - if pose is missing for `12` consecutive frames, clear the buffer
- Inference cadence:
  - run LSTM every `3` frames once the `30`-frame buffer is full

Dynamic motion-letter model:

- Purpose: handle motion-based fingerspelling letters `J` and `Z`
- Asset after training: `model/voxgest_motion_letters_tcn_v1.tflite`
- Labels: `model/class_labels_motion_letters_tcn_v1.json`
- Input shape: `[1, 30, 162]`
- Output shape: `[1, 3]`
- Labels:
  - `J`
  - `Z`
  - `NOTHING`
- Runtime rule:
  - keep separate from the dynamic word model
  - run only in alphabet-capable modes, not word-only mode
  - accepted `J` / `Z` enter the token composer as letters
  - `NOTHING` is no-output
  - if the model is missing, fall back to the static alphabet model

Phrase intent model:

- Asset after phrase training: `model/voxgest_phrase_tcn_v1.tflite`
- Default: disabled for Recognition Hardening v1
- Enable only when explicitly requested by a later milestone
- Labels: `model/class_labels_phrase_tcn_v1.json`
- Input shape: `[1, 60, 162]`
- Output shape: depends on trained phrase labels
- Current intended labels:
  - `ASK_NAME`
  - `NOTHING`
  - `PARTIAL_ASK_NAME`
- Output mapping:
  - `ASK_NAME` -> `What is your name?`
  - `NOTHING` -> no displayed output
  - `PARTIAL_ASK_NAME` -> no displayed output
- Feature rule per frame:
  - same `162`-float single-hand feature rule as the word motion model
  - resample completed phrase segments to `60` frames before inference
- Phrase segmentation:
  - do not run phrase output from a rolling 30-frame word window
  - wait for `IDLE -> START_MOTION -> ACTIVE_MOTION -> END_HOLD`
  - classify only after final hold/end of motion
  - default start motion: `0.018`
  - default end motion: `0.010`
  - default start frames: `3`
  - default end hold frames: `10`
  - default min frames: `36`
  - default max frames: `120`
  - default preroll frames: `8`

Training data note:

- Phrase data may come from selected videos and live webcam calibration.
- Selected videos are useful only when they match the exact one-hand phrase
  motion, show the full upper body/hand clearly, and include a final hold.
- Live webcam calibration should still be collected because the Android camera
  distribution may differ from external videos.

Post-processing:

- Modes:
  - `AUTO`
  - `LETTERS`
  - `WORDS`
- Smoothing:
  - rolling prediction window = `5`
  - require majority ratio `>= 0.60`
- Stable frames:
  - letters: `18`
  - words in `AUTO`: `10`
  - words in `WORDS`: `8`
- Cooldown after accept:
  - `1.25s`

Word acceptance thresholds:

- Default `AUTO`/combined word thresholds:
  - confidence `>= 0.75`
  - margin `>= 0.20`
  - motion `>= 0.05`
  - wrist path `>= 0.50`
  - hand presence `>= 0.30`
- Default `WORDS` mode thresholds:
  - confidence `>= 0.65`
  - margin `>= 0.12`
  - motion `>= 0.03`
  - wrist path `>= 0.35`
  - hand presence `>= 0.25`

Word-specific `WORDS` mode rules:

- `YES`: `conf 0.56`, `margin 0.07`, `motion 0.008`, `path 0.05`, `stable 5`
- `NO`: `conf 0.55`, `margin 0.06`, `motion 0.008`, `path 0.04`, `stable 5`
- `WATER`: `conf 0.56`, `margin 0.06`, `motion 0.006`, `path 0.04`, `stable 5`
- `PLEASE`: `conf 0.68`, `margin 0.18`, `motion 0.025`, `path 0.25`
- `HELLO`: `conf 0.70`, `margin 0.18`, `motion 0.030`, `path 0.30`
- `HELP`: `conf 0.60`, `margin 0.10`, `motion 0.025`, `path 0.20`
- `STOP`: `conf 0.62`, `margin 0.10`, `motion 0.025`, `path 0.20`
- `DOCTOR`: `conf 0.70`, `margin 0.15`, `motion 0.020`, `path 0.20`
- `NAME`: `conf 0.55`, `margin 0.05`, `motion 0.020`, `path 0.20`
- `THANKYOU`: `conf 0.52`, `margin 0.03`, `motion 0.010`, `path 0.08`, `stable 6`
- `NOTHING`: `conf 0.55`, `margin 0.05`, `motion 0.000`, `path 0.00`, `hand presence 0.00`, `stable 4`

Word override thresholds in `AUTO` mode:

- confidence `>= 0.88`
- margin `>= 0.28`
- motion `>= 0.07`
- wrist path `>= 0.80`

## App MVP Structure

Recommended Android screens:

- Camera permission / startup screen
- Live recognition screen
- Optional debug screen

Recommended live-screen behavior:

- Mode switch: `AUTO`, `LETTERS`, `WORDS`
- Large current prediction label
- Confidence/debug line for dev builds
- Bottom text strip for recognized output
- Clear button
- Optional toggle to show landmarks/debug metrics

Recommended modules:

- `camera`: CameraX frame stream
- `landmarks`: MediaPipe landmark extraction
- `features`: reproduce `lstm_features.py`
- `inference`: static + LSTM TFLite interpreters
- `motion_letters`: optional J/Z TCN interpreter
- `phrase`: endpoint segmentation + phrase TCN interpreter, optional and off by default
- `postprocess`: smoothing, thresholds, stable-frame logic
- `composer`: token-based sentence builder
- `avatar`: consume confirmed composer tokens for sign playback
- `ui`: recognition state, accepted token, and sentence output

## Token Composer Contract

Only accepted predictions may enter the sentence composer. Raw predictions,
unstable predictions, and rejected predictions must never change the sentence.

Composer rules:

- `A-Z`: append characters to the current spelled word
- `space`: commit the current spelled word boundary
- `del`: delete the latest character or full token
- dynamic word labels: append the full word token
- `nothing` / `NOTHING`: ignored; never displayed, spoken, or animated

The Python reference is [`scripts_ml/token_composer.py`](./scripts_ml/token_composer.py).
The live UI should show:

- current raw/stable prediction
- latest accepted token
- sentence strip built from accepted tokens only
- clear action
- optional speak action that speaks only confirmed sentence text

## Avatar Contract

Avatar playback is downstream of recognition and composition. Use
[`avatar/avatar_manifest.json`](./avatar/avatar_manifest.json) as the current
contract.

Playback rules:

- known word token -> play `avatar/signs/<WORD>.json`
- unknown word token -> fingerspell
- spelled words from letters -> fingerspell
- `NOTHING` -> no animation

The current `avatar/signs/*.json` files are placeholders for animation data;
they define the dictionary boundary, not final animation curves.

## Output Contract

Each accepted prediction should expose:

- `label`
- `source`: `static` or `motion`
- `confidence`
- `margin`
- `motion`
- `wristPath`
- `handPresence`
- `mode`
- `acceptedAt`

## Current Limitations

- Dynamic vocabulary is now `10` communicative words plus `NOTHING`
- Current grouped validation report is around `66.6%`, so this is demo-ready, not final-production-ready
- Some words are still weak in grouped validation and live testing, especially `WATER`, `THANKYOU`, `YES`, and `NO`
- Thresholds are tuned for the Python prototype and should remain data-driven during Android implementation

## Immediate Build Goal

The Android developer should build the first version as:

- offline-only
- TFLite-based
- same 3 modes as Python
- same label order as the JSON files
- same thresholds as `runtime_manifest_v1.json`
- phrase disabled by default
- token composer enabled for accepted letters/words/controls
- avatar playback prepared behind the composer output

Do not ask the Android app to retrain models.
Model training, word expansion, and threshold tuning should stay in this Python workspace.

## Current Hardening Step

Recommended next move is to strengthen the current recognition set:

- audit datasets with `scripts_ml/30_audit_recognition_dataset.py`
- live-test alphabet with `scripts_ml/30_eval_static_alphabet_live.py`
- record static calibration only for weak signs using
  `scripts_ml/31_record_static_calibration.py`
- live-test all 10 words
- collect extra manual sessions for weak words
- keep `NOTHING` active as the negative/no-word class
- record partial/incomplete/transition movements as `NOTHING`
- retrain both LSTM and TCN
- choose the active word model by grouped validation plus live behavior

Do not add vocabulary or phrase-level sentence gestures until hardening passes.
