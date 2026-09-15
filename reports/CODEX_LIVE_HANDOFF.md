# VoxGest Live Handoff

## Update - 2026-09-15 Samsung startup parity and timing checkpoint

BRANCH=recognition/fsl105-live-segment-v1

DEVICE=Samsung SM-A566B, serial R5GYC0M1M4P, ADB authorized

CHECKPOINT_SCOPE=Preserve the Standard FSL105 completed-event runtime after
device startup qualification and a process-clock timing repair. Physical class
battery was explicitly deferred by the user for the remainder of today.

DEVICE_STARTUP=PASS. APK installed and launched. Diagnostics proved
`STANDARD_FSL_FULLSIGN225`, `FSL105_LIVE_SEGMENT_V1`, `[1,20,225]` input,
`[1,105]` output, 105 labels, model/labels parity PASS, unmirrored model input,
front camera ID 1, mirrored preview, and unmirrored analysis. Device TFLite
golden parity PASS with top1 agreement and maximum probability delta
`5.8619776E-14`.

RUNTIME_FIX=Two initial setup completions exposed mixed CameraX/process clock
domains at finalization. Event milestones and latency now consistently use one
explicit monotonic process timestamp; raw camera timestamps remain authoritative
for chronological/gap evidence. A deliberately offset-clock regression test was
added. Model, labels, thresholds, preprocessing geometry, and Mapua behavior are
unchanged.

PHYSICAL_EVENTS=UNSCORED_SETUP_ONLY. HELLO setup 1 completed by CAPTURE_LIMIT
with 240 captured/20 resampled frames, pose 1.000, right-hand 0.991, and rejected
pre-inference as `CAPTURE_LIMIT_WITHOUT_SIGN_END`. HELLO setup 2 completed by
DYNAMIC_END with 99/20 frames, pose 1.000, right-hand 0.978, but invalid close
framing and a 20.903 s trajectory/5.427 s maximum gap caused pre-inference
`TRAJECTORY_DURATION_EXCEEDED`. Neither generated top5/top1 or TFLite latency.
Both are Category A diagnostics, not scored class attempts.

AUTOMATED_EVIDENCE=PASS; 126 tests across 32 suites, zero failures/errors/skips;
44/44 Gradle tasks executed; debug APK SHA-256
`9fba58ba9a4843c208a5493c3a96dadd7ba87cc38fce405fcf2bbaa99fa05dc5`.

DECISION=No confidence/margin/presence threshold change and no retraining from
today's evidence. There is no clean Category C or D event. Continue offline on
`recognition/multisource-calibration-v1`; resume the unchanged physical battery
tomorrow with wide upper-body framing and one deliberate sign per event.

REPORT=reports/FSL105_LIVE_SEGMENT_V1_20260915.md

---

## Update - 2026-09-15 FSL105 completed-event presentation expansion

TIMESTAMP=2026-09-15T13:40:23.1842053+08:00

BRANCH=recognition/fsl105-live-segment-v1

SOURCE_BASE_BRANCH=recognition/mapua14-live-segment-v1

SOURCE_BASE_COMMIT=fbfb9bc8ad80ba2ba3120236721098081fc1aed4

IMPLEMENTATION_COMMIT=8d7c3c8b04ada9bbd36cfe727d31864a29e274fe

CURRENT_GOAL=Expose the existing Standard FSL-105 model through complete-sign
segmentation and an authoritative 105-entry bilingual presentation surface,
without retraining or destabilizing Mapua-14, then qualify on Samsung if ADB is
available.

WORK_COMPLETED=

- Replaced the active Standard controller's rolling-window behavior with
  `FSL105_LIVE_SEGMENT_V1`: complete chronological event capture, full-event
  motion-envelope finalization, manifest-sized linear resampling to 20x225,
  exactly one inference, and WAIT_FOR_RELEASE duplicate suppression.
- Added a profile-driven finalizer while preserving the protected Mapua-14
  runtime; numeric parity coverage confirms its 48-frame finalization output.
- Made the Standard manifest authoritative for runtime dimensions, shapes,
  model/label filenames, ordered labels, orientation, and visible diagnostics
  banner. Exact 20/225/105 and `[1,20,225]`/`[1,105]` contracts fail closed.
- Retained anatomical identity, unmirrored inference, display-only preview
  mirroring, raw presence accounting, Standard missing-hand zero fill, malformed
  and missing-pose rejection, confidence/margin gates, and release/re-arm.
