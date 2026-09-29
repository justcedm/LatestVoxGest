# VoxGest Live Handoff

LATEST_STREAMING_GEOMETRY_FEASIBILITY_20260929=Read the new Core5 proposal from fetched `origin/main` without changing the recognition branch. Offline raw-landmark analysis proves the existing 225 tensor preserves nose-relative hand direction/within-hand shape but scales hands separately from pose; direct hand-minus-face distances from its blocks are invalid. An official FSL-105 train-only geometry profile used 61 fitting and 20 development clips, skipped the sealed official test files before opening, and achieved only 12/20 development nearest-centroid identification; a source-only p95 plausibility rule retained 16/20. The unchanged SIM10 TFLite model was replayed over 1.3/1.5/1.8/2.0-s rolling Samsung windows across all 69 saved events. Of 20 positive timeouts across manual/automatic modes, 15 eventually had a stable intended-class window before timeout, but only 14 had it as the first stable peak; for the nine AUTO_LEGACY timeouts specifically, 5/9 had the intended first peak (THANK YOU and NO only). Raw stability also peaked falsely on 7/16 non-signs (all six waves); exploratory source geometry reduced false peaks to 2/16 but retained intended stable peaks on only 12/53 positives. Not an acceptance improvement; no Android streaming implementation, model/gate/UI/Avatar change, or new Samsung signing. Full report: `reports/fsl_core5_rebase_v1/CORE5_STREAMING_SEMANTIC_GEOMETRY_FEASIBILITY_20260929.md`. Private sealed-source rerun matrices remain outside Git. NEXT_EXACT_ACTION=independently annotate sign/negative intervals, establish source-grounded cross-device/OOD verifier and rearm safety, then rerun held-out offline before any recognition-lab Android change.

LATEST_BOUNDARY_FILTER_SENSITIVITY_20260928=Offline-only causal score filtering at 0/100/200 ms expanded the Boundary V2 replay to 432 configurations across 69 retained Samsung events. No safe operating point emerged: with zero later-high-motion early-cut proxy flags, the best filtered setup ended only 1/9 old automatic timeouts, missed a candidate ending in 16/18 automatic events, and generated 3–4/16 negative candidates. A timeout-responsive setup ended 5/9 but flagged 4 automatic plus 3 manual-positive early-cut risks and generated 5/16 negative candidates. Classifier tensors were unchanged; 5/5 synthetic boundary tests passed. Official FSL-105 train-only, envelope-based class diagnostics cover 81 clips; official test stayed sealed. `CORE5_BOUNDARY_V2_CHECKPOINT.md` and state JSON updated. Do not implement Android V2 or request live A/B until independent sign-end/hold annotations and held-out safety evidence exist. OOD rejector remains source-inventory feasibility only; Samsung negatives remain sealed.

LATEST_DEVICE_COEXISTENCE_20260928=Samsung SM-A566B reconnected and authorized. `adb install -r` installed only new `com.voxgest.dryrun.recognitionlab` (SHA256 `1a3d2770634c79ce4e6b3ce00a3dacab289773ee389599d573a05f7d4957cd07`). Post-install user-0 hashes prove modern `com.voxgest.dryrun` stayed `6354700532f8e49eb7e16ec2e8efc7edd884dd24db1f90c1c1444c8cdd08e912` and Avatar trial stayed `f87c8a0935bfdd9c0cc83e332e63d3cb60a5ed4cf82c7531b98b39fc6b289c88`. No uninstall/data clear. The lab was not used for V2 signing because offline Boundary V2 did not pass. Five synthetic state-machine/filter unit tests PASS. See `reports/fsl_core5_rebase_v1/CORE5_BOUNDARY_V2_CHECKPOINT.md`.

LATEST_BOUNDARY_V2_20260928=Phase-0 Core5 Samsung evidence was safely committed/pushed at `fb3edaf5a43aaa8bfcedc96823a23d26e3f1ef1b`; remote HEAD verified. Three direction docs were read from fetched `origin/main` without checkout. Separate recognition-lab package built/tested (139 JVM tests PASS) and later installed alongside byte-verified modern UI and Avatar; see device coexistence above. Offline timestamp-normalized XY translation/articulation/arm descriptors cover 69 saved events/3,779 frames; 144 hysteresis/dwell/post-roll candidates were replayed. No candidate both avoided conservative later-motion early-cut flags and ended any of nine legacy timeouts; the most timeout-responsive candidate ended 5/9 but flagged 4 auto + 4 manual-positive early cuts and six negative candidate events. Therefore no Android V2 was implemented/promoted or physically tested. Capture-quality telemetry overlaps wrong-accepted OOD; independent rejection remains required. See `reports/fsl_core5_rebase_v1/CORE5_BOUNDARY_V2_CHECKPOINT.md` and `CORE5_BOUNDARY_V2_STATE.json`. Next: obtain independent sign-end/hold annotations, repeat held-out offline search and truncated-tensor/OOD replay. No model, gate, threshold, Avatar, modern UI, or production recognition change.

