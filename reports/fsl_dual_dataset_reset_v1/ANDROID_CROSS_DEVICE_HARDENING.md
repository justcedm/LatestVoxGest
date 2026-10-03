# Android cross-device hardening — 2026-09-21

Base: `a21c2114bd48e62e0d46ced3fd20400b440110bd`, clean local/remote parity
after fetch. Branch `recognition/fsl-dual-dataset-reset-v1`.
Scope: offline camera robustness, isolated Tasks experiment, opt-in raw capture and
compatibility/rejection review. **No device tests, no retraining, no model promotion.**

## Audited Practical15 pipeline

| Layer | Actual contract and finding |
|---|---|
| CameraX | Camera2-backed Preview + RGBA ImageAnalysis, requested 192x144 (actual negotiated dimensions may differ), KEEP_ONLY_LATEST, one analyzer executor. No assumption that request equals actual size. |
| Rotation | ImageProxy `rotationDegrees` rotates the full buffer upright; no horizontal ML mirror. Preview and analysis target display rotation at bind. Normal activity recreation rebinds after portrait/landscape changes. |
| Preview | One stable compatible PreviewView host across normal/fullscreen; now FIT_CENTER rather than crop-to-fill. CameraX owns rotation and native front mirror. User mirror-off applies exactly one explicit display scaleX; never touches ImageAnalysis. Letterboxing is intentional to retain signing space. |
| Overlay | Practical15 normalized upright points -> inverse quarter-turn -> original buffer -> inverse CameraX sensor-to-buffer -> current PreviewView sensor-to-view -> explicit user display toggle. Hide points if transform unavailable; do not guess a crop. Other preserved lanes retain size mapping, updated for FIT_CENTER. |
| Conversion | Packed RGBA fast path unchanged. Added stride-aware row/pixel packing for padded planes. YUV fallback is existing JPEG conversion and remains lossy; standard lane requests RGBA. |
| Tasks | Stable synchronous VIDEO HandLandmarker then PoseLandmarker on the same upright bitmap/timestamp. Explicit MPImage close; Tasks and interpreter closed on stop/error. |
| Anatomy | Existing unmirrored policy swaps reported Left/Right into anatomical slots; unknown category is unassigned. No image-X fallback in Practical15. No change to side policy. Physical one-hand verification is still mandatory. |
| Features | Raw pose99 + anatomical left63 + right63 -> frozen StandardFullSign225 normalization, no slot swap or model mirror. Raw capture occurs before this step. |
| Event | IDLE (3 neutral frames) -> PRIMING -> SIGN_ACTIVE -> CANDIDATE -> WAIT_FOR_RELEASE. Neutral means pose present, no detected hands, not merely low motion. Minimum8, max180 frames/8000ms, three missing-pose frames abort. Internal short hand gaps retained; terminal neutral frames excluded. |
| Temporal | Endpoint-aligned complete-event linear interpolation to exactly float32[48,225]. No repeated rolling-window vote. |
| Inference | Practical15 SHA-verified model/labels; float32[1,48,225] -> [1,15]; two interpreter threads. Startup feature and TFLite golden checks fail closed. |
| Gate | Raw top5/top1/confidence/margin logged before decision. Frozen .95 confidence/.05 margin/.02 motion and existing presence/re-arm rules. No changes. |

