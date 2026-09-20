# Samsung initial qualification results — 2026-09-20

STATUS=STARTUP_PARITY_PASS; CAMERA_MIRROR_HANDNESS_PASS; PHYSICAL_BATTERY_NOT_STARTED

BRANCH=recognition/fsl-dual-dataset-reset-v1

AUTHORITY_HEAD=39c9f21dbc9702942e90b3e6171bb4d4af43ff07

DEVICE=Samsung SM-A566B

DEVICE_SERIAL=R5GYC0M1M4P

## Startup and installation gate

| Check | Result | Evidence |
|---|---|---|
| ADB authorization | PASS after recovery | repeated early offline incidents were resolved by server restart plus physical cable/USB-mode remediation; later capture/install/pull remained online |
| JVM/build | PASS | 26 suites, 101 tests, zero failures/errors/skips; debug APK assembled |
| APK install | PASS | streamed install succeeded |
| Installed APK identity | PASS | 161,514,586 bytes; SHA-256 `fb39fbb1581c50bbba24935a09586e8ba6e847fe9acbc49b27c2b457f5257cf4` |
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
| Physical preview/overlay | PASS after repair | CameraX owns the front preview mirror; visible pose/hand and skeleton positions align |
| Anatomical hand identity | PASS | gold `L` and cyan `R` tracks remained distinct and aligned in the corrected preview |
| Live approval/default | PASS | `live_approved=false`; Android production default unchanged |

The first screenshot after camera start showed an empty ceiling/wall. The later
human-in-frame checks confirmed adequate centered upper-body/hand framing, pose
tracking, hand tracking, and the physical preview behavior. No semantic sign
attempt has been counted.

### Pre-battery camera diagnostic evidence

The first human-in-frame diagnostic proved pose and both hand detectors run on
the Samsung, but exposed a display-only double-mirror defect: the landmark
skeleton rendered on the side opposite the visible hand. CameraX already
mirrors the front-camera `PreviewView`; the practical controller applied a
second `scaleX=-1`, cancelling the preview mirror while the overlay mapper still
correctly expected a mirrored preview. The controller now leaves `PreviewView`
at `scaleX=1`; analysis coordinates and model input remain unmirrored. The
rebuild/reinstall repeat showed the skeleton aligned to the visible body and
hands, so the camera/mirror/handedness gate is PASS.

The same pre-battery open-palm movement also produced one wrong accepted result:
raw top-1 `CASH`, probability `0.99784863`, margin `0.99619764`, 36 frames over
5025 ms, exact resample48, trajectory motion `0.5665707`, and 807 ms
sign-end-to-result. This is preserved as diagnostic negative evidence but is
not counted in the frozen 30-trial negative battery.

The post-fix movement-only camera check produced a second wrong accepted result:
raw top-1 `COIN`, probability `0.9908304`, margin `0.98722863`, 47 frames over
5427 ms, exact resample48, trajectory motion `6.4202952`, and 670 ms
sign-end-to-result. It is also retained as pre-battery negative evidence and is
not counted in the frozen negative rate. The same diagnostic additionally
proved `INCOMPLETE_EVENT_REJECTED`, `EVENT_TIMEOUT`, release confirmation, and
re-arm transitions occur instead of hanging indefinitely.

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

Restart recognition with a clean log. Perform three valid attempts each for
`HOW_MANY`, `HOW_MUCH`, and `CASH`, in that exact order, using hands-down neutral
-> one sign once -> hands-down neutral -> wait for the result and re-arm.