LATEST_CORE5_AB_20260928=Read-only same-Samsung-tensor replay complete for YES 11, THANK YOU 9, NO 9, UNDERSTAND 10, wave 6, and partial 5 events. Baseline/Native48/SIM10 raw-correct counts: YES 9/8/4 of 11; THANK YOU 9/9/9 of 9; NO 7/9/9 of 9; UNDERSTAND 8/8/8 of 10. Under the unchanged saved-event gate, all three models still falsely accept 4/6 arbitrary waves and 1/5 partial gestures. Baseline accepts two correct YES but also the same wrong THANK YOU for a left-only YES event; none is a safe user-facing replacement. See `reports/fsl_core5_rebase_v1/CORE5_SAME_TENSOR_MODEL_COMPARISON_20260928.md` and private full vectors in the evidence folder. No model switch, retraining, threshold, UI, Avatar, or production change. Next: offline class-agnostic motion-settle/OOD trajectory audit; no further human signing requested now.

## Current: Core5 Samsung multiclass and OOD qualification — 2026-09-28

BRANCH=recognition/fsl-core5-rebase-v1; HEAD=1c99f95ee5f4446e8a146963db80b256e8f7dafc. Existing Core5 worktree remains dirty; do not reset/clean it. Samsung SM-A566B serial R5GYC0M1M4P authorized. Exact diagnostic APK SHA256 924e81190c44ac4e804bb2738e8c6e35b8cde664f435e0f5bf814230150dcec8 was restored with adb install -r after backing up the prior UI V2 APK to private evidence; no uninstall/data clear, Avatar package untouched. Debug-only profile FSL_CORE5_SIM10FPS_V1; model SHA256 3702ff77c1c44a60f0dc7f06e19e778b6498df7dbf7e205991dc15158b8e888f. Startup feature/temporal/golden TFLite parity PASS. Android JVM tests 139/139 PASS. No model, gate, threshold, production-route, UI, or Avatar change.

MANUAL_POSITIVES=Preserved HELLO 5/5 raw and accepted. New THANK YOU 5/5 raw, 4/5 accepted (one timeout). NO six confirmed physical-right-hand attempts: 6/6 raw, 3/6 accepted (two timeouts, one low confidence). UNDERSTAND: physical-right-hand subset 5/5 raw and 3/5 accepted; two deliberately left-hand signs were raw wrong and rejected. YES: six right-hand tracked attempts raw YES 4/6, raw HELLO 2/6, accepted YES 0/6; an additional left-only YES attempt was raw THANK YOU and wrongly accepted. All inferred saved events have exact tensor rebuild and desktop/Android TFLite parity PASS. These are controlled operator-intent diagnostics, not formal accuracy claims. Do not retrain from Samsung events.

NEGATIVE_OOD=Five neutral events: 0/5 false accepts, no inference. Six confirmed arbitrary non-FSL waves: 4/6 wrong accepted (THANK YOU twice, HELLO twice). Five confirmed incomplete/aborted gestures: 1/5 wrong accepted UNDERSTAND. Overall frozen negative battery 5/16 false accepts. The current candidate is **NOT user-facing/survey ready**. Confidence is very high even on accepted waves; do not attempt to fix by weakening or blindly raising thresholds.

AUTO_BOUNDARY=Initial battery complete, 18 saved events including two explicit hold controls. All nine releases had exactly three trailing no-hand frames; all nine timeouts retained a hand in every frame. Four AUTO_LEGACY HELLO events all raw/accepted HELLO but only three were deliberate signs and the extra accepted event cannot be mapped: do not score four correct. THANK YOU four raw correct, two releases/two timeouts. NO three raw correct, all timed out. YES four deliberate right-hand events, all raw HELLO and timed out. UNDERSTAND three raw/accepted and released. Deliberate visible/stationary-hand hold reproduced raw-correct timeout with both hands tracked in all 55 frames; lower-edge hold lost hand tracking for three frames and released. State-level root: legacy collector requires hand disappearance, not motion settle. This is separate from classifier/OOD failures. No collector code changed.

EVIDENCE=reports/fsl_core5_rebase_v1/CORE5_5PM_8PM_CHECKPOINT.md; CORE5_5PM_8PM_STATE.json; CORE5_SAMSUNG_MULTICLASS_QUALIFICATION_20260927.md; CORE5_YES_CLASS_FAILURE_20260928.md; CORE5_AUTO_BOUNDARY_FORENSICS_20260928.md. Private 69-event matrix is D:/VoxGest/evidence/fsl_core5_rebase_v1/core5_samsung_event_matrix_20260927.json and .csv; current turn added 59 events, 54 with tensors and replay PASS, five neutral/no-inference. Private raw events, tensors, replays, screenshots and logs remain outside Git. Rollback UI V2 APK is retained privately as rollback_ui_v2_6354700532f8.apk (SHA256 6354700532f8e49eb7e16ec2e8efc7edd884dd24db1f90c1c1444c8cdd08e912). NEXT_EXACT_ACTION=Read-only same-tensor YES Baseline/Native48/SIM10 comparison and offline motion-settle/OOD trajectory analysis. Any later repair must be isolated/debug-only with rollback; do not connect this profile to production Sign text/TTS.