- Added the hash-gated, one-to-one 105-entry English/Filipino semantic
  presentation map. The Guide derives all canonical tokens from the runtime
  labels, exposes both display languages, retains source-token quirks, and marks
  every item FSL-105 model vocabulary / not yet device-qualified.
- Added representative event, resampling, geometry, malformed-input,
  release/re-arm, manifest, bilingual topology, localization, and protected
  Mapua parity tests.
- Completed the focused and forced full automated suite and assembled a debug
  APK. Samsung installation/live qualification could not start because ADB was
  empty on five bounded checks; no physical result was invented.

ASSET_STATUS=Model and labels unchanged. Model SHA-256
`42d040ec2269d437546d327decaaca32839abdd6bb63b2d400063c90630e5d13`;
labels SHA-256
`bfa76d96ed10bf97f43ca80bcfcc5badd3e96df7ebe4c0654fc078552da55fb6`;
presentation SHA-256
`98b98a49a515fc9593529bac17eb9c50c0f3486b2506dd4c3d22e6b6364ee492`.

COMMANDS/TESTS=

- Focused FSL105/manifest/Guide/localization/Mapua regression suites: PASS.
- Forced `:app:testDebugUnitTest :app:assembleDebug --rerun-tasks`: PASS;
  125 tests across 32 suites, 0 failures, 0 errors, 0 skipped; 44/44 tasks
  executed; BUILD SUCCESSFUL in 40 seconds.
- APK SHA-256:
  `df6c343fa7495b9cf8d41ecf9664fa60f12b4f59a8089288104d77cc7f31b3f6`.
- Manifest/label/presentation JSON topology and hashes: PASS; 105 ordered unique
  canonical labels and 105 ordered unique bilingual entries, no blank display.
- `git diff --check`: PASS; line-ending conversion warnings only.
- Protected Mapua production source/model/labels diff: empty.
- `adb devices -l`: empty across the initial query, three bounded rechecks, and
  the final post-build query.

SAMSUNG_STATUS=BLOCKED_DEVICE_NOT_CONNECTED. Install, launch, runtime-banner
inspection, visual orientation/mirroring inspection, device startup parity, ten
representative words, negative trials, and physical latency remain NOT_RUN.

LIVE_WORD_RESULTS=HELLO, YES, NO, THANK YOU, ONE, FIVE, MILK, RICE, GOOD
MORNING, and UNDERSTAND are all NOT_RUN_DEVICE_NOT_CONNECTED; raw top1,
confidence, gate, latency, and tracking are NOT_CAPTURED.

NEGATIVE_RESULTS=Neutral, open-palm, and random-motion trials are
NOT_RUN_DEVICE_NOT_CONNECTED; emitted-token counts and latency are NOT_CAPTURED.

TRAINING_STATUS=UNCHANGED_NO_RETRAIN. No dataset or trainer was accessed, model
weights and canonical labels remain byte-identical, and no threshold was tuned.

RETRAIN_DECISION=NO. Automated runtime/contracts pass and there is no physical
category-D evidence of wrong raw top1 with clean segmentation and tracking.

FAILURES=The only incomplete phase is physical qualification because the
Samsung is not enumerated by ADB. The initial sandboxed Gradle invocation could
not download/access its distribution; the approved rerun used the existing
Gradle cache/distribution and passed, so it is not a source failure.

REPORT=reports/FSL105_LIVE_SEGMENT_V1_20260915.md

NEXT_ACTION=Reconnect and authorize Samsung `R5GYC0M1M4P`; verify it appears in
`adb devices -l`; install the prepared debug APK; launch Standard diagnostics;
capture startup parity, the manifest-derived `STANDARD_FSL_FULLSIGN225 /
FSL105_LIVE_SEGMENT_V1 / 20x225 / 105 classes` banner, and visual orientation;
then run the ten specified one-sign/one-release trials plus neutral/open-palm/
random-motion negatives. Classify failures A-F before any calibration/retrain.

DO_NOT_MODIFY=Do not access D:. Do not retrain or tune thresholds without clean
category-D physical evidence. Do not alter canonical labels/model weights,
unmirrored FullSign225 geometry, anatomical slots, monotonic timing, protected
Mapua-14 production behavior, or presentation styling beyond explicit scope.
Do not commit APKs, raw recordings, datasets, caches, secrets, or huge evidence.
Do not force-push or rewrite history.

---

## Update - 2026-09-14 OLD 1-5 escalation and NEW-test preparation

TIMESTAMP=2026-09-14T06:19:05.0126952+08:00

BRANCH=recognition/mapua14-live-segment-v1

COMMIT=e0e4ff6ea049bb6340319dd32476a064727e8d32

