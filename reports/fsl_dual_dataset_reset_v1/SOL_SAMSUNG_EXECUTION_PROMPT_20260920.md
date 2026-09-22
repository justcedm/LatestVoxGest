# SOL — SAMSUNG PHYSICAL QUALIFICATION + EVIDENCE-DRIVEN REPAIR

**Date:** 2026-09-20  
**Branch:** `recognition/fsl-dual-dataset-reset-v1`  
**Required starting HEAD:** `457f2be306fe4490ccc29e8c2c757b6b5ae62fe0` or a later legitimate commit on this branch.  
**Active profile:** `FSL_PRACTICAL15_V1`  
**Device:** Samsung is now physically connected and available.  
**Authority:** execute this prompt together with `reports/fsl_dual_dataset_reset_v1/SAMSUNG_QUALIFICATION_PROTOCOL.md`.

## Mission

Move the current model from “offline strong” to an evidence-based live-device decision.

Do NOT start by retraining.

First determine exactly whether failures originate from:
A. device/profile/startup parity;
B. camera/mirroring/handedness;
C. MediaPipe tracking;
D. complete-event segmentation/resampling;
E. classifier raw top-1;
F. acceptance/rejection gate;
G. latency/release-rearm behavior.

Only repair the layer proven to be failing.

## 1. Preflight

1. Fetch remote and checkout `recognition/fsl-dual-dataset-reset-v1`.
2. Verify HEAD and clean worktree.
3. Read fully:
   - `reports/CODEX_LIVE_HANDOFF.md`
   - `reports/fsl_dual_dataset_reset_v1/OFFLINE_FINAL_SUMMARY.md`
   - `reports/fsl_dual_dataset_reset_v1/FINAL_MODEL_RESULTS.json`
   - `reports/fsl_dual_dataset_reset_v1/SAMSUNG_QUALIFICATION_PROTOCOL.md`
   - `docs/SYSTEM_ENGINEERING_EVOLUTION_20260920.md`
4. Run `adb devices -l`.
5. If exactly one authorized Samsung is present, use its detected serial. Do not hard-code an old serial if the current serial differs.
6. If unauthorized: stop only for the owner to accept the USB debugging fingerprint, then continue automatically.
7. If offline: restart adb and retry the safe cable/device checks from the qualification protocol.
8. Do not access D:.

## 2. Reconfirm build before install

Run relevant unit tests and debug build.

Required:
- practical15 tests PASS;
- full Android JVM suite remains green;
- assembleDebug PASS.

Do not modify code just because a build warning exists. Only blocking failures matter.

## 3. Install and activate the exact experimental profile

Install the existing debug APK and launch with:
- developer diagnostics enabled;
- recognition profile = `FSL_PRACTICAL15_V1`.

Do NOT change the normal production/default profile.

Immediately capture logcat evidence proving:
- ACTIVE_PROFILE=FSL_PRACTICAL15_V1;
- model load PASS;
- TFLite parity PASS;
- input [1,48,225];
- output [1,15];
- complete_event_resample48;
- canonical unmirrored ML input;
- live_approved=false;
- Android default unchanged;
- current camera lens;
- preview mirror state;
- analysis_mirrored=false.

If any hash/shape/label/parity/orientation check fails, stop physical sign testing and fix that exact defect first.

## 4. Camera + handedness sanity gate

Before classification trials:
- visually verify preview orientation;
- verify anatomical left/right overlay/tracking follows the signer;
- confirm analysis coordinates stay unmirrored;
- verify MediaPipe receives usable pose + hand landmarks;
- record approximate live MediaPipe latency and frame cadence.

Run one neutral movement diagnostic without accepting a semantic token.

If left/right or mirroring is wrong, fix that before any model conclusions.

## 5. Initial 45-positive battery — frozen parameters

Do NOT change thresholds during this battery.