## Current: Core5 multiclass qualification paused after Phase 0 — 2026-09-27

BRANCH=recognition/fsl-core5-rebase-v1
HEAD=1c99f95ee5f4446e8a146963db80b256e8f7dafc; existing Core5 worktree remains uncommitted.
PHASE0=Nine preserved Samsung HELLO tensors replayed through Baseline, Native48, and SIM10. All 27 raw top-1 outcomes are HELLO; full vectors in D:/VoxGest/evidence/fsl_core5_rebase_v1/core5_hello_same_tensor_ab_20260927.json and .csv. No top-1 advantage is established for SIM10 on HELLO.
PHASE1-3=NOT_STARTED. Current installed com.voxgest.dryrun APK SHA256 6fbf1c0ed078201a629095065c9cce3569137ee662c181db034daa0ee6ffd8c9 differs from validated SIM10 APK 924e81190c44ac4e804bb2738e8c6e35b8cde664f435e0f5bf814230150dcec8. Core5DiagnosticActivity is absent from current installed APK. No replacement or reinstall has been made; owner direction to restore the validated debug APK is pending.
EXTRA_EVENT=7dc79689-2fb9-407b-b205-23f8cfd94a34, UI-labelled HELLO after Phase0. Operator's performed sign unconfirmed; preserved and excluded from scored battery.
REPORT=reports/fsl_core5_rebase_v1/CORE5_SAMSUNG_MULTICLASS_QUALIFICATION_20260927.md (in progress).
NEXT_ACTION=If owner authorizes restoring the validated SIM10 APK, use adb install -r without uninstall/data clear, recheck installed hash and startup parity, then collect THANK YOU/YES/NO/UNDERSTAND manual batches, negatives, and finally unchanged AUTO_LEGACY events. Do not alter production/default models or gates.

## Current: Core5 SIM10 Samsung HELLO-only candidate gate — 2026-09-27

BRANCH=recognition/fsl-core5-rebase-v1
HEAD=1c99f95ee5f4446e8a146963db80b256e8f7dafc; Core5 worktree remains uncommitted.
PROFILE=FSL_CORE5_SIM10FPS_V1, explicit debug-only candidate; baseline is the default.
MODEL_SHA256=3702ff77c1c44a60f0dc7f06e19e778b6498df7dbf7e205991dc15158b8e888f
DEVICE=Authorized Samsung SM-A566B; installed APK hash matches built APK.
PARITY=Supplied golden top-1 HELLO; Android max probability delta 1.1920929e-7 (PASS). Five saved live events have exact desktop/Android replay PASS.
HELLO=Five timely-ended MANUAL events, raw HELLO 5/5 and accepted HELLO 5/5. Four other HELLO-labelled events timed out and were correctly rejected; do not turn the five-event diagnostic result into a general accuracy claim.
PRESERVATION=Core5 V1 model SHA unchanged; no production gate, UI, Avatar, demo10, or other model change. Private events/logs/screenshots remain outside Git.
REPORT=reports/fsl_core5_rebase_v1/SIM10FPS_SAMSUNG_QUALIFICATION_20260927.md
NEXT_ACTION=Stop at HELLO and await review/authorization for other classes and negative/OOD tests. Do not retrain or change gates from this result.

## Current: canonical D migration, Stages0-2 only - 2026-09-22

BRANCH=recognition/fsl105-mapua-unified-tasks-v1
SOURCE_BASE_COMMIT=5ca87048592a0d4cdd880afa6a03a8f5c62e28fb
COMMIT=Resolve current migration checkpoint through git log for this file.
CURRENT_GOAL=Organize active versus historical/ASL/unvalidated storage by copy and
verification. Do not start ML in this session; reopen Codex from the new D repository
after migration. The new user authority explicitly permits D and supersedes earlier
historical prohibitions on D access. Original C worktree stays in place.

STAGE1=Fresh clone/branch and canonical layout ready. Both published datasets copied
with exact hashes;4361 file readability checks passed, including all3237videos.
New-location Android tests/build PASS129/31. Required ignored Avatar preview restored
byte-identically, separately manifested. No app source/model/gate behavior change.
STAGE2=Archive/quarantine verification ongoing; current report:
reports/storage_migration/STAGE1_20260922.md. Known ASL excluded from FSL; mixed legacy
recordings labelled unvalidated, not assumed ASL. Rollback models remain immutable.

EXTERNAL_CHANGE=Owner confirmed Downloads cleanup/movement during run;34 planned
entries missing before copy. Record unresolved/excluded, never as verified retirement.
DELETIONS_BY_AGENT=0. TRAINING=NOT_STARTED. LIVE_TESTING=NOT_RESUMED.
NEXT_ACTION=Finish remaining copy/readability/parity evidence, produce exact retirement
manifest with source-process/approval gates, push only safe migration branch changes,
then stop for user to reopen D:\VoxGest\repository\LatestVoxGest before ML stages.

## Archived Samsung wrap-up authority

## Current: Samsung testing stopped by owner - 2026-09-22

