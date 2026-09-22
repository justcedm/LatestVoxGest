# VoxGest capstone deadline freeze

Last updated: 2026-09-11 Asia/Manila

## Verdict at freeze preparation

`DEMO_READY=NO`

The installed Standard FSL-105 runtime is build-clean, parity-clean, and fail-closed for the
qualified negative cases. The requested short physical sign pilot and duplicate-event trial are
not yet complete, so no safe sign list is claimed. Retraining was not run.

## Build, install, and runtime contract

- Authoritative workspace: `C:\VOXGEST_RECOVERY_20260910\New_VovGest_GIT\android_dry_run`.
- JVM suite: 24 suites, 89 tests, 0 failures, 0 errors.
- `assembleDebug`: PASS.
- Installed on Samsung `R5GYC0M1M4P` (`SM_A566B`): PASS.
- APK bytes: `159736074`.
- APK SHA-256: `99F4BBCDCF1973EA0E4BC47FD7F6B0B99C155C1E094DE8BDF281DFEB665357F1`.
- App launch: PASS.
- Active model: `model/fsl_fullsign225_20f_105_v1/voxgest_fsl_fullsign225_105_float32.tflite`.
- Active profile: `fullsign225_20f_v1` / `STANDARD_FSL_FULLSIGN225`.
- Runtime shape: `[1,20,225] -> [1,105]`; label count `105`.
- `DEMO_ALLOWLIST_APPLIED=false`; legacy OneHand162 fallback preserved but not loaded.
- Front preview mirrored for the signer; analysis/model input remains canonical unmirrored.
- Protected clock remains exactly `val now = (System.nanoTime() / 1_000_000L)`.

## Device parity

- FullSign225 feature parity: PASS; maximum absolute error `0.0`; anatomical hand slots are not
  swapped.
- TFLite golden parity: PASS; expected/actual top-1 `BROWN` at index `9`; maximum probability
  difference `5.8619776E-14` on the latest device run.
- Model SHA-256: `42D040EC2269D437546D327DECAACA32839ABDD6BB63B2D400063C90630E5D13`.
- Labels SHA-256: `BFA76D96ED10BF97F43CA80BCFCC5BADD3E96DF7EBE4C0654FC078552DA55FB6`.

## Negative qualification

| Scenario | Semantic accepts | Result | Exact evidence |
|---|---:|---|---|
| No person / no hands | 0 | PASS | `IDLE_NO_LANDMARKS`, buffer `0/20`, no inference |
| Normal idle | 0 | PASS | no accepted semantic token |
| Centered open palm held still | 0 | PASS after repair | `WAITING_FOR_NEUTRAL_RELEASE`, buffer `0/20`, no inference |
| Random wave | not completed | PENDING | waiting for a clean hands-down setup |

The first open-palm attempts exposed real false accepts (`SON`, `SLOW`, and `BLIND`) and were not
hidden. The focused repair now requires a sustained pose-present, hands-down neutral release before
an event can arm; a pre-held raised palm and temporary hand-detector dropouts cannot arm it. The
final centered-palm screenshots visibly show a detected/aligned hand while the accepted-sign card
remains `Waiting for an accepted sign`.

Evidence:

- `reports/evidence/voxgest_negative_no_person_20260911.png`
- `reports/evidence/step_1c_open_palm_centered_midpoint_20260911.png`
- `reports/evidence/step_1c_open_palm_centered_final_20260911.png`

## Physical performance observed

Representative centered-palm samples with landmark overlay on:

- `CAMERA_SENSOR_FPS=9.922-9.962`
- `MEDIAPIPE_FPS=8.361-8.603`
- `CAMERA_TO_LANDMARK_MEDIAN_MS=230.443-230.993`
- `CAMERA_TO_LANDMARK_P95_MS=270.473-271.548`
- `MEDIAPIPE_TOTAL_MEDIAN_MS=111.226-111.288`
- TFLite was intentionally not invoked for the rejected open-palm negative.

CameraX uses latest-frame backpressure. No duplicate perception pipeline or visualization-only Face
Mesh was added.

## Composer and Listen audit

- Standard accepted results bypass the legacy demo allowlist and enter `TokenComposer` verbatim.
- `GOOD MORNING` followed by `MOTHER` can be retained in order, but the current frozen presenter has
  no Filipino mapping for that pair; `Magandang umaga, Nanay.` is not proven and is not fabricated.
- Listen currently resolves exact single utterances `HELLO`, `MILK`, or `RICE`. Combined spoken
  `hello milk` resolves unavailable.
- Existing Samsung evidence proves separate `HELLO -> neutral -> MILK` actions in one retained GLB
  renderer with no reload. This is selector/action evidence, not combined-STT evidence.

## Exact deployed FSL-105 model metrics

Source: `app/src/main/assets/model/fsl_fullsign225_20f_105_v1/runtime_manifest.json`.

```text
SELECTED_ARCHITECTURE=rdtcn
HELD_OUT_TEST_ACCURACY=0.9364375461936437
HELD_OUT_TEST_MACRO_PRECISION=0.9410390743481
HELD_OUT_TEST_MACRO_RECALL=0.9409048770197285
HELD_OUT_TEST_MACRO_F1=0.9385262654349393
TOP CONFUSIONS=NOT RECOVERED IN AUTHORITATIVE C: TREE
TRAIN/VAL/TEST COUNTS=NOT RECOVERED IN AUTHORITATIVE C: TREE
SIGNER_OVERLAP_COUNT=NOT RECOVERED IN AUTHORITATIVE C: TREE
SOURCE_VIDEO_OVERLAP_COUNT=NOT RECOVERED IN AUTHORITATIVE C: TREE
TFLITE_TOP1_AGREEMENT=1.0
TFLITE_MAX_PROBABILITY_DIFFERENCE=1.430511474609375e-06
```

The referenced final report
`reports/fullsign225_training/sept5_emergency_retry1/selected_winner_test_and_tflite.json` is absent
from the recovered C: tree. Legacy 64-class reports were not substituted.

## Recovery and stability

- Post-Phase-1 gate patch: `reports/FSL105_EVENT_GATE_DEADLINE_FREEZE_20260911.patch`.
- Patch SHA-256: `56A39D1787D69BED7C097B9938D8039F17A5C4C5B096C79158E1F4C6BCE4F09D`.
- The patch was generated with an alternate Git index; the real index was not changed.
- No recent `FATAL`, `ANR`, `SIGSEGV`, or `OOM` was observed on the Samsung.
- `RETRAIN_REQUIRED=NO`: the current blocker is incomplete live qualification/event framing, not
  post-parity proof of systematically wrong raw top-1 predictions.

## Remaining deadline gate

1. Complete random-wave rejection with zero semantic tokens.
2. Capture the short pilot: `HELLO`, `NO`, `MILK`, `RICE`, `KNOW`, `GOOD MORNING`, `MOTHER`.
3. Prove held `KNOW` emits once, then neutral/release and a second `KNOW` emits exactly once.
4. Only list signs as safe when their physical expected/raw/decision evidence passes.