Use:
- confidence 0.95;
- margin 0.05;
- min raw event frames 8;
- max event duration 8000 ms;
- min trajectory motion mean L2 0.02;
- existing pose/hand checks;
- existing release/re-arm.

Attempt rule:
`hands-down neutral -> one sign -> hands-down neutral -> wait for result/re-arm`

Run 3 valid attempts each, exact order:
1. HOW_MANY
2. HOW_MUCH
3. CASH
4. CARD
5. COIN
6. NO
7. YES
8. HELLO
9. THANK_YOU
10. PLEASE
11. RECEIPT
12. WAIT
13. AGAIN
14. PROBLEM
15. DISCOUNT

For EVERY attempt record:
- expected;
- attempt number;
- event state path;
- event duration;
- raw frame count;
- pose presence;
- left/right/any-hand presence;
- trajectory motion;
- top5;
- raw top1;
- probability;
- margin;
- final accept/reject;
- rejection reason;
- MediaPipe latency;
- TFLite latency;
- sign-end-to-result latency;
- visible framing/tracking issue.

Never count an invalid tracking attempt as a classifier miss. Record it separately as a tracking/capture failure and repeat only enough to obtain the prescribed number of valid classifier attempts.

Never hide wrong raw top1 behind rejection.

## 6. Initial 30-negative battery

Run 5 attempts each:
1. neutral/no sign;
2. open palm held;
3. random wave;
4. partial sign stopped early;
5. hand enters/leaves without sign;
6. body movement with no deliberate sign.

Record every inference and every accepted semantic token.

Do NOT tune thresholds until the whole fixed positive + negative battery is complete.

## 7. Compute diagnostic metrics

After the first fixed battery calculate:
- raw top1 correct / valid positive attempts;
- accepted correct rate;
- wrong accepted rate;
- correct raw predictions rejected;
- no-inference/tracking-failure rate;
- per-class 3/3, 2/3, <=1/3;
- negative false-accept rate;
- median/p95 end-to-result latency;
- A-F failure categories from the existing protocol.

Produce:
`reports/fsl_dual_dataset_reset_v1/SAMSUNG_INITIAL_RESULTS_20260920.md`
and a compact CSV.

Keep raw logs/captures outside Git.

## 8. Decision tree — repair only what evidence proves

### Case A: startup/parity/profile failure
Fix bundle/routing/hash/shape only.
Rebuild.
Repeat startup gate.
Do not retrain.

### Case B: tracking/mirror/handedness/event failure
Fix camera/MediaPipe/event logic only.
Use debug tensor/state evidence.
Rebuild.
Retest affected signs.
Do not retrain first.

### Case C: raw top1 is correct but gate rejects repeatedly
Tune gate only after the complete fixed battery.
Use development + live evidence.
Do not loosen a global threshold solely to save one weak class if it increases negative false accepts.

### Case D: clean event + good landmarks + wrong raw top1
This is classifier/domain generalization failure.
For repeated weak classes:
1. compare live tensor statistics against training-class tensors;
2. inspect hand slot occupancy, event duration, trajectory scale, motion envelope;
3. determine whether error is domain shift or class confusion;
4. if required, capture targeted Samsung examples ONLY for weak classes;
5. keep calibration session distinct from final qualification;
6. retrain/adapt the smallest necessary model lane;
7. do not contaminate sealed/offline test data.

### Case E: many classes fail cleanly
Do not individually patch 15 thresholds.
Investigate a shared train/runtime/domain mismatch first.

## 9. Targeted adaptation rules if needed

Only after the initial battery.

Capture labeled Samsung examples for weak classes with:
- same canonical FullSign225 builder;
- session ID;
- attempt index;
- timestamps;
- no mirrored tensor;
- no raw media committed to Git.

Include representative non-sign events for rejection analysis.

Preserve Mapua as the source anchor.

Do not train solely on one burst of owner recordings and then call the same burst a test.

If final model becomes owner/device-calibrated, explicitly label it:
`SAMSUNG_CALIBRATED_DEMO_PROFILE`
and prohibit signer-independent claims.

