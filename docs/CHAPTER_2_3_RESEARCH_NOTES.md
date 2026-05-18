# VoxGest Chapter 2-3 Research Notes

## Development Process

VoxGest follows Developmental Research with Agile Rapid Application Development.

Developmental Research fits because the project creates and improves a working
technology artifact: an offline bidirectional accessibility app. Agile RAD fits
because recognition, Android UI, testing, and documentation need short cycles
of building, live testing, and revision.

## Framework

Offline Android Edge-Computing Bidirectional Accessibility Framework.

The phone performs recognition locally. The app does not require cloud inference
for the recognition demo, which supports privacy, lower latency, and use in
network-limited settings.

## System Flow

```text
camera input -> landmarks -> static/dynamic model -> gates -> token composer -> text/speech/avatar
```

Details:

- Camera input captures the signer.
- MediaPipe landmarks convert frames into hand and pose points.
- Static model handles alphabet/control signs.
- Dynamic LSTM/TCN model handles demo10 word signs plus `NOTHING`.
- Gates check confidence, margin, motion, wrist path, and hand presence.
- Token composer updates text only from accepted predictions.
- Output is shown as text, spoken with TTS, or passed to the avatar.

## Speech Flow

```text
speech input -> text -> avatar/fingerspelling response
```

The hearing person's speech is converted into text. Known words can trigger
lightweight avatar animations; unknown words can be fingerspelled.

## Evaluation Plan

Use ISO/IEC 25010 quality characteristics:

- Functional Suitability: Does the app perform the required recognition and
  communication tasks?
- Performance Efficiency: Does it run fast enough on-device for live use?
- Reliability: Does it avoid unsafe false outputs and handle missing landmarks?
- Usability: Can a hearing person and Deaf/signing user use it with minimal
  learning?

## Why Model Development Changed Over Time

The project started with image-based recognition ideas, then moved toward
landmarks because live camera conditions exposed issues with background,
lighting, and user position. The current model contract is more controllable:
static letters use hand landmarks, while dynamic words use 30-frame landmark
sequences.

## Why Live Testing Is Part Of The Methodology

Validation accuracy alone does not prove demo readiness. Live testing reveals
camera-specific failures, hand-selection mistakes, motion confusion, and false
accepts. The live logger records these failures so the next data collection is
targeted instead of random.

## Why App And ML Pipeline Are Developed In Parallel

The Android app determines real user constraints: readable text, offline
behavior, camera placement, one-thumb controls, and avatar response. The ML
pipeline determines what the app can safely output. Developing both in parallel
keeps the model contract aligned with the actual interface.

## Why Demo Words Must Stabilize Before Expansion

Adding more words increases confusion. The current demo10 set must be stable
before moving to 25 or 50 words, otherwise new vocabulary will hide basic
recognition problems and weaken the defense/demo.

## Current Controlled Expansion Gate

The latest right-hand TCN live test shows demo10 is mostly strong, but `NAME`
is still being confused as accepted `STOP`. Because STOP itself is now stable,
the methodology requires targeted contrast repair instead of immediate
vocabulary expansion.

The next data cycle is:

```text
record NAME/STOP/NOTHING contrast samples -> retrain LSTM/TCN -> live test NAME and STOP -> decide whether sprint20 can begin
```

Sprint20 is treated as an expansion profile, not the guaranteed production
profile. Android remains defaulted to demo10 until the expanded profile passes
live testing and preserves `NOTHING` as a no-output class.
