# Android Handoff Next Steps

Priority: build the Android app shell and inference pipeline now while ML hardening continues in Python.

Do not wait for final ML perfection.

## Build Order

1. CameraX preview.
2. MediaPipe landmarks.
3. Static A-Z TFLite inference.
4. Token composer.
5. Text-to-speech output.
6. Optional motion-letter TFLite inference for J/Z.
7. Dynamic 10-word TFLite inference.
8. Speech-to-text for speaking user reply.
9. Avatar playback.
10. Conversation log/debug screen.

## Recognition Contract

Phrase recognition is disabled by default:

```text
VOXGEST_ENABLE_PHRASE=0
```

Static alphabet model:

```text
input:  [1, 63]
output: [1, 29]
labels: load from class_labels_v3.json
```

Optional motion-letter model for J/Z:

```text
input:  [1, 30, 162]
output: [1, 3]
labels: J, Z, NOTHING
purpose: recognize motion-based alphabet letters separately from word gestures
```

Dynamic word model:

```text
input:  [1, 30, 162]
output: [1, 11]
labels: YES, NO, PLEASE, WATER, HELLO, HELP, STOP, DOCTOR, NAME, THANKYOU, NOTHING
```

Android must load labels from JSON and must not hard-code label order.

## Token Composer

Only confirmed accepted predictions may update text.

Rules:

- `A-Z` appends letters.
- accepted motion-letter `J` and `Z` predictions append letters.
- `space` commits a word boundary.
- `del` deletes the latest character or token.
- accepted word gestures append full word tokens.
- `nothing` and `NOTHING` are ignored and never displayed/spoken.

Reference:

```text
scripts_ml/token_composer.py
```

## Avatar Contract

Reference:

```text
avatar/avatar_manifest.json
avatar/signs/*.json
```

Behavior:

- known word token -> play `avatar/signs/<WORD>.json`
- unknown word token -> fingerspell
- spelled words -> fingerspell
- `NOTHING` -> no animation

## Bidirectional Flow

Sign-language user signs:

```text
camera -> landmarks -> recognition -> accepted tokens -> text -> app speaks
```

Speaking user talks:

```text
speech -> STT -> large text display -> avatar signs known words -> fingerspell unknown words
```

## Current ML Caveat

Use the clean 11-class LSTM as the current fallback until TCN is retrained with `VOXGEST_WORD_PROFILE='demo10'`.

The current TCN labels still include `ASK_NAME`, so it is not the active hardening model until retrained and live-tested.

J and Z should not be merged into the dynamic word model. They use the same sequence feature shape, but they should remain a separate motion-letter classifier so Android can treat their accepted outputs as characters, not full word tokens.