## 10. Final qualification after repairs are frozen

Once code/model/gates are frozen:
- rebuild and reinstall;
- start a fresh qualification session;
- 5 valid attempts per supported concept;
- at least 30 fresh negative attempts.

Preferred per-class DEMO_READY:
- >=4/5 raw top1 correct;
- zero wrong accepted for that class.

Stronger result:
- 5/5 raw top1 correct;
- zero wrong accepted.

Global targets:
- wrong accepted <=10%;
- negative false accepts <=5%;
- report latency.

A class below 4/5 stays CANDIDATE/BLOCKED.

Do not fabricate 15/15.

## 11. Practical vocabulary policy

Current 15:
HELLO, THANK_YOU, YES, NO, PLEASE, HOW_MUCH, CASH, CARD, RECEIPT, WAIT, HOW_MANY, AGAIN, PROBLEM, COIN, DISCOUNT.

If one class remains structurally weak after legitimate repair, do not endlessly overfit it.

A vocabulary swap is allowed only if:
- replacement is from authoritative published FSL data;
- it is more practical or equally useful;
- its offline + live evidence is stronger;
- the manifest/paper delta records the change.

## 12. FSL-105 boundary

Do not claim dual-source yet.

Official FSL-105 raw remains pending. The current live test is for the Mapua-only practical profile.

Do not mix historical OneHand162 FSL-105 features into this 48-frame FullSign225 model as a shortcut.

## 13. Git discipline

After:
1. startup/device parity;
2. initial battery;
3. each actual engineering repair;
4. final qualification;

update:
- `reports/CODEX_LIVE_HANDOFF.md`;
- concise reports under `reports/fsl_dual_dataset_reset_v1/`.

Commit source/tests/config/manifests/reports/small golden fixtures only.

Do not commit:
raw recordings, logcat dumps with private info, screenshots, APKs, large features, checkpoints, environments, secrets.

Never force push.

## 14. Protected assets

Do NOT modify unless the defect is directly in shared infrastructure and change is regression-tested:
- Standard FSL-105 model/profile;
- Mapua14 rescue bundle;
- demo10/manual5;
- Avatar;
- Listen animations;
- unrelated UI styling.

Preserve canonical monotonic timing behavior.

## 15. Required live handoff

Return/update:

`SAMSUNG_PHASE_STATUS=`
`BRANCH=`
`HEAD=`
`DEVICE_SERIAL=`
`ADB_STATUS=`
`ACTIVE_PROFILE=`
`STARTUP_PARITY=`
`CAMERA_MIRROR_HANDNESS=`
`INITIAL_POSITIVE_COMPLETE=YES/NO`
`INITIAL_RAW_TOP1_RATE=`
`INITIAL_ACCEPTED_CORRECT_RATE=`
`INITIAL_WRONG_ACCEPT_RATE=`
`INITIAL_NEGATIVE_FALSE_ACCEPT_RATE=`
`TRACKING_FAILURE_RATE=`
`MEDIAN_LATENCY_MS=`
`P95_LATENCY_MS=`
`GREEN_CLASSES=`
`YELLOW_CLASSES=`
`RED_CLASSES=`
`ROOT_CAUSE=`
`REPAIR_APPLIED=`
`FINAL_QUALIFICATION_COMPLETE=YES/NO`
`DEMO_READY_WORDS=`
`CANDIDATE_WORDS=`
`BLOCKED_WORDS=`
`STANDARD_FSL105_PRESERVED=YES/NO`
`MAPUA14_PRESERVED=YES/NO`
`AVATAR_MODIFIED=NO`
`NEXT_EXACT_ACTION=`

The governing rule is:

**Do not chase the output. Trace the pipeline.**
Source FSL -> MediaPipe landmarks -> complete event -> FullSign225 -> resample48 -> raw classifier -> gate -> user output.

Fix the first layer that breaks.
