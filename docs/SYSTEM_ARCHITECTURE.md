# System Architecture

## Sign-to-Hearing Path

```text
Android Camera
    |
    v
MediaPipe Pose + Hands
    |
    v
Landmark preprocessing / normalization
    |
    v
Temporal FullSign225 sequence
    |
    v
TFLite temporal classifier
    |
    v
Acceptance / rejection layer
    |
    +--> reject unsupported / incomplete / low-quality event
    |
    v
Supported concept
    |
    +--> readable text
    +--> optional text-to-speech
```

FullSign225 is composed of:

- 99 pose features
- 63 anatomical-left-hand features
- 63 anatomical-right-hand features

## Hearing-to-Signer Path

```text
Microphone
    |
    v
Android SpeechRecognizer
    |
    v
Transcript
    |
    v
Strict supported-concept resolver
    |
    v
Verified/candidate Avatar action
```

Speech recognition is an input convenience, not a dependency of Avatar playback. Manual supported-word selection must remain available when an offline speech model is unavailable.

## Runtime Principles

- local/on-device processing
- deterministic label ordering
- model/feature contract parity between Python and Android
- no unrestricted phrase generation
- no fuzzy mapping to unsupported concepts
- no Avatar action for rejected recognition output
- explicit failure states instead of fabricated translations

## Recognition Safety

Closed-set classifiers always produce a top score. VoxGest therefore applies additional quality and acceptance checks so an arbitrary movement does not automatically become a word.

Evaluation must consider:

- landmark visibility
- hand/pose presence
- event completeness
- motion quality
- confidence and class separation
- signer/device/domain variation
- false accepts on unsupported motion
