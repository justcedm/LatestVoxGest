# VoxGest Core5 Final Validation and Production Integration Runbook — 2026-10-03

## Deadline objective

Qualify and integrate a controlled isolated-sign recognition path for exactly five supported FSL signs:

- HELLO
- THANK YOU
- YES
- NO
- UNDERSTAND

This is a survey/demo delivery lane, not unrestricted FSL translation.

## Current validated foundation

- Recognition Lab CONTROLLED_WINDOW mode implemented.
- CONTROLLED_WINDOW does not depend on EVENT_TIMEOUT.
- Samsung neutral smoke test collected 49 real MediaPipe frames and automatically ended.
- Feature parity passed for [1,48,225] -> [1,5].
- Baseline, Native48 and SIM10 frozen hashes verified.
- Production SIGN remains unchanged until owner-device qualification passes.
- Modern UI and Avatar packages must remain preserved.

## Validation order

### Stage 1 — Framing
User must be visible with head, shoulders, upper torso and signing hand(s) in frame.

### Stage 2 — 15-trial diagnostic ladder
Run 3 deliberate controlled trials for each sign:
HELLO, THANK YOU, YES, NO, UNDERSTAND.

Replay the exact saved tensor from each trial through:
- Baseline
- Native48
- SIM10

Do not tune thresholds or retrain during this stage.

### Stage 3 — Model decision
Choose a production candidate only from evidence on identical new tensors.
Do not select SIM10 merely because device landmark cadence is approximately 8–10 fps.

### Stage 4 — 50-trial owner-device qualification
If Stage 2 is viable, run 10 deliberate trials per sign.

Engineering target:
- HELLO >= 8/10
- THANK YOU >= 8/10
- YES >= 8/10
- NO >= 8/10
- UNDERSTAND >= 8/10

Also require:
- no crash;
- camera stable;
- MediaPipe stable;
- feature parity;
- deterministic capture completion;
- retry works;
- result displayed before TTS;
- no EVENT_TIMEOUT dependency.

This is an owner-device engineering gate, not population-level accuracy.

### Stage 5 — Production integration
Only after GO:
- port the qualified controlled-capture path to the modern SIGN tab;
- keep the diagnostic Recognition Lab separate;
- preserve CONVERSATION, BOARD, LISTEN and GUIDE;
- do not change Avatar work;
- show user-friendly states only.

Production flow:
READY -> GET READY -> SIGN NOW -> RECOGNIZING -> RECOGNIZED / SIGN AGAIN

Controls:
START SIGNING
SPEAK
RETRY
CLEAR

Never expose raw model hashes, tensors, EVENT_TIMEOUT, or developer labels in production.

## Failure isolation

For every failure classify it as one of:
- FRAMING
- LANDMARK
- FEATURE
- CLASSIFIER
- ACCEPTANCE
- UI/INTEGRATION

For a failed trial, compare all three frozen models on the same tensor before proposing any training or model change.

## Hard stop rules

Do not:
- retrain from owner Samsung captures;
- contaminate sealed evaluation evidence;
- revive Boundary V2;
- build a new global OOD network;
- add more recognition words;
- add A-Z recognition;
- merge ASL data;
- modify Avatar;
- overwrite frozen models;
- claim unrestricted FSL translation.

## Final package evidence

Produce:
- 15-trial diagnostic CSV
- 50-trial qualification CSV
- three-model same-tensor comparison CSV
- selected model/hash
- production integration report
- final survey APK SHA256
- rollback APK/path or reproducible commit
- Git commit and push verification
