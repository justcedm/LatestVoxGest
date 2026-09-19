# VoxGest Live Handoff

TIMESTAMP=2026-09-20T05:10:14+08:00

BRANCH=recognition/scenario15-counter-v1

COMMIT=d4dc2e1ee5cb6a11768e3164a14d96d04ad4c27e (working checkpoint not committed yet)

SOURCE_BASE_COMMIT=d4dc2e1ee5cb6a11768e3164a14d96d04ad4c27e

HANDOFF_UPDATE_COMMIT=PENDING

BRANCH_HEAD=d4dc2e1ee5cb6a11768e3164a14d96d04ad4c27e plus reviewed working checkpoint

CURRENT_GOAL=Complete the one-day frozen 15-concept counter scenario using existing phrase and Standard specialist assets, exactly one Mapua9 RD-TCN48, complete-sign capture, deterministic routing, early Samsung testing, and evidence-only repairs.

WORK_COMPLETED=

- Fetched/pruned, checked out the requested branch, and proved exact local/remote parity at the required commit.
- Recovered initial Samsung ADB unauthorized state with a safe server restart and user RSA authorization; device now reports device.
- Ran the full Android unit suite and assembled the debug APK: PASS, 44 tasks.
- Installed and launched the untouched baseline. Standard FullSign225 and TFLite golden parity passed on Samsung; input/output/label and camera mirror contracts match.
- Added an isolated debug-only 18-trial quick gate for WHAT/YOUR/NAME/MY/MILK/RICE without changing production routing.
- Rebuilt and reinstalled successfully after the harness addition.
- Froze and fully validated the exact 219-clip Mapua9 subset from the preserved canonical cache: all hashes, finite float32 [48,225] tensors, metadata, folds, and group isolation pass.
- Added the contract for exactly one authorized training candidate, RD-TCN48. Training has not started before the required early physical gate.

FILES_CHANGED=

- android_dry_run/app/src/debug/AndroidManifest.xml
- android_dry_run/app/src/debug/java/com/voxgest/dryrun/scenario15/Scenario15ExistingAssetsQuickGateActivity.kt
- training_configs/scenario15_mapua9_v1.json
- scripts_ml/99_prepare_scenario15_mapua9.py
- scripts_ml/100_train_scenario15_mapua9.py
- reports/scenario15_counter_v1/MAPUA9_SPLIT_MANIFEST.csv
- reports/scenario15_counter_v1/MAPUA9_DATASET_SUMMARY.json
- reports/scenario15_counter_v1/PREFLIGHT_AND_QUICK_GATE_20260920.md
- reports/CODEX_LIVE_HANDOFF.md

COMMANDS/TESTS=

- git fetch/branch/ancestor/local-remote checks: PASS.
- adb devices -l: R5GYC0M1M4P device, model SM-A566B.
- Full Android tests and build: PASS, 44 tasks.
- Baseline APK install/launch: PASS.
- Standard Android FullSign225 golden parity: PASS.
- Standard Android TFLite golden parity: PASS.
- Harness rebuild/test/install/launch: PASS.
- Mapua9 preparation: 219/219 PASS; zero partition/fold group crossings.
- Mapua9 scripts/config static parse and git diff check: PASS.

DATASET_STATUS=MAPUA9_FROZEN_PASS. Counts: HELLO=19, YES=26, NO=33, THANK_YOU=24, PLEASE=17, HOW_MUCH=12, CASH=26, CARD=34, RECEIPT=28. Development=185; sealed test=34. Signer IDs remain unavailable, so future offline metrics are not signer independent.

TRAINING_STATUS=READY_NOT_STARTED. Exactly one RD-TCN48 candidate is authorized. Early physical existing-asset gate remains first.

SAMSUNG_STATUS=CONNECTED_AUTHORIZED_WAITING_FOR_UNLOCK. Standard startup parity is proven. Zero quick-gate sign attempts have been recorded; no live recognition claim is made.

METRICS=

- Standard input/output: [1,20,225] -> [1,105]; labels=105.
- Standard preview mirror=true; analysis/model mirror=false.
- Mapua9 selected clips=219; development=185; sealed=34.
- Mapua9 development folds=45/47/46/47.
- Mapua9 tensor integrity=219/219 PASS.
- Mapua9 group partition/fold crossings=0.
- Mapua9 frozen split SHA-256=883974a7086eb43a9bb1da89e845dd4b5f6a4ab1c5d68f6cdc2b7d3d2a7ae55b.

FAILURES=

- The Samsung is dozing behind the Android lock shade. ADB can wake it but cannot and must not bypass the user credential. The activity is installed/focused but physically hidden.
- No WHAT/YOUR/NAME/MY/MILK/RICE attempt is complete yet.
- Mapua9 training, integration, quick 45, final 75, and negative 30 are not yet complete.
- Signer IDs are unavailable; offline clip metrics cannot establish unseen-signer performance.

CURRENT_HYPOTHESIS=The preserved phrase model should be tested unchanged for the four name-flow tokens, and Standard should be tested unchanged as a restricted MILK/RICE specialist. The canonical Mapua cache is sufficient for the one authorized RD-TCN48 without re-extraction. Live Samsung evidence, especially raw top-1, will decide any repair.

NEXT_ACTION=Manually unlock the Samsung and keep the Scenario 15 quick gate foregrounded. Complete the queued 18 attempts with one displayed sign per ARM event; then pull/analyze the JSON and begin the single Mapua9 RD-TCN48 run.

DO_NOT_MODIFY=

- Do not access or write the retired D: workspace.
- Do not modify Avatar, Listen, unrelated UI, Standard FSL-105 rollback, or Mapua14 rollback.
- Do not change production routing before the isolated scenario lane is evidence-backed.
- Do not train more than the one authorized Mapua9 RD-TCN48 candidate.
- Do not stage APKs, logs, recordings, datasets, caches, environments, secrets, or private paths.
- Do not hide raw top-1 failures behind confidence/margin gates.
- Do not call offline clip metrics signer independent.

## Archived prior handoff (Mapua14 checkpoint)

TIMESTAMP=2026-09-12T22:00:10+08:00

BRANCH=recognition/mapua14-rescue-v1

COMMIT=8f1794d0

SOURCE_BASE_COMMIT=7929cd28027feb34b83eb3ad0649f39f5fb00abe

HANDOFF_UPDATE_COMMIT=8f1794d0

BRANCH_HEAD=8f1794d0 (audited offline package; metadata-only handoff commit follows)

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
