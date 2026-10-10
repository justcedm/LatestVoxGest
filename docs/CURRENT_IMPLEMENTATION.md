# Current Implementation

## Android Product

VoxGest currently uses a modern five-tab Android interface:

- SIGN
- CONVERSATION
- BOARD
- LISTEN
- GUIDE

The application is designed for local/on-device operation. Python is used for dataset preparation, feature extraction, model training, evaluation, and TFLite export; Android/Kotlin is the runtime deployment target.

## SIGN

The current survey-oriented recognition path is intentionally controlled rather than continuous.

Typical flow:

```text
READY
-> START SIGNING
-> countdown
-> controlled capture window
-> MediaPipe landmark results
-> FullSign225 temporal tensor
-> TFLite classifier
-> acceptance/rejection
-> recognized supported concept
-> text / optional speech
```

Current Core5 labels:

1. HELLO
2. THANK YOU
3. YES
4. NO
5. UNDERSTAND

The current system should not claim recognition outside its implemented and qualified vocabulary.

## LISTEN

LISTEN is the reverse communication path.

Preferred flow:

```text
speech
-> Android speech recognition
-> transcript
-> exact supported concept / alias
-> Avatar action
```

Manual fallback:

```text
Play Signs
-> supported concept selector
-> Avatar action
```

**Play Signs must remain independent of speech-recognition availability.**

## Avatar

The production Avatar is presented in an upper-body signing view so that the head, shoulders, arms, hands, fingers, and torso remain visible.

The current runtime catalog includes three historically known-good Core3 actions and nine candidate actions under continuing FSL/expert validation.

## BOARD

BOARD provides a non-sign visual communication fallback through drawing/writing.

## GUIDE

GUIDE documents supported vocabulary and learning/reference information. It must not mark unverified content as linguistically validated.

## UI

The current product direction uses a light cream/teal visual system with optional theme support, first-use onboarding, local display-name persistence, portrait/landscape adaptation, and accessibility-focused controls.