TIMESTAMP=2026-09-22T13:41:00+08:00
BRANCH=recognition/fsl-dual-dataset-reset-v1
SOURCE_BASE_COMMIT=d010246813a3f1815be64c7333ea7ba7b31cf9d7
COMMIT=Resolve wrap-up documentation checkpoint using git log for this file.
HANDOFF_UPDATE_COMMIT=This documentation checkpoint. BRANCH_HEAD=base plus wrap-up.
CURRENT_GOAL=STOP physical testing; provide factual summary for owner/ChatGPT review.
WORK_COMPLETED=Final filtered runtime log saved; diagnostic app force-stopped with
returncode0. No new code/model/gate changes. Full review in
fsl_dual_dataset_reset_v1/SAMSUNG_TEST_WRAPUP_20260922.md.
SAMSUNG_STATUS=AUTHORIZED_AT_WRAPUP; APP_STOPPED; NOT_PHYSICALLY_QUALIFIED.
METRICS=129 passing JVM tests; installed/startup parity PASS; accepted controlled
HELLO success NOT ESTABLISHED. Formal positive/negative accuracy and p95 unavailable.
FAILURES=Confirmed pre-fix anatomical inversion and unintended CASH false accept;
post-fix unscored wrong/unverified outputs, timeouts, incomplete events. One raw
HELLO .9134132 rejected LOW_CONFIDENCE, with expected gesture unconfirmed.
CURRENT_HYPOTHESIS=Multiple layers remain unresolved; raw HELLO shows inference is
not universally absent but does not prove classifier correctness or gate suitability.
NEXT_ACTION=Wait for owner/ChatGPT review; do not request more signs or resume tests
automatically. Owner said CHECK DONE but did not explicitly confirm corrected L/R.
FILES_CHANGED=This handoff; SAMSUNG_TEST_WRAPUP_20260922.md.
COMMANDS_TESTS=Final log collection and am force-stop returncode0; no new build needed.
DATASET_STATUS=UNCHANGED. TRAINING_STATUS=NO_RETRAINING.
DO_NOT_MODIFY=Frozen models/labels/gates and protected profiles/UI/Avatar. Analysis
unmirrored; LIVE_STREAM remains non-default and untested. No retired workspace access.

## Archived installed correction checkpoint

## Current: installed anatomy correction, physical recheck pending - 2026-09-22

TIMESTAMP=2026-09-22T13:30:00+08:00
BRANCH=recognition/fsl-dual-dataset-reset-v1
SOURCE_BASE_COMMIT=ab6a7c508fc6c8cdaae319a2a365ee8108f764c6
COMMIT=Resolve exact correction checkpoint with git log for this file.
HANDOFF_UPDATE_COMMIT=This checkpoint; BRANCH_HEAD=base plus scoped correction.

CURRENT_GOAL=Verify corrected hand slots, then five controlled HELLO trials and
three consecutive correct accepts under unchanged model/gates.

WORK_COMPLETED=Owner confirmed both inverted slots and unintended CASH .99757093
false accept. Added one Practical15-specific anatomical boundary, nine tests,
fullscreen-only FILL_CENTER and isolated debug opt-in LIVE_STREAM wiring. Built,
installed and verified exact installed APK hash. Runtime Practical15/ordered labels,
feature golden and TFLite golden parity PASS. Front camera analyzing normally.

SAMSUNG_STATUS=AUTHORIZED; UPDATED_APK_INSTALLED; STARTUP_PARITY_PASS;
PHYSICAL_ANATOMY_RECHECK_REQUESTED. VIDEO_DEFAULT active; experiment inactive.
METRICS=129 tests/31 suites, zero failures/errors. Analyzer samples9.909-11.701fps.
Controlled HELLO0/5, milestone0/3; no post-fix controlled result yet.
POST_INSTALL_UNSCORED=Saved CARD .98868203 ACCEPT/EMIT, PROBLEM .6892044 REJECT,
DISCOUNT .80464745 REJECT plus timeout/incomplete transitions. Expected actions
remain unconfirmed; no HELLO success or formal neutral rate claimed. Details in
ANATOMY_TEMPORAL_FIX_20260922.md; physical hand identity reply remains required.

FAILURES=Earlier unintended CASH is a confirmed false accept, not HELLO success.
Settings ACTIVE LAUNCHER card describes the packaged default, not debug override;
runtime ACTIVE_PROFILE/model-load logs prove Practical15. No UI redesign made.
CURRENT_HYPOTHESIS=Removing the device-proven incorrect side swap restores anatomy;
physical confirmation pending. No model-failure or retraining conclusion yet.

NEXT_ACTION=Owner LEFT-only2s -> neutral3s -> RIGHT-only2s -> neutral10s; save logs
and observations, then five individually marked HELLO trials with project reference.
EVIDENCE_PATHS=reports/device_tests/samsung/anatomy_temporal_fix_20260921/ (ignored).
See fsl_dual_dataset_reset_v1/ANATOMY_TEMPORAL_FIX_20260922.md.

