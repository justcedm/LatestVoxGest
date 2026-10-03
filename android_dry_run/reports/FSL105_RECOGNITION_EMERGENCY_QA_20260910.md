# FSL-105 recognition emergency QA

Last updated: 2026-09-11 00:59 Asia/Manila

## Current verdict

`VOXGEST_COMMUNICATION_PIPELINE_REVIEW_REQUIRED`

The rebuilt Standard runtime passes unit, assembly, artifact, feature-parity, and TFLite-parity gates on Samsung. The no-person negative passes. Hand-present negatives and deliberate FSL trials still require an assisted signer in front of the physical device; communication changes remain gated until those recognition checks pass.

`RETRAIN_REQUIRED=NO`

There is no post-parity physical evidence of systematically incorrect raw top-1 labels, so retraining is not authorized.

## Build and install

- JVM/unit suite: 24 suites, 84 tests, 0 failures, 0 errors.
- `assembleDebug`: PASS.
- Installed with `adb install -r`: PASS on `R5GYC0M1M4P` (`SM_A566B`).
- APK bytes: `159,736,074`.
- APK SHA-256: `14FCE991BAB4A353AC455054FC11827081E92921A1071D86FC83F3A8B0E12315`.
- Protected clock line remains exactly `val now = (System.nanoTime() / 1_000_000L)`.

## Physical Samsung runtime contract and parity

- Active profile: `STANDARD_FSL_FULLSIGN225`.
- Model input/output: `[1,20,225] -> [1,105]`.
- Labels: 105; class order verified.
- Legacy OneHand fallback: preserved but not loaded.
- FullSign225 feature parity: PASS; maximum absolute error `0.0`; anatomical hand slots not swapped; inference input unmirrored.
- TFLite parity: PASS; top-1 index `9`, label `BROWN`; maximum probability difference `5.8619776E-14`; device inference `1.054609 ms`.
- Model SHA-256: `42D040EC2269D437546D327DECAACA32839ABDD6BB63B2D400063C90630E5D13`.
- Labels SHA-256: `BFA76D96ED10BF97F43CA80BCFCC5BADD3E96DF7EBE4C0654FC078552DA55FB6`.

## Negative safety

| Scenario | Duration/evidence | Semantic accepts | Result |
|---|---:|---:|---|
| No person / no hands (camera facing empty ceiling) | 15 seconds; screenshot and UI hierarchy below | 0 | PASS |
| Open idle palm | Awaiting assisted physical action | — | PENDING |
| Hand held still | Awaiting assisted physical action | — | PENDING |
| Random waving | Awaiting assisted physical action | — | PENDING |
| Incomplete/partial movement | Awaiting assisted physical action | — | PENDING |
| Hand enters frame | Awaiting assisted physical action | — | PENDING |
| Hand leaves frame | Awaiting assisted physical action | — | PENDING |

No-person behavior remained `IDLE`, with exact rejection `IDLE_NO_LANDMARKS`, empty top-5 because inference was intentionally not invoked, buffer `0/20`, and “Waiting for an accepted sign” in the normal user UI.

Evidence:

- `reports/evidence/voxgest_negative_no_person_20260911.png`
- `reports/evidence/voxgest_negative_no_person_20260911.xml`

## Post-hardening physical performance (overlay on, no-person run)

Steady sample:

- `CAMERA_SENSOR_FPS=10.873`
- `MEDIAPIPE_FPS=10.886`
- `CAMERA_TO_LANDMARK_MEDIAN_MS=169.780`
- `CAMERA_TO_LANDMARK_P95_MS=244.217`
- `CONVERSION_MEDIAN_MS=19.858` (`p95=26.323`)
- `HAND_TASK_MEDIAN_MS=35.913` (`p95=51.407`)
- `POSE_TASK_MEDIAN_MS=26.092` (`p95=42.965`)
- `MEDIAPIPE_TOTAL_MEDIAN_MS=82.575` (`p95=113.556`)
- `OVERLAY_FPS=10.886`
- `DISPLAY_VSYNC_FPS=60.081`
- Live TFLite/window timing: not invoked during a no-hand negative by design.

The runtime prioritizes latest frames through CameraX `KEEP_ONLY_LATEST`. No additional perception pipeline or visualization-only Face Mesh is running.

## Stability

No `FATAL`, `ANR`, `OOM`, or `SIGSEGV` appeared in the captured parity/no-person validation logs.

## Pending physical gate

1. Complete the six hand-present negative scenarios and confirm zero accepted semantic tokens.
2. Run the listed FSL pilot, three attempts per sign where practical, capturing expected/raw top-2/confidence/margin/decision/final token/window/tracking fields.
3. Verify one accepted sign produces one token, held repetition cannot spam, and release/re-arm permits one later token.
4. Measure overlay on versus off during hand-present inference.
5. Only after those gates pass, implement and validate the bounded bilingual composer and Listen multi-action queue.
