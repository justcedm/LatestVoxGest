# VoxGest — 4-Day Recognition Engineering Sprint Prompt

**Issued:** 2026-09-16
**Branch:** `recognition/mapua14-rescue-v1`
**Primary objective:** make the Mapúa-trained classifier and Samsung runtime operate on the **same semantic/temporal feature distribution**, prove live behavior on the physical Samsung, and ship the strongest defensible recognition path in four days without corrupting Standard FSL-105.

---

You are the dedicated VoxGest **recognition rescue engineer**. Treat this as an emergency, evidence-driven systems sprint. Do not optimize for pretty offline metrics. Optimize for **correct live behavior on the connected Samsung**.

## NON-NEGOTIABLE SAFETY

- Work only on `recognition/mapua14-rescue-v1` unless explicitly told otherwise.
- Never access/write the retired D: workspace. Safe C: only.
- Preserve Standard FSL-105, Legacy Demo, Avatar, Listen, and UI styling.
- Do not overwrite `fsl_fullsign225_20f_105_v1` or its labels.
- Preserve canonical unmirrored FullSign225: pose99 | anatomical-left63 | anatomical-right63.
- No silent mirroring, no left/right slot swap.
- Preserve `(System.nanoTime() / 1_000_000L)`.
- Never force-push or rewrite history.
- Raw Samsung captures, videos, features, checkpoints, APKs, caches, and secrets remain outside Git.
- Do not claim signer-independent accuracy from Mapúa because signer IDs are unavailable.
- Do not call a build or offline test a live success.

## READ FIRST

Read completely before changing code:

- `reports/CODEX_LIVE_HANDOFF.md`
- `reports/mapua14_rescue_v1/SUMMARY.md`
- `reports/mapua14_rescue_v1/training_results.json`
- `training_configs/mapua14_rescue_v1.json`
- `scripts_ml/fullsign225_feature_builder.py`
- `scripts_ml/94_prepare_mapua14_rescue.py`
- `scripts_ml/95_train_mapua14_rescue.py`
- `android_dry_run/app/src/main/java/com/voxgest/dryrun/Mapua14RescueRuntime.kt`
- `android_dry_run/app/src/main/java/com/voxgest/dryrun/Mapua14RescueCameraRecognitionController.kt`
- `android_dry_run/app/src/main/java/com/voxgest/dryrun/StandardFslCameraRecognitionController.kt`
- `docs/ARCHITECTURE_DECISIONS.md`

Current audited baseline:
- 367 PASS Mapúa clips / 14 classes.
- RD-TCN48 winner, 125,326 params.
- development macro-F1 ≈ 0.9824.
- sealed clip accuracy ≈ 0.9821; macro-F1 ≈ 0.9796.
- TFLite parity PASS.
- physical Samsung live performance NOT YET TESTED.

## CRITICAL ENGINEERING HYPOTHESIS TO TEST

Training uses a **complete detected sign-motion trajectory resampled to 48 normalized temporal positions**.

The Android rescue runtime currently uses a **48-frame rolling landmark window** before inference.

Those are not automatically equivalent distributions. A 48-frame live rolling buffer can contain preparation, neutral frames, partial signs, stale frames, or a different real-time duration than the training trajectory. Therefore:

> **Do not retrain blindly until training/runtime temporal parity is measured.**

The first major question is whether the classifier is bad live, or whether the runtime is feeding it tensors that do not resemble the tensors used during training.

# PHASE 1 — CONNECT SAMSUNG AND FREEZE THE BASELINE

1. Verify repository branch, HEAD, working tree, remotes.
2. Run unit tests and `assembleDebug` before live changes.
3. Confirm Samsung appears in `adb devices -l` and is authorized.
4. Install the exact current debug APK.
5. Launch the experimental lane with BOTH required extras:
   - diagnostics = true
   - profile = `MAPUA14_RESCUE_V1`
6. Prove on-device feature golden parity and TFLite golden parity before testing signs.
7. Record Samsung model, Android version, branch, HEAD, build variant/hash.

Do not change thresholds yet.

# PHASE 2 — RAW LIVE BASELINE FIRST

Run the original qualification before any rescue tuning:

- 14 signs × 5 valid attempts = 70 sign trials.
- Negative categories: no hands, idle body, open palm, random waving, touching face, pointing, hand entering/leaving, partial sign, natural gesturing.

For every sign attempt record:
- expected label
- raw top1 label + probability
- raw top3
- margin
- pose presence ratio
- left/right/any-hand presence ratio
- collected frame count
- actual window duration ms
- median/max frame gap
- event state/reason
- gate final accept/reject + reason
- latency
- whether final accepted label was correct.

Report independently:
- `RAW_TOP1_CORRECT`
- `GATE_ACCEPTED_CORRECT`
- `WRONG_ACCEPTED`
- `REJECTED`
- negative false accepts.