FILES_CHANGED=Practical15TasksAnatomy/tests; opt-in MediaPipe extractor; Practical15
controller; isolated adapter; fullscreen scale; .gitignore; physical reports/handoff.
COMMANDS_TESTS=testDebugUnitTest/assembleDebug PASS; install/hash PASS; feature
parity maxerror0; TFLite parity maxerror1.1920929E-7; remote/base parity verified.
DATASET_STATUS=UNCHANGED. TRAINING_STATUS=NO_RETRAINING.
DO_NOT_MODIFY=Models/labels/gates, StandardFSL105/Mapua14/demo10/OneHand162/Avatar,
mini-camera layout. Analysis unmirrored; LIVE_STREAM non-default. Never access the
retired workspace. Physical recognition success remains UNPROVEN.

## Archived physical authority before owner confirmation

## Current authority: physical Samsung HELLO qualification — 2026-09-21

TIMESTAMP=2026-09-21T20:22:00+08:00

BRANCH=recognition/fsl-dual-dataset-reset-v1

SOURCE_BASE_COMMIT=2ecd30a128ed90024481f471fcd5c321bf39dd96

COMMIT=Physical evidence/documentation checkpoint; exact commit via git log for this file.

CURRENT_GOAL=Five controlled HELLO attempts and three consecutive correct accepted
HELLO outputs with the existing model and frozen gates; fix only device-proven defects.

WORK_COMPLETED=Exact required clean branch/remote parity verified; SDK ADB resolved;
Samsung authorized by owner; audited debug APK installed with Success and exact
installed SHA identity; correct activity/profile launched; camera started; startup
feature/model parity and live inference saved. Android16/API36, Samsung SM-A566B,
arm64-v8a. Live analyzer approximately8.3–10fps in inspected samples.

SAMSUNG_STATUS=CONNECTED_AUTHORIZED; STARTUP_PARITY_PASS; ANATOMICAL_SLOT_MISMATCH.

METRICS=Controlled HELLO0/5, milestone0/3. Uncontrolled setup events1–4 predicted CASH;
events1/2 rejected confidence, event3 accepted .99757093 with visible CASH text and
EMIT log, event4 rejected hand presence. TTS audible output not confirmed. No scored
neutral accuracy or formal wrong-accept rate can be assigned to uncontrolled setup.

FAILURES=Owner identified the raised hand in neutral_ready_screen.png as physical LEFT,
but app assigned R. Opposite-hand screenshot shows L after request for physical RIGHT;
explicit owner confirmation of the second check is pending. Current Practical15 uses
the inherited unmirrored-input reported-side swap. This needs isolation from classifier
quality and validation before HELLO. Setup also included timeout/incomplete events.

CURRENT_HYPOTHESIS=Tasks-to-anatomical side assignment is reversed for this physical
configuration; do not retrain or weaken the gate to compensate.

NEXT_ACTION=Get explicit physical-RIGHT confirmation for anatomy_check_screen.png;
then apply only the smallest Practical15-specific reported-side correction if confirmed,
add tests, rebuild/reinstall, recheck both hands, and run five HELLO attempts one at a
time. Raw top1 must be recorded even when rejected. Brief YES/NO controls if needed.

EVIDENCE_PATHS=reports/device_tests/samsung/hello_20260921/ (local, ignored; install,
device properties, startup/runtime logs, screenshots, command metadata and installed hash).

FILES_CHANGED=.gitignore; scripts_ml/106_samsung_qualification_evidence.py;
reports/fsl_dual_dataset_reset_v1/SAMSUNG_HELLO_QUALIFICATION_20260921.md; this handoff.

COMMANDS_TESTS=adb devices/install/am start/installed SHA/feature parity/TFLite parity
and live event inference PASS. No Android source change in this physical checkpoint;
existing120 JVM tests remain the installed checkpoint's offline evidence.

TRAINING_STATUS=NO_RETRAINING; DATASET_STATUS=UNCHANGED.

DO_NOT_MODIFY=Model/gates/labels/thresholds/debounce/UI/Avatar/StandardFSL105/Mapua14/
demo10; no profile fusion; no ASL contamination; analysis unmirrored; async non-default.
Never commit raw evidence or access the retired workspace. Physical success unproven.

[Physical qualification detail](fsl_dual_dataset_reset_v1/SAMSUNG_HELLO_QUALIFICATION_20260921.md)

## Current authority: offline recognition hardening — 2026-09-21

BRANCH=recognition/fsl-dual-dataset-reset-v1

SOURCE_BASE_COMMIT=a21c2114bd48e62e0d46ced3fd20400b440110bd

CHECKPOINT=Offline camera, raw Domain-C capture and isolated LIVE_STREAM hardening;
resolve exact checkpoint with git log for this file. Previous task snapshots below
are historical, not current execution instructions.

Completed:

- Fetched remotes; verified clean worktree and exact remote parity before edits.
- Audited CameraX -> upright unmirrored Tasks -> anatomy -> FullSign225 -> complete
  event -> resample48 -> TFLite -> gate; documented device-dependent assumptions.
- FIT_CENTER camera presentation, explicit display-only mirror toggle, Practical15
  sensor-matrix overlay, padded RGBA packing, lifecycle event reset/session guards,
  missing-camera and CameraState-error cleanup. No unrelated UI redesign.
