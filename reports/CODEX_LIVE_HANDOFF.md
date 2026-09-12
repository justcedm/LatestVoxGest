# VoxGest Live Handoff

TIMESTAMP=2026-09-12T22:00:10+08:00

BRANCH=recognition/mapua14-rescue-v1

COMMIT=PENDING_SUBSTANTIVE_COMMIT

SOURCE_BASE_COMMIT=7929cd28027feb34b83eb3ad0649f39f5fb00abe

HANDOFF_UPDATE_COMMIT=PENDING

BRANCH_HEAD=PENDING

CURRENT_GOAL=Train and package the explicitly authorized PASS-only Mapua-14 rescue recognizer, preserve Standard FSL-105, and stop at the physical Samsung boundary when no device is connected.

WORK_COMPLETED=

- Created the isolated branch and C:\VOXGEST_TRAINING\MAPUA14_RESCUE_V1 workspace.
- Froze a source-video split before training: 311 development, 56 sealed test,
  four class-stratified development folds, zero source-video overlap, and zero
  selected audited exact/perceptual duplicate crossings.
- Re-extracted 367/367 PASS raw MP4s with MediaPipe 0.10.9 into canonical,
  unmirrored FullSign225 complete-trajectory sequences at 32 and 48 frames.
- Proved the Python FullSign225 frame builder against all five existing Android
  golden cases with exact fixed anatomical slots and mirror rejection.
- Trained exactly A=RD-TCN32, B=GRU32, C=RD-TCN48, D=GRU48. All four completed;
  no failed run was hidden. Selection used development folds only.
- Selected C=RD-TCN48, retrained it on all development clips for 87 epochs,
  opened the sealed clip test once, and exported float32 TFLite.
- Added the separate MAPUA14_RESCUE_V1 debug-intent lane, runtime asset/hash/
  shape checks, Android golden parity, 48-frame rolling window, raw top-3 and
  gate-reason logging, duplicate suppression, and exact 14-label presentation
  mappings. Standard and Legacy Demo routing remain separate.
- Ran the full Android unit suite and assembled the debug APK successfully.
- Checked ADB twice. No Samsung was connected, so installation and physical
  live/negative trials were not performed and no live claim is made.

FILES_CHANGED=