Never let the acceptance gate hide classifier quality.

# PHASE 3 — ADD DEBUG-ONLY RUNTIME FEATURE CAPTURE

If raw live accuracy is not clearly healthy, implement a **debug-only Samsung capture path** for MAPUA14_RESCUE_V1. Do not modify Standard behavior.

For each labeled attempt, export to safe C: via ADB/app external files:

1. exact chronological raw FullSign225 frames before fixed-length normalization;
2. timestamp per frame;
3. pose/left/right presence flags;
4. motion/activity score and event-state transitions;
5. start/end frame chosen as sign-motion envelope;
6. exact final 48×225 tensor sent to TFLite;
7. expected label, top-k result, margin, gate result;
8. camera lens + analysis orientation; preview mirror is display-only;
9. build/branch/model hash/label hash.

Use a versioned schema and atomic file writes. Never commit capture payloads.

# PHASE 4 — PROVE DATASET ↔ RUNTIME FEATURE PARITY

Build a comparison script that consumes:
- Mapúa training tensors generated by the frozen extractor; and
- Samsung debug capture tensors generated by Android.

Measure and report by class and globally:

- pose / left-hand / right-hand / any-hand missingness;
- frame-gap/time-span distributions;
- sign-motion duration;
- coordinate mean/std/range for pose, left hand, right hand;
- wrist-relative hand scale distribution;
- hand-to-face / hand-to-torso position distributions;
- per-frame displacement and velocity magnitude;
- fraction of near-zero/static frames;
- start/end neutral contamination;
- tensor L2 magnitude distribution;
- classifier pre-softmax/logit distribution if accessible;
- penultimate embedding distance/prototype distance if practical.

Also take at least several Samsung-recorded trial videos and run the Python extractor offline on the same clips. Compare the resulting temporal envelope and normalized tensor against the Android-produced tensor. Exact float equality is not required across different MediaPipe implementations, but **orientation, anatomical slots, scaling, motion envelope, missing-data policy, and temporal coverage must be semantically equivalent**.

Explicitly test for:
- accidental front-camera mirroring in ML input;
- anatomical left/right inversion;
- pose normalization mismatch;
- hand scale mismatch;
- Android collecting only a partial sign;
- Android collecting too much neutral time;
- 48 live frames spanning much longer/shorter physical time than the training motion trajectory.

# PHASE 5 — FIX TEMPORAL DOMAIN MISMATCH BEFORE RETRAINING

If the major mismatch is temporal coverage, replace the experimental rescue lane’s naive fixed rolling-frame semantics with **complete-sign event capture + normalized resampling**, while keeping Standard untouched.

Target behavior:

`IDLE -> PRIMING -> SIGN_ACTIVE -> CANDIDATE -> ACCEPTED -> WAIT_FOR_RELEASE -> IDLE`

During `SIGN_ACTIVE`:
- collect the actual usable motion trajectory with timestamps;
- preserve only a small defensible neutral boundary comparable to training;
- use short-gap interpolation only under the same policy as training;
- at sign completion / sufficient stable envelope, resample the COMPLETE trajectory to exactly 48 positions;
- infer on `[1,48,225]`;
- prevent duplicate emission until release/re-arm.

Do not simply wait for exactly 48 raw camera frames. Temporal normalization must represent the complete semantic motion, not camera cadence.

Add deterministic JVM tests using synthetic timestamps/trajectories proving:
- fast and slow versions of the same normalized trajectory yield comparable 48-position sampling;
- partial sign does not silently become a full sequence;
- neutral-only input does not become a valid sign event;
- release/re-arm behavior works;
- frame gaps and missing-hand policy remain bounded.

# PHASE 6 — DECISION GATE AFTER PARITY FIX

Re-run the same Samsung live set.

### Case A — raw top1 >= 80% and wrong-accepted <= 10%
Classifier is viable. Do NOT retrain immediately. Tune rejection/event logic only using negatives and validation captures, then rerun a held-out live session.

### Case B — raw top1 roughly 50–80%
Proceed to targeted Samsung domain adaptation.

### Case C — raw top1 < 50%
Do not threshold-game. First produce a class-level failure analysis. If tensors remain mismatched, keep fixing preprocessing. If tensors are well matched but class separation is poor, escalate data/model architecture.

# PHASE 7 — TARGETED SAMSUNG DOMAIN ADAPTATION, ONLY IF NEEDED

Create a separate experimental profile, never overwrite rescue v1.

Suggested profile ID:
`MAPUA14_SAMSUNG_ADAPT_V1`

Capture new Samsung data using the SAME runtime feature contract that will be used at inference.

Minimum fast rescue target, if physically feasible:
- 10 clean labeled repetitions/class for adaptation = 140 positive trials.
- additional negative/non-sign captures across all prescribed categories.
- record at least two separate sessions/lighting/background states if time allows.