- Debug opt-in Domain-C recorder: raw pre-normalization xyz/presence/handedness,
  camera metadata, completion/count/duration and actual canonical tensor SHA256;
  bounded asynchronous local storage, no pixels, no default capture.
- Debug-guarded isolated detectAsync adapter: exact hand/pose timestamps, bounded
  latest-frame admission, expiration and chronological pairing. Not wired into any
  profile; dedicated physical harness integration is the next experimental step.
- Rejection review separates closed-set raw classifier confidence from sign validity.
  CASH/COIN diagnostic false accepts remain unresolved physical evidence; no tuning.
- Cross-device checklist, external USB/UVC feasibility report and separate drawing
  communication backlog prepared. Neither optional feature was implemented.

Validation:

- Android testDebugUnitTest + assembleDebug PASS; 120 tests across 30 suites,
  including 9 pairing, 3 geometry, 3 RGBA packing and 4 capture/invariance tests.
- Python unittest discovery 34 PASS; standalone Android export comparison 2 PASS.
  Pytest is not installed; its two function-style export tests ran via their existing
  standalone entry point. No package upgrade was needed.
- Python temporal golden fixture PASS, max position difference 0.
- Practical15 source/APK model+labels hashes PASS; desktop TFLite golden top1 COIN,
  max probability difference 2.7284841053187847e-11. No Android execution claim.
- Native APK audit: 32 libraries/4 ABIs; ZIP 16-KiB alignment PASS. Arm64 PT_LOAD
  alignment PASS; TFLite armeabi-v7a/x86/x86_64 still 4-KiB aligned. General 16-KiB
  compatibility is NOT certified. Physical and RELRO/runtime checks remain.
- Tracked model assets, Practical15 runtime/gates, StandardFSL105/Mapua14 rollback
  runtime source and demo10 assets unchanged from base. Shared camera helpers and
  camera presentation changed; protected-lane visual smoke tests remain required.

MODEL_RETRAINED=NO

LIVE_STREAM_DEFAULT=NO

PHYSICAL_TESTS_PERFORMED=NO

AVATAR_MODIFIED=NO

No dataset, bulk tensor, device capture, APK, log, checkpoint weight or credential
belongs in this checkpoint. No retired workspace access.

Next exact action: when owner returns, detect the connected device, install the new
debug build, activate Practical15 with BOTH developer-diagnostics and profile extras,
verify startup parity and normal/fullscreen front/rear geometry, then anatomical
left/right. Enable Domain-C only with owner consent; request compact HOW_MANY,
HOW_MUCH, CASH (three trials each) before continuing the frozen 45/30 protocol.
Do not tune thresholds, retrain, or switch to LIVE_STREAM first.

Details:

- [Android audit and cross-device matrix](fsl_dual_dataset_reset_v1/ANDROID_CROSS_DEVICE_HARDENING.md)
- [Async experiment](fsl_dual_dataset_reset_v1/ASYNC_MEDIAPIPE_EXPERIMENT.md)
- [Domain-C enable/export protocol](fsl_dual_dataset_reset_v1/DOMAIN_C_CAPTURE_READINESS.md)
- [External camera feasibility](../docs/EXTERNAL_CAMERA_FEASIBILITY.md)
- [Drawing communication backlog](../docs/DRAWING_COMMUNICATION_BACKLOG.md)

## Archived: original-NPY matched-development comparison

TIMESTAMP=2026-09-21T00:40:00+08:00

BRANCH=recognition/fsl-dual-dataset-reset-v1

COMMIT=cbe56fcf plus matched development comparison pending

SOURCE_BASE_COMMIT=cbe56fcf

HANDOFF_UPDATE_COMMIT=PENDING_THIS_COMMIT

BRANCH_HEAD=cbe56fcf plus reviewed matched-development results

CURRENT_GOAL=Compare the supplied Mapua NPY-derived canonical representation against the current MP4/Holistic representation under an identical four-fold development-only RD-TCN48 experiment before making any Android candidate decision.

WORK_COMPLETED=

- Fetched all remotes and verified the requested branch was clean and exactly at
  remote HEAD `7e23c888f8c734a6f433a84cce081538b443117b` before work.
- Audited all 1,107 supplied Mapua NPY files: every file is finite
  `float64 [75,225]`, with no load failures or non-finite values.
- Added a versioned converter that validates paired MP4/NPY identity, derives
  motion from the NPY itself, uses the shared canonical FullSign225 builder,
  preserves anatomical slots, never mirrors, interpolates only bounded internal
  hand gaps, crops the complete event, and resamples to `float32 [48,225]`.
- Converted all 394 frozen Practical15 source clips into a separate safe-C
  experiment root with 0 failures; no source NPY was overwritten.
- Preserved the frozen partition, source group, and development fold for every
  paired MP4/NPY representation.
- Produced 394-row clip parity evidence and 15-class aggregates covering MAE,
  RMSE, Pearson, cosine, boundary/presence deltas, and trajectory motion.
- Added and validated the authoritative 105-row FSL-105 numeric-label manifest;
  no original folder was renamed and source quirks are preserved.
- Added a three-domain A/B/C Samsung parity plan without changing Android.
- Retrained Pipeline A and Pipeline B from scratch across the same four frozen
  development folds using identical RD-TCN48 code, seeds, augmentation, class
  weighting, optimizer, batch size, and early stopping.
