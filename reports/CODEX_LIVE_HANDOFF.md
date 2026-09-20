# VoxGest Live Handoff

TIMESTAMP=2026-09-20T23:24:26+08:00

BRANCH=recognition/fsl-dual-dataset-reset-v1

COMMIT=39c9f21dbc9702942e90b3e6171bb4d4af43ff07 plus Samsung startup checkpoint

SOURCE_BASE_COMMIT=6afa7322679317a0fac8b1bb0e66fe810ff99b10

HANDOFF_UPDATE_COMMIT=PENDING_THIS_COMMIT

BRANCH_HEAD=39c9f21dbc9702942e90b3e6171bb4d4af43ff07 plus Samsung startup checkpoint

CURRENT_GOAL=Complete Samsung anatomical handedness/framing verification, then execute the frozen 45-positive and 30-negative initial battery without threshold changes.

WORK_COMPLETED=

- Verified the requested authority is an ancestor and maintained local/remote parity at every pushed checkpoint.
- Verified the published Mapua archive (1,107 MP4s, 26 labels) and quarantined all non-authoritative sources.
- Documented the exact official FSL-105 acquisition blocker without substituting an unofficial mirror.
- Audited the practical pool, froze a source-clip/group-safe 334/60 development/sealed split, and generated representative landmark/motion evidence.
- Ran one RD-TCN48 architecture over four frozen development folds and froze the evidence-supported 15-class vocabulary.
- Retrained once on all development clips, evaluated the sealed set once, exported float32 TFLite, and proved TF/TFLite parity.
- Replayed all 60 sealed raw videos through MediaPipe, motion extraction, FullSign225, resample48, and the Android-bound TFLite model.
- Integrated the exact bundle as debug-intent-only FSL_PRACTICAL15_V1 with complete-event capture, mandatory release/re-arm, diagnostics, and no production-default change.
- Added shared Python/JVM temporal parity and complete-event/dropout/handedness/timeout/incomplete-event tests.
- Audited fixed rejection behavior on development-only synthetic corruptions and added a conservative no-motion structural guard.
- Ran 101 JVM tests with zero failures and assembled the debug APK successfully.
- Proved the five embedded practical-profile APK assets are byte-identical to
  the source bundle and added per-event MediaPipe/result latency logging.
- Wrote the paper-alignment delta and exact Samsung qualification protocol.
- Verified Samsung SM-A566B serial R5GYC0M1M4P is authorized, installed the
  byte-audited debug APK, and proved the installed APK hash matches the build.
- Launched only FSL_PRACTICAL15_V1 and captured on-device feature parity,
  TFLite golden parity, model/label shape parity, frozen capture/gate settings,
  and front-camera mirror contract evidence.
- Added event-duration and explicit frozen capture-configuration telemetry
  before any semantic physical trial.

FILES_CHANGED=