CameraX documents a current sensor-to-view transform including camera geometry but
excluding app scaleX; it may be null before preview layout/binding. This supports
mapping without assuming preview and analysis share resolution/crop.
[PreviewView API](https://developer.android.com/reference/androidx/camera/view/PreviewView).
FIT_CENTER retains the source aspect ratio and entire preview image.
[Preview scale types](https://developer.android.com/reference/androidx/camera/view/PreviewView.ScaleType).

## Device-dependent failure handling and remaining limits

- Missing requested camera: explicit `hasCamera` block; choose the other lens through
  existing UI. Never silently report front while using rear.
- Permission: existing runtime permission request/denied state retained. Provider/bind
  errors and observed CameraState errors mark recognition unavailable and clean up;
  existing restart action is available.
- Stop/start: Practical15 now closes/resets on lifecycle STOP and reloads on START if
  previously running. Session IDs invalidate late binds/accepted callbacks. Explicit
  stop/release prevents automatic resume. Interrupted events do not cross lifecycle gaps.
- Process recreation: normal app initialization + parity checks run again; no partial
  event persisted. Experimental profile activation still requires the existing debug
  intent policy. Revalidate actual process-restart behavior physically.
- Analyze failure resets partial collector state, rather than leaving CANDIDATE stuck.
- Low memory: latest-only CameraX, bounded event frames, bounded capture writer and
  async images limit growth. No intentional OOM/thermal stress or low-memory device
  test was run. Errors surface as blocked evidence; memory exhaustion is not certified.
- Source timestamps: VIDEO clamps regressions to last+1 ms. Async experiment instead
  drops regressions. Dropped frames change temporal sampling density; no nominal FPS
  guarantee. Existing end-to-result logs subtract camera timestamps from elapsed clock;
  trust that latency only after proving the device's timebase/acquisition-age evidence.
- Preview FIT_CENTER preserves the preview stream; it cannot expand a sensor crop chosen
  by the device. Verify both analysis coverage and displayed signing space on each device.
- Full 180-degree rotation without activity recreation and vendor display quirks remain
  physical test items; no orientation configuration override was introduced.
- StandardFSL105/Mapua14 historical controllers still contain their own preview scale
  assignments. Their protected runtime source was not rewritten in this task. Camera
  presentation smoke tests on those rollback lanes are explicitly required; the new
  sensor-matrix overlay is Practical15-specific.

## Build/native compatibility

minSdk26; targetSdk34; compileSdk34; AGP8.5.2; Java17; CameraX1.4.1;
Tasks0.10.35; TFLite2.17.0. No dependency or SDK version changed. APK packages 32 native
libraries across arm64-v8a, armeabi-v7a, x86, x86_64.

Read-only APK audit and SDK zipalign: all uncompressed native ZIP offsets pass 16-KiB
alignment. All arm64 PT_LOAD alignments pass. TFLite JNI PT_LOAD remains 4096 for
armeabi-v7a, x86 and x86_64. Do not claim general 16-KiB compatibility, especially for
x86_64 emulation. Static segment/ZIP checks do not certify RELRO or runtime behavior.
Retain dependencies tonight; qualify a separate runtime upgrade only with golden and
physical regression evidence. No ABI was silently removed.

Android requires both appropriate native packaging and compatible prebuilt libraries;
AGP alone is not proof. Device/emulator testing is still required.
[Official 16-KiB guidance](https://developer.android.com/guide/practices/page-sizes).

## Regression evidence

- Baseline: testDebugUnitTest + assembleDebug PASS (101 existing tests).
- Hardening: JVM suite and assembleDebug PASS; final count recorded in live handoff.
- Initial hardening tests: 9 exact-pair cases, 3 camera geometry cases, 2 capture schema/hash
  cases; additional stride tests cover row padding, pixel padding and truncated buffers.
- Python unittest discovery: 34 PASS, including shared FullSign225 five-case Android
  golden fixture, anatomical slots and mirrored-input fail-closed tests.
- `101_verify_fsl_temporal_fixture.py`: PASS, maximum position difference 0.
- `105_verify_android_offline_contract.py`: Practical15 source/APK model and labels
  hash PASS; [1,48,225]/[1,15] float32; golden top1 COIN, max probability difference
  2.7284841053187847e-11 on desktop TFLite. This is not Android execution evidence.
- Frozen model SHA256: `dfe78b557052032b2b288685b1de97108d0ddbb3516e3bdf758c0ef1c26219e6`.
- Frozen labels SHA256: `4d3083b853d126c243b7f9a5db94be796a99a7bfe1379ef400d1d3f82a153cb6`.
- All tracked model assets and protected runtime/profile files compared with base.
  No Avatar, Listen assets, model, vocabulary or threshold edits.

## Physical matrix (all pending for this build)

| Device | Startup / camera matrix | Recognition / resource matrix | Status |
|---|---|---|---|
| Current Samsung, discover serial | API/ABI/page size; hashes; front/rear; mirror on/off; normal/fullscreen; portrait/landscape; each anatomical hand | Frozen 45 positive + 30 negative; Domain-C export; timing; home/resume; reopen | NOT TESTED |
| Another modern Android OEM | Same matrix, actual negotiated resolution/crop; sensor rotation; 16-KiB if available | Same battery; permission denial/regrant; camera busy; process restart; 10-minute thermal run | NOT TESTED |
| Older/slower API26+ device | Supported ABI/native initialization; missing-front case if applicable | Frame density, event timeout, p50/p95, memory pressure, release/re-arm | NOT TESTED |

For every row: inspect raw classifier output separately from gate outcome. Count no
inference and negative false accepts; never silently exclude failures from rates.
First check camera/orientation/anatomy without linguistic trials, then a compact batch.

## Recognition safety review

Prior diagnostic non-sign CASH/COIN accepts at approximately .99785/.99083 demonstrate
that high softmax is not sign validity. Those observations are not a completed scored
negative battery. Do not infer model accuracy or calibrate a new threshold from them.

Current gap: any detected hand can start an event; returning hands out of frame can
complete one. Random movements can satisfy duration/presence/motion gates. A supported
class posterior is conditional on the trained classes, not an OOD detector.

Prepared experimental options (none enabled):

1. Event structure: entry/active/release durations, motion spread and active fraction,
   flag implausibly abrupt entry/exit; test against legitimate fast/static FSL motions.
2. Trajectory quality: wrist continuity/identity jumps, hand-scale extremes, missingness
   bursts and pose anchoring. Separate tracking failure from low classifier confidence.
3. Motion/timing: normalized velocity/acceleration and time distribution rather than
   a single minimum scalar; do not reject a legitimate low-motion sign blindly.
4. Prototype/OOD distance: fit class-specific pooled canonical trajectory/embedding
   prototypes on development training folds only; evaluate held-out source groups and
   separately collected negatives. Calibrate distance per representation, not against
   unrelated models' softmax scores. Domain-C evidence must precede adoption.

Required evidence before any change: complete frozen positive/negative battery; report
raw accuracy, accepted accuracy, wrong accepts, rejected-correct and no-inference rates,
per-class outcomes, and timebase-valid p50/p95. Tracking/capture failure gets repaired at
that layer. Repeated clean-event wrong raw predictions establish classifier/domain-shift
investigation, not automatic retraining. Keep release/re-arm and default gates frozen.

Related: [async experiment](ASYNC_MEDIAPIPE_EXPERIMENT.md),
[capture readiness](DOMAIN_C_CAPTURE_READINESS.md),
[external cameras](../../docs/EXTERNAL_CAMERA_FEASIBILITY.md),
[drawing backlog](../../docs/DRAWING_COMMUNICATION_BACKLOG.md).