- Selected original-NPY canonicalization as the stronger development pipeline:
  it improved combined OOF macro-F1, accuracy, weakest-class F1, ECE, and NLL.
- Verified all eight fold artifacts through an idempotent cached rerun.

FILES_CHANGED=

- training_configs/mapua_npy_canonical_v1.json
- scripts_ml/102_convert_mapua_original_npy_fullsign225.py
- scripts_ml/103_compare_mapua_npy_vs_mp4_development.py
- scripts_ml/104_build_fsl105_numeric_label_manifest.py
- tests/test_mapua_npy_converter.py
- reports/fsl_dual_dataset_reset_v1/MAPUA_NPY_CANONICAL_AUDIT.md
- reports/fsl_dual_dataset_reset_v1/MAPUA_NPY_CANONICAL_CLIP_METRICS.csv
- reports/fsl_dual_dataset_reset_v1/MAPUA_NPY_CANONICAL_CLASS_METRICS.csv
- reports/fsl_dual_dataset_reset_v1/FSL105_NUMERIC_LABEL_MANIFEST.csv
- reports/fsl_dual_dataset_reset_v1/THREE_DOMAIN_PARITY_PLAN.md
- reports/fsl_dual_dataset_reset_v1/MAPUA_NPY_VS_MP4_DEVELOPMENT_COMPARISON.md
- reports/fsl_dual_dataset_reset_v1/MAPUA_NPY_VS_MP4_CONFUSION_MATRIX.csv
- reports/CODEX_LIVE_HANDOFF.md

COMMANDS/TESTS=

- Remote/local parity and clean worktree gate: PASS.
- Original NPY whole-dataset contract scan: 1,107/1,107 load PASS.
- Converter/shared-builder unit tests: 7/7 PASS.
- Practical15 conversion: 394/394 PASS; 0 failures.
- Same-source A/B tensor comparison: 394/394 PASS.
- Private-path scan of proposed tracked artifacts: PASS.
- FSL-105 labels/train/test deterministic join: 105 IDs and 2,130 split rows PASS.
- Matched MP4/Holistic RD-TCN48: four development folds PASS.
- Matched original-NPY RD-TCN48: four development folds PASS.
- Idempotent comparison rerun: all 8 folds contract-matched and loaded from cache.

DATASET_STATUS=MAPUA_ORIGINAL_NPY_VERIFIED; NPY_CANONICAL_394_READY; FROZEN_334_DEVELOPMENT_60_SEALED_ASSIGNMENTS_PRESERVED; FSL105_NUMERIC_LABEL_MAP_READY.

TRAINING_STATUS=MATCHED_DEVELOPMENT_COMPARISON_PASS; ORIGINAL_NPY_WINS_OFFLINE_DEVELOPMENT. Existing Android-bound model remains unchanged and no new Android candidate was exported.

SAMSUNG_STATUS=UNCHANGED_FROM_PRIOR_CHECKPOINT. This experiment makes no new live or Android-domain claim.

METRICS=

- A-vs-B mean per-clip MAE=0.148218866995.
- A-vs-B mean per-clip RMSE=0.418502741.
- A-vs-B mean Pearson=0.933226864893.
- A-vs-B mean cosine=0.950076381426.
- Highest class mean MAE=AGAIN 0.476241396; CASH 0.288604481;
  HOW_MANY 0.205002830; THANK_YOU 0.219019907.
- Lowest class mean Pearson=AGAIN 0.833932212; CASH 0.856544278;
  HOW_MANY 0.879250279.
- Matched MP4 development OOF accuracy=0.955089820; macro-F1=0.950410728;
  weakest-class F1=0.812500000 HOW_MANY; ECE-10=0.018051216.
- Matched original-NPY development OOF accuracy=0.964071856;
  macro-F1=0.962949558; weakest-class F1=0.882352941 HOW_MANY;
  ECE-10=0.010011780.
- NPY-minus-MP4 macro-F1 delta=+0.012538830; weakest-class F1
  delta=+0.069852941.
- MP4 top confusion=HOW_MANY->COIN x3; NPY top confusion=AGAIN->DISCOUNT x2.

FAILURES=

- No conversion failures occurred.
- Supplied NPYs omit extractor version/settings, confidence, pose visibility,
  timestamps, and pixels, so exact MP4/Holistic tensor equality is impossible.
- A/B tensor similarity does not establish Android Tasks domain proximity.
- Mapua signer IDs remain unavailable; no signer-independent claim is allowed.
- Original-NPY improved offline development metrics but still has no Samsung
  Tasks-domain evidence and is not authorized to replace the current model.

CURRENT_HYPOTHESIS=Original-NPY canonicalization is the stronger offline development representation under the fixed comparison, particularly for HOW_MANY, but Android transfer remains unknown because Samsung MediaPipe Tasks domain C has not been captured. No model replacement is justified until A/B/C parity evidence exists.

NEXT_ACTION=On a separately authorized debug-only Android checkpoint, capture Samsung Tasks pose/hand landmarks before classifier normalization for the planned eight-concept diagnostic batch, then compare domain C against A and B before exporting or installing any NPY-derived candidate.