- training_configs/mapua14_rescue_v1.json
- scripts_ml/fullsign225_feature_builder.py
- scripts_ml/94_prepare_mapua14_rescue.py
- scripts_ml/95_train_mapua14_rescue.py
- tests/test_fullsign225_feature_builder.py
- reports/mapua14_rescue_v1/*
- android_dry_run/app/src/main/assets/model/mapua14_rescue_v1/*
- android_dry_run/app/src/main/java/com/voxgest/app/MainActivity.kt
- android_dry_run/app/src/main/java/com/voxgest/dryrun/Mapua14RescueRuntime.kt
- android_dry_run/app/src/main/java/com/voxgest/dryrun/Mapua14RescueCameraRecognitionController.kt
- android_dry_run/app/src/main/java/com/voxgest/dryrun/VoxGestCameraRecognitionController.kt
- android_dry_run/app/src/main/java/com/voxgest/dryrun/RecognitionResult.java
- android_dry_run/app/src/main/java/com/voxgest/dryrun/RecognitionOutputCoordinator.kt
- android_dry_run/app/src/main/java/com/voxgest/dryrun/DemoAllowlistPolicy.kt
- android_dry_run/app/src/main/java/com/voxgest/dryrun/ui/VoxGestLocalization.kt
- android_dry_run/app/src/test/java/com/voxgest/dryrun/Mapua14RescueGateTest.kt
- android_dry_run/app/src/test/java/com/voxgest/dryrun/RecognitionOutputCoordinatorTest.kt
- docs/ARCHITECTURE_DECISIONS.md
- reports/CODEX_LIVE_HANDOFF.md

COMMANDS/TESTS=

- `python -m unittest tests.test_fullsign225_feature_builder -v`: 3/3 PASS.
- `94_prepare_mapua14_rescue.py --workers 3`: 367/367 extracted; 0 failures.
- Idempotent cached extraction verification: 367/367; duplicate crossings 0.
- `95_train_mapua14_rescue.py`: four candidates PASS; sealed test opened once;
  TF/TFLite numeric parity PASS.
- Architecture/TFLite smoke conversion: RD-TCN 125326 parameters; GRU 100592.
- `gradlew testDebugUnitTest`: PASS.
- `gradlew testDebugUnitTest assembleDebug`: BUILD SUCCESSFUL.
- Protected deployed model path diff: empty.
- `adb devices -l`: zero connected devices on both checks.

DATASET_STATUS=PASS-only Mapua-14 source count 367. Counts: EIGHT=33, FIVE=32, FOUR=27, HELLO=19, NINE=27, NO=33, ONE=21, SEVEN=25, SIX=25, TEN=22, THANK_YOU=24, THREE=28, TWO=25, YES=26. HELLO is the smallest class. Signer IDs remain unavailable; offline metrics are not signer independent.

TRAINING_STATUS=OFFLINE_PASS. Winner C=RD-TCN48, 125326 parameters. Final bundle mapua14_fullsign225_48f_v1. TFLite parity PASS. Model is experimental and not approved for Standard promotion before physical unseen-Samsung qualification.

SAMSUNG_STATUS=BLOCKED_DEVICE_NOT_CONNECTED. APK build passed, but install/launch, 70 sign attempts, nine negative categories, live latency, wrong-accept, rejection, and false-accept measurements remain NOT_YET_TESTED.

METRICS=

- A RD-TCN32 development macro-F1=0.9783081997367711.
- B GRU32 development macro-F1=0.8832048932626663.
- C RD-TCN48 development macro-F1=0.9824349261849262.
- D GRU48 development macro-F1=0.8980770950571371.
- Exploratory sealed clip accuracy=0.9821428571428571.
- Exploratory sealed clip macro-F1=0.979591836734694.
- Only sealed confusion: YES -> TEN, count 1.
- TF/TFLite top-1 agreement=1.0; maximum probability difference=4.76837158203125e-07.
- Model SHA-256=f850c5d414c5c253ef9131bae5a85bb3ed5ad5412abdf9936df510c6ec043dcc.
- Label SHA-256=af398236fd62da6c5bafbe0b60d21bc8a155c48aeb45987090c1b14a20cb9ef0.

FAILURES=

- Samsung absent from ADB; physical generalization and negative rejection are
  unmeasured. This is a required stop, not a model pass/fail conclusion.
- Mapua signer IDs are unavailable. High offline clip metrics may reflect
  signer/background/session regularities and must not be called signer-independent.
- The first extraction launch used the transaction root rather than its
  `extracted` child and failed closed with FileNotFoundError before producing
  tensors. Path resolution was corrected; the frozen split was unchanged; the
  successful pass completed 367/367.
- The first Gradle launch lacked JAVA_HOME. Rerun with the existing Android
  Studio JBR passed.

CURRENT_HYPOTHESIS=Complete-trajectory FullSign225 plus RD-TCN48 materially fits the Mapua clip distribution, but only an unseen Samsung signer can establish whether this is a useful rescue foundation. Gate performance must be reported separately from raw top-1.

NEXT_ACTION=Connect and authorize the Samsung, install the current debug APK, launch MAPUA14_RESCUE_V1 using both required debug extras, verify on-device golden parity, then interactively capture five attempts for each of 14 signs and the nine prescribed negative categories. Do not promote to Standard before those results.

DO_NOT_MODIFY=

- Do not access or write the retired D: workspace.
- Do not force-push or rewrite GitHub history.
- Do not stage raw videos, external features, checkpoints, caches, environments,
  APK/AAB/build output, device captures, secrets, or local configuration.
- Do not replace the deployed FSL-105 model, its 105 labels, or Standard profile.
- Do not change UI layout/styling, Avatar, or Listen.
- Do not mirror canonical ML input or swap anatomical left/right slots.
- Preserve `(System.nanoTime() / 1_000_000L)`.
- Do not claim signer-independent or live accuracy from this experiment.

## Future update contract

Every meaningful task must refresh TIMESTAMP, BRANCH, COMMIT,
SOURCE_BASE_COMMIT, HANDOFF_UPDATE_COMMIT, BRANCH_HEAD, CURRENT_GOAL,
WORK_COMPLETED, FILES_CHANGED, COMMANDS/TESTS, DATASET_STATUS, TRAINING_STATUS,
SAMSUNG_STATUS, METRICS, FAILURES, CURRENT_HYPOTHESIS, NEXT_ACTION, and
DO_NOT_MODIFY.