SOURCE_BASE_COMMIT=b8da0c52acc6d531c8a7a4f328454a76659fb7e6

HANDOFF_UPDATE_COMMIT=e6904bfa1899ed481bcb687a991c052a655a9882

BRANCH_HEAD=e6904bfa1899ed481bcb687a991c052a655a9882 (metadata-only handoff correction follows)

CURRENT_GOAL=Stop the unusable OLD rolling48 series at five attempts, reconstruct all captured evidence without inference, audit the existing NEW completed-event/resampled48 runtime, and prepare five NEW HELLO attempts without retraining or threshold tuning.

WORK_COMPLETED=

- Preserved the complete OLD 1-5 log/screenshot set outside Git under
  `C:\VOXGEST_TRAINING\MAPUA14_RESCUE_V1\evidence\live_segment_ab_20260914`.
- Reconstructed each attempt's raw prediction sequence, gate decisions,
  rejection/event-state reasons, rolling-window duration, MediaPipe snapshots,
  TFLite latency, presence evidence, accepted token, marker timing, outcome, and
  evidence hashes. Missing fields are explicitly `NOT_CAPTURED`.
- Stopped the OLD series at five; Attempts 6-10 do not exist.
- Kept the canceled positioning interval separate from scored HELLO attempts.
  It emitted false-positive HELLO and THANK_YOU tokens without an instructed
  sign and explains accumulated presentation text.
- Audited NEW source and tests for the required state flow, chronological event
  capture, finalization-before-inference, exact complete-trajectory resample48,
  static/dynamic completion, release/re-arm duplicate suppression, anatomical
  hand identity, ambiguous collision fail-closed behavior, and display-only
  preview mirroring.
- Added diagnostic-only timestamps and explicit top-3/end-to-raw/
  end-to-accepted fields for the NEW physical test. No runtime decision,
  threshold, model input, or output behavior changed.
- Verified the existing machine-readable 14-label asset and documented it as
  the only MAPUA14 supported-vocabulary authority.
- Corrected architecture documentation to distinguish no copied detections in
  the identity stabilizer from the audited one-to-three-frame preprocessing
  interpolation policy; raw tracking quality is counted before interpolation.
- Built the diagnostic-ready APK. A later installation attempt was blocked
  because the Samsung disconnected from ADB; no NEW attempt or startup result
  was fabricated.

FILES_CHANGED=

- android_dry_run/app/src/main/java/com/voxgest/dryrun/Mapua14LiveSegmentCameraRecognitionController.kt
- docs/ARCHITECTURE_DECISIONS.md
- docs/MAPUA14_SUPPORTED_VOCABULARY_CONTRACT.md
- docs/PROJECT_HISTORY_JUNE_SEPT_2026.md
- reports/mapua14_rescue_v1/SAMSUNG_LIVE_SEGMENT_AB_20260914.md
- reports/mapua14_rescue_v1/SAMSUNG_OLD_1_5_DIAGNOSTIC_20260914.md
- reports/CODEX_LIVE_HANDOFF.md

COMMANDS/TESTS=

- Initial Gradle command without `JAVA_HOME`: environment failure before
  compilation; rerun with Android Studio JBR.
- `:app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug`: PASS.
- Forced rerun of six relevant suites: 24 tests, 0 failures, 0 errors, 0 skipped.
- Forced full `:app:testDebugUnitTest :app:assembleDebug`: PASS; 115 tests across
  30 suites, 0 failures, 0 errors, 0 skipped; 44/44 Gradle tasks executed.
- Prepared APK SHA-256:
  `bdf8c859b9b71d84e6afa00ce6b16a548b9a908e027a4c9dfb00204554504fc5`.
- Model SHA-256:
  `f850c5d414c5c253ef9131bae5a85bb3ed5ad5412abdf9936df510c6ec043dcc`.
- Label SHA-256:
  `af398236fd62da6c5bafbe0b60d21bc8a155c48aeb45987090c1b14a20cb9ef0`.
- `git diff --check`: PASS; line-ending conversion warnings only.
- Final preparation `adb devices -l`: empty; install/start not attempted further.

DATASET_STATUS=UNCHANGED. No dataset read, write, ingestion, or generation occurred.

TRAINING_STATUS=UNCHANGED_NO_RETRAIN. Existing RD-TCN48 weights remain byte-identical; no trainer was run and no threshold was tuned.

SAMSUNG_STATUS=OLD_1_5_CAPTURED_THEN_DEVICE_DISCONNECTED. OLD evidence is preserved. The prepared diagnostic APK is not yet installed because ADB became empty. NEW physical attempts remain NOT_TESTED.

