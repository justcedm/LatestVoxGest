# Installed Practical15 anatomy correction - 2026-09-22

Owner confirmed pre-fix physical LEFT -> R and RIGHT -> L. Incidental CASH .99757093
was unintended: confirmed false accept, not recognition success.

## Correction and preservation

Only Practical15 opts into Tasks reported Left -> anatomical-left and Right ->
anatomical-right; unknown -> unassigned. No image mirror, X flip or second swap.
Python training uses unmirrored video/Holistic anatomical outputs. Shared225
normalization remains pose0..98, left99..161, right162..224. Other profiles retain
their extractor policy; all models/labels/gates unchanged.

Fullscreen alone uses aspect-preserving FILL_CENTER; mini retains FIT_CENTER and
existing layout/dimensions. Overlay uses the preview sensor transformation. Current
front-display mirror preference is ON; analysis stays unmirrored. Landmark display
was enabled through existing UI. Screenshot shows pose alignment; physical hand and
fullscreen verification remain pending. Earlier mirror-OFF preference is historical.

## Saved build/install/runtime proof

- 129 JVM tests/31 suites, zero failures/errors; testDebugUnitTest/assembleDebug PASS.
- Build and installed APK SHA256:
  `8286cf010d4e9223db9c68c779317635c271cbf8bb5e12708e783ca698f464d2`.
- Install Success; cold activity launch OK; Samsung SM-A566B authorized.
- ACTIVE_PROFILE=FSL_PRACTICAL15_V1; VIDEO_DEFAULT;
  handedness=TASKS_REPORTED_ANATOMICAL_V1.
- Feature golden PASS maxerror0; TFLite golden PASS expected/actual COIN,
  maxdifference1.1920929E-7. Input[1,48,225], output[1,15].
- Logged labels: HELLO, THANK_YOU, YES, NO, PLEASE, HOW_MUCH, CASH, CARD, RECEIPT,
  WAIT, HOW_MANY, AGAIN, PROBLEM, COIN, DISCOUNT.
- Model SHA256 `dfe78b557052032b2b288685b1de97108d0ddbb3516e3bdf758c0ef1c26219e6`;
  labels SHA256 `4d3083b853d126c243b7f9a5db94be796a99a7bfe1379ef400d1d3f82a153cb6`.
- Frozen confidence.95/margin.05/motion.02/minimum8raw/max8000ms;
  arm3/release3 and existing pose/hand .65 gates unchanged.

The settings ACTIVE LAUNCHER card describes the packaged default, not the debug
override. Runtime logs prove the active profile. No settings UI change was made.

## Temporal interpretation

48 model frames are resampled points from a completed event, not48 camera arrivals.
Event duration=(last-first timestamp)/1000; sample FPS=(raw_count-1)/duration.
There is no fixed six-second window inferred from48/8. Terminal release frames are
removed before resampling. Internal hand absence does not itself prove neutrality.

Training decoded all source frames, interpolated bounded internal gaps up to3frames,
cropped the motion envelope with3 boundary frames, and resampled the trajectory48.
Audited334 development sources are25fps; selected spans range.16-2.96seconds.
Current startup analyzer samples9.909-11.701fps, MediaPipe medians84.116-86.358ms.
Controlled live duration/neutral fraction remain unmeasured.

A=VIDEO_DEFAULT. B=debug-only app-private practical15_live_stream.enabled sentinel;
both feed identical collector/model/gate. B is physically UNTESTED, remains opt-in,
and uses exact-timestamp hand/pose pairing with bounded admission. Restore A after B.

## Private evidence and next gate

Ignored root: reports/device_tests/samsung/anatomy_temporal_fix_20260921/.
Saved install_anatomy_fix, launch_fixed_v1, installed_fixed_hash,
fixed_startup_runtime, fixed_camera_screen, anatomy_recheck_clear plus metadata.
No APK, device log or screenshot is tracked.

Requested LEFT-only2s -> neutral3s -> RIGHT-only2s -> neutral10s. Owner response and
interpretation pending. Controlled HELLO0/5, YES/NO untested. Next five HELLO events
use the published project reference, individually marked; record rawtop5, scores,
margin, presence, motion, quality/state/reason, latency and UI/TTS. No success claim.
## Unscored post-install observations through 13:33:58

Saved anatomy_recheck_log_01/02. Owner physical-hand confirmation still pending;
these are NOT controlled HELLO trials. Event numbering restarts with controller.

| Time | Raw top1 | Probability | Margin | Frames / seconds | Gate | End-to-raw ms |
|---|---|---|---|---|---|---|
| 13:29:17 | CARD | .98868203 | .981793 | 34 / 5.783 | ACCEPT | 916 |
| 13:32:01 | PROBLEM | .6892044 | .39903963 | 61 / 7.433 | LOW_CONFIDENCE | 653 |
| 13:33:02 | DISCOUNT | .80464745 | .6442541 | 25 / 3.216 | LOW_CONFIDENCE | 962 |

CARD has an EMIT log; intended action and audible TTS are unconfirmed. Preserve as
an unintended-output candidate, not success. Several held-hand events hit the
unchanged8s timeout and released/re-armed; incomplete events also occurred. Logged
Tasks sides now map left->left and right->right, but mapping logs alone cannot prove
physical handedness. Human identity remains necessary. No scored neutral trial or
physical A/B outcome is claimed. Current hand-free intervals contain no EMIT, but
one incomplete event at13:33:27 prevents treating the whole interval as clean idle.
