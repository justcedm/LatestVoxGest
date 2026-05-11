# VoxGest Sentence Intent Next-Level Documentation - 2026-05-08

## Purpose

This document summarizes the current VoxGest system and the structural upgrade
for one-hand sentence-intent recognition. It is written for project paper
updates and future engineering handoff.

The first phrase-intent target is:

```text
ASK_NAME -> What is your name?
```

This is an accessibility-oriented one-hand phrase shortcut. It is not presented
as the standard ASL sentence form.

## Current System

VoxGest currently has three recognition surfaces:

```text
Static letters      -> A-Z, del, space, nothing
Short word motion   -> demo10 wor
ds + NOTHING
Phrase intents      -> endpoint-segmented sentence shortcuts
```

Static model:

```text
model/voxgest_v3.h5
model/voxgest_v3.tflite
model/class_labels_v3.json
input:  [1, 63]
output: [1, 29]
```

Word motion models:

```text
model/voxgest_lstm_v1.h5
model/voxgest_lstm_v1.tflite
model/class_labels_lstm_v1.json

model/voxgest_tcn_v1.h5
model/voxgest_tcn_v1.tflite
model/class_labels_tcn_v1.json
input:  [1, 30, 162]
```

Current short word vocabulary:

```text
YES, NO, PLEASE, WATER, HELLO, HELP, STOP, DOCTOR, NAME, THANKYOU, NOTHING
```

Phrase model target files:

```text
model/voxgest_phrase_tcn_v1.h5
model/voxgest_phrase_tcn_v1.tflite
model/class_labels_phrase_tcn_v1.json
model/phrase_tcn_training_report.json
input:  [1, 60, 162]
```

## Key Design Correction

The first `ASK_NAME` test showed premature recognition: the system recognized
`ASK_NAME` after seeing only the first gesture of the whole phrase.

Cause:

```text
ASK_NAME was being treated like a short word in a sliding 30-frame window.
```

Structural fix:

```text
Do not classify phrase intents with the short word window.
Segment the complete phrase motion first, then classify once.
```

The corrected live flow is:

```text
IDLE -> START_MOTION -> ACTIVE_MOTION -> END_HOLD -> CLASSIFY PHRASE
```

This means `ASK_NAME` should only output after the user completes the whole
gesture and holds briefly at the end.

## Current Architecture

```text
Camera frame
  -> MediaPipe holistic landmarks
  -> single-hand feature policy
  -> static letter model for letters
  -> 30-frame word model for short words
  -> phrase segmenter for full sentence motions
  -> 60-frame phrase TCN after endpoint
  -> output mapper
```

The word model stays fast and responsive. The phrase model is deliberate and
endpoint-based.

## Files Added Or Updated

Added:

```text
scripts_ml/phrase_config.py
scripts_ml/gesture_segmenter.py
scripts_ml/26_record_phrase_intents.py
scripts_ml/27_train_phrase_tcn.py
```

Updated:

```text
scripts_ml/lstm_features.py
scripts_ml/20_webcam_dual.py
scripts_ml/export_tflite_fixed.py
VOXGEST_SENTENCE_INTENT_NEXT_LEVEL_2026-05-08.md
```

## Phrase Labels

Phrase intent:

```text
ASK_NAME -> What is your name?
```

Phrase negative/no-output labels:

```text
NOTHING
PARTIAL_ASK_NAME
```

`PARTIAL_ASK_NAME` is important. It teaches the phrase model that the first half
or aborted version of the gesture is not enough to output the sentence.

## Recording Rules

For `ASK_NAME`, record:

```text
neutral -> full one-hand phrase gesture -> final hold
```

Important terminology:

```text
60 x 162 = one saved phrase sample shape
60 samples = about 60 repeated captured phrase attempts
group = one separate recording session/run of the recorder
```

The phrase trainer requires multiple groups so it can validate on a different
recording session from the one it trains on. One long session is weaker than
several shorter sessions because it does not prove the model can generalize
across timing, position, lighting, and fatigue changes.

For `NOTHING`, record:

```text
idle, neutral movement, transitions, hand entering/leaving frame
```

For `PARTIAL_ASK_NAME`, record:

```text
first half of ASK_NAME, aborted ASK_NAME, start gesture then stop
```

Recommended data targets:

```text
ASK_NAME:         240-360 samples
NOTHING:          480+ samples
PARTIAL_ASK_NAME: 120-240 samples
```

Fatigue-friendly collection plan after one 60-sample session already exists:

```powershell
$env:VOXGEST_PHRASE_SEQUENCES_PER_LABEL='30'
.\voxgest_env\Scripts\python.exe scripts_ml\26_record_phrase_intents.py ASK_NAME
.\voxgest_env\Scripts\python.exe scripts_ml\26_record_phrase_intents.py NOTHING
.\voxgest_env\Scripts\python.exe scripts_ml\26_record_phrase_intents.py ASK_NAME
.\voxgest_env\Scripts\python.exe scripts_ml\26_record_phrase_intents.py NOTHING
```

That creates three groups per class:

```text
session 1: 60 samples
session 2: 30 samples
session 3: 30 samples
total:     120 samples
```

This satisfies the default trainer minimum for `ASK_NAME` and `NOTHING`.

## Using Selected Video Data

Selected videos can reduce webcam recording fatigue, but they should not fully
replace live calibration. External videos help when they show the same phrase
intent, similar one-hand motion, clear landmarks, and a complete endpoint. They
hurt quality when they show a different signer style, a two-hand version, a
different phrase, heavy camera cuts, cropped hands, or no final hold.