METRICS=OLD scored HELLO attempts: 5. Correct raw top-1 attempts: 3/5. Accepted correct: 3/5. No-inference attempts: 2/5. Wrong accepted within scored attempts: 0/5. Each successful attempt first had one correct raw window rejected for TEMPORAL_STABILITY and then one correct accepted window. Warmed TFLite calls were 0.689687-0.952930 ms. Rolling windows were 5,499-5,795 ms. User-observed delays: Attempt 3 ~40 seconds, Attempt 4 ~15 seconds after moving closer, Attempt 5 >60 seconds with no result. Exact post-sign latency is NOT_CAPTURED. Separate canceled positioning evidence contains false accepts HELLO and THANK_YOU.

FAILURES=OLD is operationally unusable. Attempt 1 never inferred because the signer was not framed. Attempt 5 at the same closer distance as Attempt 4 never inferred and logged WAITING_FOR_NEUTRAL_RELEASE:588 plus IDLE_NO_LANDMARKS:487. Existing Clear controls did not remove the stale recognition banner; controlled process restart cleared presentation state, and no UI code was changed. The Samsung later disconnected before the diagnostic APK could be installed.

CURRENT_HYPOTHESIS=The dominant OLD failure is pre-inference readiness/landmark/event-gate instability plus arbitrary rolling48 formation, not TFLite execution. Attempt 4's shorter user-observed delay correlates with better MediaPipe throughput/latency, but Attempt 5 failed at the same distance; proximity causation is insufficiently evidenced. NEW must now prove whether completed-event finalization/resampling reliably reaches raw inference and acceptance within 1-3 seconds after sign completion.

NEXT_ACTION=Reconnect and authorize Samsung `R5GYC0M1M4P`; install the prepared APK; launch with diagnostics and exact profile `MAPUA14_LIVE_SEGMENT_V1`; capture startup feature/TFLite parity and camera/mirror identity; then interactively run five HELLO attempts at one consistent framing distance. Each attempt is neutral -> one HELLO -> neutral -> wait. Stop if repeated latency exceeds 10 seconds or failures recur. Continue NEW to ten only if the first five clearly improve over OLD.

PAPER_IMPACT=YES

PAPER_SECTIONS_TO_REVIEW=Chapter 1 Scope and Limitations; Chapter 3 temporal preprocessing/runtime architecture; Chapter 4 physical-device recognition results; Threats to validity.

DO_NOT_MODIFY=Do not access D:. Do not retrain, tune thresholds, modify Avatar/Listen/UI, replace or promote Standard FSL-105, change canonical unmirrored geometry or anatomical slots, change `(System.nanoTime() / 1_000_000L)`, invent physical results, or commit raw logs/screenshots/APKs/datasets/caches/checkpoints/secrets. No force push or history rewrite.

---

## Update - 2026-09-14 live-segment pre-device checkpoint

TIMESTAMP=2026-09-14T04:45:23+08:00

BRANCH=recognition/mapua14-live-segment-v1

COMMIT=d806e01a1ebeeb61511d5972b42a26ebe26903df

CURRENT_GOAL=Physically compare the rollback rolling48 lane with the isolated complete-event/resampled48 lane on the authorized Samsung without retraining or threshold tuning.

WORK_COMPLETED=

- Preserved interrupted work in checkpoint commits `e5bb4d4d` and `efa3b29d`.
- Added debug-only `MAPUA14_LIVE_SEGMENT_V1` with IDLE -> ARMING -> CAPTURING -> FINALIZING -> INFERENCE -> WAIT_FOR_RELEASE.
- Finalized chronological events with the audited internal-gap, adaptive motion-envelope, and complete-trajectory linear resampling policy to exactly `[1,48,225]`.
- Kept the existing RD-TCN48 model and label assets byte-identical.
- Added temporal anatomical hand identity, bounded short reacquisition, diagnostics, and fail-closed ambiguity handling without image-X slot assignment or landmark fabrication.
- Added CameraX/Camera2 FRONT/BACK/EXTERNAL discovery, latest-frame analysis, display-only preview mirroring, asynchronous camera-open/error handling, and resource cleanup.
- Preserved Standard FSL-105 identity and outputs; shared CameraX mirroring/source/lifecycle safety was updated only where required.
- Added dated A/B report skeleton, Android/external-camera documentation, architecture decision, project-history milestone, and hand-occlusion test record.

FILES_CHANGED=

