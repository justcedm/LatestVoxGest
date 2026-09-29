# VoxGest Core5 Independent Development Dataset Preparation — 2026-09-29

## Current state

The latest offline forensic study is preserved at:

`b07c20e3104a29be07c6680d3c7669a939898c1d`

Key conclusions:

- YES remains a model/domain-sensitivity problem.
- NO is classifier-correct in Native48/SIM10 but is damaged by the current global OOD rejector.
- Cadence alone did not reproduce the Samsung YES failure on FSL-105 source clips.
- No tested global or class-conditioned verifier is qualified.
- No Android implementation is authorized.
- Existing Samsung events have been analyzed repeatedly and should now be treated as sealed diagnostic evaluation evidence rather than tuning/calibration data.

## Next phase

Prepare a new, independently annotated development dataset and the tooling needed to collect it.

Do not collect or train from it until the protocol is finalized.

### Development vocabulary

- HELLO
- THANK YOU
- YES
- NO
- UNDERSTAND

### Initial target per signer/device

- HELLO x10
- THANK YOU x10
- YES x15
- NO x15
- UNDERSTAND x10
- arbitrary non-FSL motion x10
- partial/aborted sign x10
- neutral/no-sign x10

Total initial target: 90 trials per signer/device lane.

### Required metadata per trial

- intended class or negative type
- signer ID alias
- device ID alias
- capture date/time
- camera orientation
- effective landmark result rate
- start timestamp
- human-marked sign end
- return-neutral timestamp
- safe-rearm timestamp
- hand presence
- pose presence
- raw landmarks
- event/tensor hash
- classifier outputs from Baseline / Native48 / SIM10
- notes / invalid-capture reason

### Separation rules

Keep the historical Samsung 69-event set sealed.

Do not use historical Samsung waves/partials to choose thresholds.

New development captures may be used for development only after:
- signer/device identity lane is explicit;
- linguistic intent is independently verified where required;
- train/dev/holdout roles are assigned before model fitting.

### Preferred split strategy

At minimum create:
- DEV_TUNE
- DEV_HOLDOUT
- SEALED_FINAL

Do not let the same exact trial appear in more than one role.

If multiple signers or devices are available, reserve at least one signer/device combination for cross-domain evaluation.

## Candidate training direction

After data collection, compare a new experimental five-class model using controlled cadence/domain augmentation across multiple effective rates rather than SIM10-only assumptions.

Possible augmentation rates:
- 60
- 30
- 20
- 15
- 12
- 10
- 8 fps-equivalent

These are candidate augmentation rates, not production requirements.

The new candidate must be compared against:
- Baseline
- Native48
- SIM10

across all five classes.

Do not optimize only for YES.

## Rejection direction

Rejection should be revisited only after the classifier comparison.

NO must not be penalized by a global rejector that under-scores valid NO source examples.

Future verifier experiments should preserve class balance and calibrate from source/development data only.

## Android policy

No production Android change yet.

Use the separate recognition-lab package for any future device experiments.

Modern VoxGest UI and Avatar remain untouched.

## Immediate offline-only task

Before the project owner returns:

1. audit existing capture/export tooling;
2. design the new development-capture schema;
3. prepare recognition-lab collection support if needed;
4. add deterministic file naming and manifest generation;
5. add validation that prevents accidental mixing with sealed historical Samsung events;
6. build/test offline;
7. stop before any physical signing or Samsung collection.

## Required stop condition

When tooling is ready, report:

```text
DEVSET_PROTOCOL_READY=
COLLECTION_TOOL_READY=
MODERN_UI_UNCHANGED=
RECOGNITION_LAB_ONLY=
TESTS=
OUTPUT_SCHEMA=
HISTORICAL_69_SEALED=
SAMSUNG_NEEDED_NEXT=YES/NO
NEXT_EXACT_ACTION=
```

Do not start model training or Android live testing until new development data exists.