DO_NOT_MODIFY=

- Do not access the retired workspace drive.
- Do not overwrite original NPYs or commit generated tensors/checkpoints/caches.
- Preserve FSL_PRACTICAL15_V1, Standard FSL-105, Mapua14, demo10, and Avatar.
- Do not change Android runtime/profile/assets during this experiment.
- Do not use the sealed classifier test for preprocessing selection.
- Do not claim A or B is closer to Android before Samsung Tasks landmarks exist.

## Prior Samsung checkpoint

TIMESTAMP=2026-09-20T23:48:14+08:00

BRANCH=recognition/fsl-dual-dataset-reset-v1

COMMIT=c98953dd plus verified preview-mirror repair

SOURCE_BASE_COMMIT=6afa7322679317a0fac8b1bb0e66fe810ff99b10

HANDOFF_UPDATE_COMMIT=PENDING_THIS_COMMIT

BRANCH_HEAD=c98953dd plus verified preview-mirror repair

CURRENT_GOAL=Execute the frozen 45-positive and 30-negative Samsung battery after verified startup, camera, overlay, and anatomical-hand checks.

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
- Reproduced and fixed a display-only front-preview double mirror caused by a
  manual `PreviewView.scaleX=-1` on top of CameraX's native front mirror.
- Rebuilt, reinstalled, and physically verified that pose/hand skeletons now
  align with the visible signer while analysis/model input remains unmirrored.
- Preserved two pre-battery wrong accepts (`CASH` and `COIN`) as negative/OOD
  evidence without counting them in the frozen 30-trial negative battery.

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
- Physical camera/overlay verification: PASS after isolated display fix;
  anatomical L/R colors and skeletons align with the visible hands.

DATASET_STATUS=MAPUA_PUBLISHED_FSL_ONLY_INTERIM; 394 PASS clips; 334 development; 60 sealed; FSL105_PENDING_OFFICIAL_RAW; ASL_CONTAMINATION_QUARANTINED.

TRAINING_STATUS=OFFLINE_PASS. RD-TCN48, 125391 parameters, 15 classes, complete-event FullSign225 resample48. Signer-independent claim prohibited.

SAMSUNG_STATUS=STARTUP_PARITY_PASS; CAMERA_MIRROR_HANDNESS_PASS; INITIAL_BATTERY_PENDING. Device is authorized, the exact experimental profile is active, and the physically observed preview/overlay defect is repaired and verified. Positive/negative attempts remain 0/45 and 0/30.

METRICS=

- Development OOF accuracy=0.9491017964; macro-F1=0.9453016957; weakest F1=0.7878787879 HOW_MANY.
- Exploratory sealed accuracy=0.95; macro-F1=0.9557183557; weakest F1=0.8571428571 CASH.
- Sealed confusions: CARD->COIN x1; CASH->PROBLEM x1; NO->YES x1.
- Raw-video replay accuracy=0.95; cache max absolute difference=0.0; exact envelope matches=60/60.
- TFLite max probability difference=4.172325134277344e-07.
- Desktop replay TFLite median=0.9066 ms; p95=1.2948 ms.
- Provisional gate confidence=0.95, margin=0.05, motion mean-L2 floor=0.02.
- Installed APK SHA-256=fb39fbb1581c50bbba24935a09586e8ba6e847fe9acbc49b27c2b457f5257cf4.
- Model SHA-256=dfe78b557052032b2b288685b1de97108d0ddbb3516e3bdf758c0ef1c26219e6; labels SHA-256=4d3083b853d126c243b7f9a5db94be796a99a7bfe1379ef400d1d3f82a153cb6.
- Pre-battery negative diagnostics: 2/2 wrong accepted (`CASH` 0.99784863,
  `COIN` 0.9908304); these are not included in the formal negative rate.

FAILURES=

- Official FSL-105 v2 raw is absent; official automated acquisition is blocked by HTTP 403/Cloudflare.
- Mapua signer IDs are unavailable, so clip metrics cannot establish signer independence.
- The sealed raw replay repeats the same sealed source clips through the perception path; it is not a second independent dataset.
- Score-only rejection weakness is now visible live: two non-sign camera-check
  motions were wrongly accepted as `CASH` and `COIN` at frozen thresholds.
- Per-class Samsung generalization and formal battery latency are not yet measured.
- ADB entered `offline` repeatedly during early evidence collection. Normal
  server restarts plus physical cable/USB-mode remediation restored the device;
  the subsequent recordings, rebuild install, and installed-APK pull stayed online.

CURRENT_HYPOTHESIS=The installed practical15 profile now has exact package, feature, model, temporal, camera, overlay, and anatomical-hand parity. The two diagnostic wrong accepts make classifier/gate behavior on formal positives and negatives the decisive remaining gate; thresholds remain frozen until the complete battery.

NEXT_ACTION=Restart recognition with a clean log and execute three valid attempts each for HOW_MANY, HOW_MUCH, and CASH using hands-down neutral -> one sign once -> hands-down neutral -> wait for result/re-arm.

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
