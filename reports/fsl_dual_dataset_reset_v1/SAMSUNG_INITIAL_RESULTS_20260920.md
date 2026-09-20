# Samsung initial qualification results — 2026-09-20

STATUS=STARTUP_PARITY_PASS; PHYSICAL_BATTERY_NOT_STARTED

BRANCH=recognition/fsl-dual-dataset-reset-v1

AUTHORITY_HEAD=39c9f21dbc9702942e90b3e6171bb4d4af43ff07

DEVICE=Samsung SM-A566B

DEVICE_SERIAL=R5GYC0M1M4P

## Startup and installation gate

| Check | Result | Evidence |
|---|---|---|
| ADB authorization | PASS, with two recovered transient offline incidents | `adb devices -l` returned `device`; normal ADB server restarts restored both drops |
| JVM/build | PASS | 26 suites, 101 tests, zero failures/errors/skips; debug APK assembled |
| APK install | PASS | streamed install succeeded |
| Installed APK identity | PASS | 161,514,586 bytes; SHA-256 `260f79ae1f968c39dde061ba511035c443e5832a1a93525a31505b97cf8a39a6` |
| Active profile | PASS | `FSL_PRACTICAL15_V1`; debug-intent-only; production default unchanged |
| Model asset | PASS | SHA-256 `dfe78b557052032b2b288685b1de97108d0ddbb3516e3bdf758c0ef1c26219e6` |
| Label asset | PASS | SHA-256 `4d3083b853d126c243b7f9a5db94be796a99a7bfe1379ef400d1d3f82a153cb6` |
| APK asset parity | PASS | all five practical-profile entries byte-identical to source assets |
| Feature parity | PASS | FullSign225 golden fixture; max absolute error 0; fixed anatomical slots; no hand swapping |
| TFLite parity | PASS | expected/top-1 `COIN`; max probability difference `1.1920929E-7` |
| Tensor contract | PASS | input `[1,48,225]`; output `[1,15]`; 15 labels |
| Temporal contract | PASS | complete event followed by exact resample48 |
| Frozen gate | PASS | confidence `0.95`; margin `0.05`; motion mean-L2 `0.02` |
| Frozen capture | PASS | minimum 8 raw frames; maximum 8000 ms; existing pose/hand and release/re-arm behavior |
| Camera binding | PASS | actual lens FRONT; preview mirrored; analysis unmirrored; model input unmirrored |
| Live approval/default | PASS | `live_approved=false`; Android production default unchanged |

The first screenshot after camera start showed the preview streaming but pointed
at an empty ceiling/wall. Therefore framing, pose tracking, anatomical-left/right
overlay identity, and physical mirror perception are intentionally still
PENDING; no semantic sign attempt has been counted.

Raw logcat, screenshots, and the pulled installed APK are retained outside Git.
No recording, APK, device capture, or private local path is committed.

## Frozen initial battery status

- Positive valid classifier trials: `0/45`.
- Negative trials: `0/30`.
- Threshold changes: none.
- Retraining: none.
- Raw correct rate: pending.
- Accepted correct rate: pending.
- Wrong accepted rate: pending.
- Negative false-accept rate: pending.
- Tracking failure rate: pending.
- Median/p95 end-to-result latency: pending.

## Next exact action

Place the phone upright and the signer centered with head, torso, elbows, and
both hands visible. Perform only the neutral anatomical-left/right diagnostic.
After visual and log verification, begin the frozen positive battery in the
specified order.