Recommended hybrid data strategy:

```text
selected videos -> broad motion prefill
webcam samples   -> final camera/user calibration
```

Good selected videos should have:

- one visible dominant hand
- full phrase motion from start to final hold
- stable camera framing
- hand and upper body visible
- no heavy edits or jump cuts
- the same intended one-hand accessibility gesture

Poor selected videos can make live quality worse because the phrase TCN will
learn the video dataset distribution instead of the actual user/camera setup.

Folder layout:

```text
phrase_videos/
  ASK_NAME/
    sample_001.mp4
    sample_002.mp4
  NOTHING/
    idle_001.mp4
  PARTIAL_ASK_NAME/
    partial_001.mp4
```

Extraction command:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\28_extract_phrase_videos.py ASK_NAME NOTHING PARTIAL_ASK_NAME
```

After video extraction, still record a smaller webcam calibration pass:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\26_record_phrase_intents.py ASK_NAME
.\voxgest_env\Scripts\python.exe scripts_ml\26_record_phrase_intents.py NOTHING
.\voxgest_env\Scripts\python.exe scripts_ml\26_record_phrase_intents.py PARTIAL_ASK_NAME
```

If fatigue is the main concern, use this lighter target after good video
extraction:

```text
ASK_NAME webcam:         60-120 samples
NOTHING webcam:          120-240 samples
PARTIAL_ASK_NAME webcam: 60-120 samples
```

## Command Checklist

Set the recording policy:

```powershell
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_SINGLE_HAND_POSE='1'
$env:VOXGEST_PHRASE_SEQUENCES_PER_LABEL='60'
```

Use `left` instead of `right` if the target signer uses the left hand.

Record phrase data:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\28_extract_phrase_videos.py ASK_NAME NOTHING PARTIAL_ASK_NAME
.\voxgest_env\Scripts\python.exe scripts_ml\26_record_phrase_intents.py ASK_NAME
.\voxgest_env\Scripts\python.exe scripts_ml\26_record_phrase_intents.py NOTHING
.\voxgest_env\Scripts\python.exe scripts_ml\26_record_phrase_intents.py PARTIAL_ASK_NAME
```

Train the phrase TCN:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\27_train_phrase_tcn.py
```

Run live testing:

```powershell
$env:VOXGEST_DYNAMIC_MODEL='tcn'
$env:VOXGEST_MODE='WORDS'
$env:VOXGEST_DOMINANT_HAND='right'
$env:VOXGEST_SINGLE_HAND_POSE='1'
.\voxgest_env\Scripts\python.exe scripts_ml\20_webcam_dual.py
```

Expected behavior:

```text
Short words -> detected by 30-frame word TCN
ASK_NAME    -> detected only after complete phrase motion and final hold
NOTHING / PARTIAL_ASK_NAME -> no displayed sentence
```

## Live Recognizer Behavior

The live recognizer now loads the phrase model only if these files exist:

```text
model/voxgest_phrase_tcn_v1.h5
model/class_labels_phrase_tcn_v1.json
```

If the phrase model is missing, the app still runs the existing static and word
recognition system.

The live recognizer ignores phrase labels from the short word model. This
prevents a 30-frame model from prematurely outputting `ASK_NAME`.

## Android Runtime Contract

Android should eventually load:

```text
Static alphabet model: [1, 63]
Word motion model:     [1, 30, 162]
Phrase motion model:   [1, 60, 162]
```

Android must reproduce:

- mirrored front-camera behavior
- physical dominant-hand mapping
- single-hand pose masking
- selected hand feature extraction
- endpoint phrase segmentation
- phrase label-to-text mapping

Phrase output mapping:

```text
ASK_NAME -> What is your name?
```

## Paper-Ready Methodology Wording

```text
The dynamic recognition module uses MediaPipe holistic landmarks and a
dominant-hand feature policy. Short word signs are recognized through a
30-frame Temporal Convolutional Network. Phrase-level sentence intents use a
separate endpoint-based recognition path. The system first detects the start of
a phrase motion, tracks the active motion, waits for a final hold, resamples
the completed segment to 60 frames, and then classifies the segment using a
phrase TCN. This prevents phrase outputs from being committed before the signer
finishes the sentence-level gesture.
```

## Paper-Ready Accessibility Wording

```text
Some standard ASL expressions require both hands, which may limit usability for
signers with one functional hand. VoxGest addresses this through
accessibility-oriented phrase shortcuts. These custom one-hand gestures are not
claimed to replace standard ASL; instead, they provide an alternative input
method for users who cannot perform selected two-hand expressions. The system
maps the recognized intent label to readable text, such as mapping ASK_NAME to
"What is your name?".
```

## Success Criteria

The phrase milestone is successful when:

- `ASK_NAME` does not output during the first gesture only
- `ASK_NAME` outputs after the full phrase gesture and final hold
- `PARTIAL_ASK_NAME` produces no displayed sentence
- `NOTHING` suppresses idle and transition false positives
- existing demo10 words still work through the word TCN
- Android contract clearly separates word and phrase models

## Current Limitation

The phrase model is not trained until phrase samples are recorded. The current
code path is ready, but the model files will exist only after:

```powershell
.\voxgest_env\Scripts\python.exe scripts_ml\27_train_phrase_tcn.py
```