- training_configs/fsl_practical15_mapua_v1.json
- scripts_ml/96_prepare_fsl_practical15.py through 101_verify_fsl_temporal_fixture.py
- reports/fsl_dual_dataset_reset_v1/*
- android_dry_run/app/src/main/assets/model/fsl_practical15_fullsign225_48f_v1/*
- android_dry_run/app/src/main/java/com/voxgest/dryrun/FslPractical15Runtime.kt
- android_dry_run/app/src/main/java/com/voxgest/dryrun/FslPractical15CameraRecognitionController.kt
- minimal existing debug routing/result-policy files
- Android practical-profile JVM tests/resources
- reports/CODEX_LIVE_HANDOFF.md

COMMANDS/TESTS=

- Provenance, archive SHA/count, feature SHA/shape/finite, split-group isolation: PASS.
- Four-fold RD-TCN48 OOF selection: PASS.
- Final float32 TFLite parity: PASS, top-1 agreement 1.0.
- Sealed raw-video replay: 60/60 processed, zero extraction failures, exact cache parity.
- Python temporal fixture: PASS, maximum position difference 0.0.
- Android JVM: 26 suites, 101 tests, 0 failures/errors/skips.
- Android testDebugUnitTest + assembleDebug: BUILD SUCCESSFUL.
- APK practical-profile asset hash/byte verification: PASS.
- Protected Standard/Mapua14/demo model hashes: unchanged.
- Samsung install: PASS; installed/base APK SHA-256 equals the audited build.
- On-device feature parity: PASS; no hand-slot swapping; model input unmirrored.
- On-device TFLite golden parity: PASS; input [1,48,225], output [1,15].
- Camera binding: PASS; FRONT, preview mirrored, analysis unmirrored.

DATASET_STATUS=MAPUA_PUBLISHED_FSL_ONLY_INTERIM; 394 PASS clips; 334 development; 60 sealed; FSL105_PENDING_OFFICIAL_RAW; ASL_CONTAMINATION_QUARANTINED.

TRAINING_STATUS=OFFLINE_PASS. RD-TCN48, 125391 parameters, 15 classes, complete-event FullSign225 resample48. Signer-independent claim prohibited.

SAMSUNG_STATUS=STARTUP_PARITY_PASS; HUMAN_IN_FRAME_CHECK_PENDING. Device is authorized and the experimental profile is active. The empty-room preview is correctly oriented by contract, but anatomical left/right overlays and framing require the operator in frame before semantic signs. Positive/negative attempts remain 0/45 and 0/30.

METRICS=

- Development OOF accuracy=0.9491017964; macro-F1=0.9453016957; weakest F1=0.7878787879 HOW_MANY.
- Exploratory sealed accuracy=0.95; macro-F1=0.9557183557; weakest F1=0.8571428571 CASH.
- Sealed confusions: CARD->COIN x1; CASH->PROBLEM x1; NO->YES x1.
- Raw-video replay accuracy=0.95; cache max absolute difference=0.0; exact envelope matches=60/60.
- TFLite max probability difference=4.172325134277344e-07.
- Desktop replay TFLite median=0.9066 ms; p95=1.2948 ms.
- Provisional gate confidence=0.95, margin=0.05, motion mean-L2 floor=0.02.
- Installed APK SHA-256=260f79ae1f968c39dde061ba511035c443e5832a1a93525a31505b97cf8a39a6.
- Model SHA-256=dfe78b557052032b2b288685b1de97108d0ddbb3516e3bdf758c0ef1c26219e6; labels SHA-256=4d3083b853d126c243b7f9a5db94be796a99a7bfe1379ef400d1d3f82a153cb6.

FAILURES=

- Official FSL-105 v2 raw is absent; official automated acquisition is blocked by HTTP 403/Cloudflare.
- Mapua signer IDs are unavailable, so clip metrics cannot establish signer independence.
- The sealed raw replay repeats the same sealed source clips through the perception path; it is not a second independent dataset.
- Score-only rejection is weak on synthetic corruptions. Live negative false-accept performance is unknown.
- Samsung camera framing, landmark quality, per-class generalization, and latency are not yet measured.
- ADB entered `offline` twice during startup evidence collection; both incidents
  recovered through a normal server restart. USB transport stability remains
  under observation and no event evidence was lost.

CURRENT_HYPOTHESIS=The installed practical15 profile has exact package, feature, model, temporal, and camera-contract parity. Human-in-frame handedness/tracking and clean-event classifier behavior are now the decisive gates; USB stability must also remain adequate for evidence collection.

NEXT_ACTION=Place the Samsung upright with the signer centered, run the neutral anatomical-left/right camera sanity check, then execute the exact 45-positive order followed by 30 negatives without changing thresholds.

DO_NOT_MODIFY=

- Do not access the retired workspace drive.
- Preserve Standard FSL-105, Mapua14 rescue, demo10/manual5, and Avatar.
- Do not use quarantined ASL/WLASL/phrase/team/internet data in the FSL claim.
- Do not change confidence, margin, presence, duration, or motion gates during the first physical battery.
- Do not commit raw videos, features, checkpoints, APKs, logs, captures, credentials, caches, or private paths.
- Do not claim live readiness, signer independence, or dual-source training without evidence.

## Earlier same-day checkpoint

TIMESTAMP=2026-09-20T13:42:00+08:00

BRANCH=recognition/fsl-dual-dataset-reset-v1

COMMIT=6afa7322679317a0fac8b1bb0e66fe810ff99b10 (provenance checkpoint pending)

SOURCE_BASE_COMMIT=6afa7322679317a0fac8b1bb0e66fe810ff99b10

HANDOFF_UPDATE_COMMIT=PENDING

BRANCH_HEAD=6afa7322679317a0fac8b1bb0e66fe810ff99b10 plus reviewed provenance work

CURRENT_GOAL=Deliver the strongest defensible published-FSL practical profile offline by 18:00, including canonical complete-event FullSign225, leakage-safe RD-TCN48 evidence, TFLite parity, isolated Android integration, raw-video replay, rejection preparation, and a ready Samsung protocol.

WORK_COMPLETED=

- Checked out the requested branch cleanly and proved the required commit is its exact remote HEAD.
- Read the complete offline execution authority and all required provenance, dataset, architecture, and current-runtime references.
- Verified the original Mapua archive: 590,909,988 bytes, SHA-256 51333b36e8cca082bc5ecb1b53b0242e1c91d9d00393c2a75e18dce27ceb48de.
- Verified 1,107 Mapua raw MP4s across 26 labels and the existing 670-row PASS-only canonical feature cache.
- Searched named safe-C roots for FSL-105 raw; none was found.
- Attempted only the official Mendeley v2 source. Public API/page automation is blocked by HTTP 403 and a Cloudflare JavaScript challenge; no unofficial substitute was used.
- Activated the authorized Mapua-only contingency without waiting for Samsung or FSL-105.

FILES_CHANGED=

- reports/fsl_dual_dataset_reset_v1/SOURCE_PROVENANCE_INVENTORY.md
- reports/fsl_dual_dataset_reset_v1/FSL105_ACQUISITION_BLOCKER.md
- reports/CODEX_LIVE_HANDOFF.md

COMMANDS/TESTS=

- git fetch/checkout/ancestor/local-remote parity: PASS.
- safe-C raw/archive search: PASS; FSL-105 raw absent.
- Mapua archive SHA-256 and raw inventory: PASS.
- official Mendeley metadata/API attempts: HTTP 403.
- official page command-line attempt: Cloudflare managed challenge.
- bounded official in-app page attempts: no downloadable artifact.

DATASET_STATUS=MAPUA_RAW_VERIFIED; FSL105_PENDING_RAW; ASL/phrase/team/internet sources QUARANTINED.

TRAINING_STATUS=NOT_STARTED. Candidate audit and frozen split precede training.

SAMSUNG_STATUS=INTENTIONALLY_UNAVAILABLE_UNTIL_APPROX_18:00; offline work continues.

METRICS=

- Mapua raw clips=1,107; labels=26.
- Mapua raw audit statuses: PASS=670, REVIEW=408, REJECT_TECHNICAL=29.
- Mapua pose detection rate=1.0; any-hand rate=0.779283; internal dropout=0.022066.
- Complete-motion temporal retention median: 20f=0.931925, 32f=0.969269, 48f=0.986112.

FAILURES=

- Official FSL-105 raw is absent locally.
- Official automated acquisition is blocked by HTTP 403/Cloudflare.
- Mapua signer IDs are unavailable; no signer-independent claim is permitted.

CURRENT_HYPOTHESIS=The published Mapua pool can support a strong 15-concept interim retail/social recognizer by adding COIN and DISCOUNT to the 13 mandated fallback concepts, but the list remains provisional until landmark and leakage-safe separability evidence is complete.

NEXT_ACTION=Generate the 15-class landmark/trajectory audit and representative evidence, freeze a leakage-safe source-clip split, run development-fold RD-TCN48 evidence, then freeze the strongest defensible vocabulary.

DO_NOT_MODIFY=

- Never access the retired D: workspace.
- Preserve Standard FSL-105, Mapua14 rescue, demo10, and Avatar.
- Do not redesign UI or touch Avatar.
- Do not use ASL, WLASL, phrase experiments, random internet signs, or unvalidated team signs in the FSL claim.
- Do not commit raw video, feature tensors, checkpoints, APKs, captures, credentials, or private paths.
- Do not claim signer independence or Samsung parity without evidence.

## Archived prior handoff

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