- android_dry_run/app/src/main/java/com/voxgest/dryrun/Mapua14LiveSegmentStateMachine.kt
- android_dry_run/app/src/main/java/com/voxgest/dryrun/Mapua14CompleteTrajectory48.kt
- android_dry_run/app/src/main/java/com/voxgest/dryrun/Mapua14LiveSegmentCameraRecognitionController.kt
- android_dry_run/app/src/main/java/com/voxgest/dryrun/Mapua14SegmentGate.kt
- android_dry_run/app/src/main/java/com/voxgest/dryrun/TemporalAnatomicalHandIdentityStabilizer.kt
- android_dry_run/app/src/main/java/com/voxgest/dryrun/MediaPipeLandmarkExtractor.kt
- android_dry_run/app/src/main/java/com/voxgest/dryrun/CameraSourceDiscovery.kt
- shared runtime/routing and corresponding unit tests
- docs/ANDROID_COMPATIBILITY_MATRIX.md
- docs/EXTERNAL_CAMERA_SUPPORT.md
- docs/PROJECT_HISTORY_JUNE_SEPT_2026.md
- docs/ARCHITECTURE_DECISIONS.md
- reports/HAND_OCCLUSION_STRESS_TEST.md
- reports/mapua14_rescue_v1/SAMSUNG_LIVE_SEGMENT_AB_20260914.md

COMMANDS/TESTS=

- `gradlew :app:compileDebugKotlin :app:compileDebugJavaWithJavac`: PASS.
- `gradlew :app:testDebugUnitTest :app:assembleDebug`: PASS.
- Unit results: 115 tests, 0 failures, 0 errors, 0 skipped across 30 suites.
- Debug APK SHA-256: `f1d66b42e7b9074705b39e5340d4924b34ba8a24b9d527fcf5ae591aa5fb826f`.
- `git diff --check`: PASS (line-ending conversion warnings only).
- `adb devices -l`: no connected devices; repeated checks and bounded wait produced an empty inventory.

DATASET_STATUS=UNCHANGED. No dataset access, ingestion, mutation, or training occurred.

TRAINING_STATUS=UNCHANGED_NO_RETRAIN. Existing RD-TCN48 SHA-256 is `f850c5d414c5c253ef9131bae5a85bb3ed5ad5412abdf9936df510c6ec043dcc`; labels SHA-256 is `af398236fd62da6c5bafbe0b60d21bc8a155c48aeb45987090c1b14a20cb9ef0`.

SAMSUNG_STATUS=BLOCKED_DEVICE_NOT_CONNECTED. Target remains Samsung SM-A566B / `R5GYC0M1M4P`; install, startup parity logs, 10 OLD HELLO attempts, 10 NEW HELLO attempts, mirror confirmation, and physical hand-collision sequence are not yet captured.

METRICS=Series-1 preserved evidence: HELLO raw top1 4/4 at approximately 0.999999; accepted 0/4; rejection reason TEMPORAL_STABILITY 4/4; rolling windows 5,829-5,963 ms; MediaPipe approximately 7.988 FPS; TFLite 0.803-1.050 ms. New physical metrics are NOT_YET_TESTED.

FAILURES=Physical validation could not start because ADB reported zero devices. No Samsung performance or recognition result is inferred, and no live-fix claim is made.

CURRENT_HYPOTHESIS=Series-1 directly demonstrates correct HELLO raw predictions blocked by the old temporal-stability gate. The new completed-event representation removes the arbitrary rolling-window mismatch, but Samsung A/B evidence is required to determine whether it improves raw and accepted outcomes.

NEXT_ACTION=Reconnect and authorize `R5GYC0M1M4P`; install the existing debug APK; launch OLD then NEW with exact debug extras; capture operator-marked 10+10 HELLO attempts one at a time; report raw classifier and gate outcomes separately; proceed to 14x5 only if NEW clearly improves HELLO.

PAPER_IMPACT=YES

PAPER_SECTIONS_TO_REVIEW=Methods/temporal preprocessing; Android runtime architecture; experimental protocol and live-device results; threats to validity and limitations. Do not revise success claims until physical evidence exists.

DO_NOT_MODIFY=Do not access D:. Do not retrain or tune thresholds during A/B. Do not modify Avatar, Listen, UI styling, Standard FSL-105 model behavior, anatomical slot semantics, or `(System.nanoTime() / 1_000_000L)`. Do not version raw device logs, APKs, datasets, caches, checkpoints, environments, or secrets. Do not promote the debug model to Standard.

---

## Previous checkpoint - 2026-09-12

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