Do NOT random-split individual repeated takes from the same burst across train/test.
Use session-aware separation:
- Adaptation/train session(s)
- Later sealed Samsung validation session not used for optimization.

Training strategy:
- keep Mapúa PASS data as source-domain anchor;
- mix source + Samsung adaptation data with balanced class sampling;
- prevent Samsung repetitions from dominating only because they are recent;
- compare at least:
  1. frozen RD-TCN48 baseline;
  2. RD-TCN48 fine-tune with mixed Mapúa + Samsung data;
  3. if needed, modest feature/noise/temporal augmentation matched to measured Samsung differences.
- do not add huge architecture complexity unless the measured evidence requires it.

If personal/device calibration materially improves results, label it honestly as a calibrated profile. Do not present it as signer-independent generalization.

# PHASE 8 — NEGATIVE / OOD ENGINEERING

A 14-way softmax will always choose a class. `confidence` alone is not an OOD detector.

Use negative captures to evaluate:
- confidence
- top1-top2 margin
- temporal stability
- hand/pose quality
- activity envelope quality
- logit energy or embedding/prototype distance if practical.

Implement only evidence-supported rejection rules. `NOTHING` must remain rejection state, not a vocabulary class.

Required final negative metric:
`FALSE_ACCEPT_RATE = negative false accepts / valid negative attempts`

Provisional rescue target: <= 5%.

# PHASE 9 — FINAL FOUR-DAY PRODUCT GATE

A profile can be considered the demo rescue foundation only if all are true on a later physical Samsung session:

- raw top1 correct >= 80%
- wrong accepted <= 10%
- negative false accept rate <= 5%
- no persistent left/right/mirror mismatch
- no obvious temporal partial-sign bug
- repeat/release logic works
- TFLite/device latency is acceptable
- Standard FSL-105 rollback remains intact
- build/tests pass

If these are not achieved, do not fabricate success. Report the exact bottleneck and select the strongest honest fallback profile for the demo.

# FOUR-DAY EXECUTION PRIORITY

**Day 1:** Samsung baseline + runtime capture instrumentation + parity measurements.

**Day 2:** temporal/runtime parity fix; rerun physical baseline; only then decide whether adaptation is needed.

**Day 3:** if required, Samsung-domain adaptation + OOD calibration + Android integration.

**Day 4:** frozen build, later-session physical qualification, regression tests, documentation, backup/rollback proof. No late architecture experiments unless current path is mathematically dead.

# CHECKPOINT DISCIPLINE

At every meaningful checkpoint:
- update `reports/CODEX_LIVE_HANDOFF.md`;
- update `docs/ARCHITECTURE_DECISIONS.md` only for actual decisions;
- add concise evidence reports under `reports/mapua14_rescue_v1/`;
- explicit staging only;
- run `git diff --cached --check`;
- commit and push;
- verify remote branch HEAD.

Checkpoint immediately before long training, before strategy changes, after live qualification, and before stopping.

# REQUIRED FINAL RETURN FORMAT

`SPRINT_STATUS=`
`BRANCH=`
`HEAD=`
`SAMSUNG_CONNECTED=YES/NO`
`BASELINE_70_ATTEMPTS_COMPLETE=YES/NO`
`BASELINE_RAW_TOP1_CORRECT=`
`BASELINE_RAW_TOP1_RATE=`
`BASELINE_WRONG_ACCEPTED=`
`BASELINE_REJECTED=`
`BASELINE_NEGATIVE_ATTEMPTS=`
`BASELINE_FALSE_ACCEPTS=`
`TEMPORAL_TRAIN_RUNTIME_PARITY=PASS/FAIL/NOT_TESTED`
`MAJOR_DOMAIN_MISMATCHES=`
`RUNTIME_CAPTURE_SCHEMA=`
`TEMPORAL_NORMALIZATION_CHANGED=YES/NO`
`ADAPTATION_REQUIRED=YES/NO`
`ADAPT_PROFILE=`
`FINAL_LIVE_RAW_TOP1_RATE=`
`FINAL_WRONG_ACCEPT_RATE=`
`FINAL_NEGATIVE_FALSE_ACCEPT_RATE=`
`FINAL_PROFILE=`
`STANDARD_FSL105_PRESERVED=YES/NO`
`BUILD_TEST_STATUS=`
`MAPUA_RESCUE_FOUNDATION=PASS/FAIL/NOT_YET_TESTED`
`BLOCKERS=`
`NEXT_EXACT_ACTION=`
`HANDOFF_UPDATED=YES/NO`

The priority is not to maximize offline score. The priority is to make the **dataset preprocessing, temporal representation, Android feature tensor, classifier input, live event segmentation, and rejection logic describe the same physical signing event**.