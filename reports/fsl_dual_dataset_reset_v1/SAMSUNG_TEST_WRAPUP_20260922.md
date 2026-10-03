# VoxGest Samsung testing wrap-up for ChatGPT - 2026-09-22

## Bottom line

Owner stopped testing because progress was confusing and recognition was not working
reliably. Reliable HELLO recognition was NOT established. No completed five-attempt
controlled HELLO battery or three-consecutive-accept milestone exists. Do not describe
this session as successful physical qualification. Final logs were saved and the
diagnostic app was force-stopped. No further physical actions are requested.

Repository: justcedm/LatestVoxGest.
Branch: recognition/fsl-dual-dataset-reset-v1.
Code/install checkpoint: 6965d713168d62a444a525dd1c6a94d16f94f3d0.
Pre-wrap documentation HEAD: d010246813a3f1815be64c7333ea7ba7b31cf9d7.
This report is committed in the subsequent documentation-only checkpoint.

## Confirmed engineering facts

- Samsung SM-A566B, Android16/API36, arm64-v8a; USB authorized.
- Updated debug APK installed successfully; installed/build SHA256 match:
  8286cf010d4e9223db9c68c779317635c271cbf8bb5e12708e783ca698f464d2.
- Active runtime FSL_PRACTICAL15_V1; model input[1,48,225], output[1,15].
- Labels: HELLO, THANK_YOU, YES, NO, PLEASE, HOW_MUCH, CASH, CARD, RECEIPT,
  WAIT, HOW_MANY, AGAIN, PROBLEM, COIN, DISCOUNT.
- Feature golden parity PASS maxerror0; TFLite golden PASS expected/actual COIN,
  max probability difference1.1920929E-7. These are fixture checks, not live accuracy.
- 129 JVM tests across31 suites passed; debug build passed.
- Front camera, landmarks and event inference run. Model input stays unmirrored.
- Unchanged confidence.95, margin.05, motion meanL2.02, minimum8rawframes,
  maximum8000ms, existing pose/hand gates and release/re-arm behavior.
- No retraining, model/label substitution or threshold/debounce changes.

## Confirmed defect and installed changes

Owner explicitly verified the old code assigned physical LEFT to R and RIGHT to L.
The targeted fix removes the inherited reported-side swap only for Practical15 at
one Tasks-to-anatomical boundary. Canonical225 order remains pose0..98, left99..161,
right162..224. Unknown handedness is unassigned; no image/X flip or second swap.
Nine deterministic tests were added. Shared canonicalizer/other-profile policy and
OneHand162 were not changed.

Fullscreen preview alone now uses aspect-preserving FILL_CENTER; mini layout remains
unchanged. Existing display mirror setting is ON and landmark display is enabled.
Logs show reported left->left/right->right; owner replied CHECK DONE but did NOT
explicitly confirm physical L/R indicators after the fix. Therefore anatomical and
fullscreen visual PASS are still unproven, despite implemented code changes.

The existing isolated LIVE_STREAM experiment was wired behind a debug opt-in sentinel.
It stayed inactive. VIDEO_DEFAULT was used. Physical A/B was NOT completed.

## Saved event evidence, not an accuracy table

Expected gestures were not consistently confirmed and events were not a completed
controlled battery. Event IDs can restart when the camera/controller restarts.

| Date/time | Raw top1 | Probability | Gate | Raw frames / duration | End-to-raw |
|---|---|---|---|---|---|
| Sep21 20:17:07 | CASH | .99757093 | ACCEPT | 27 / 3.263s | 719ms |
| Sep22 13:29:17 | CARD | .98868203 | ACCEPT | 34 / 5.783s | 916ms |
| Sep22 13:32:01 | PROBLEM | .6892044 | LOW_CONFIDENCE | 61 / 7.433s | 653ms |
| Sep22 13:33:02 | DISCOUNT | .80464745 | LOW_CONFIDENCE | 25 / 3.216s | 962ms |
| Sep22 13:36:47 | DISCOUNT | .9692462 | ACCEPT | 10 / 1.408s | 959ms |
| Sep22 13:37:26 | HELLO | .9134132 | LOW_CONFIDENCE | 15 / 1.809s | 585ms |
| Sep22 13:38:16 | DISCOUNT | .6387303 | LOW_CONFIDENCE | 43 / 5.360s | 770ms |
| Sep22 13:40:31 | PROBLEM | .549086 | LOW_CONFIDENCE | 9 / 1.065s | 662ms |

The owner confirmed pre-fix CASH was unintended: definite false accept. Post-fix
CARD/DISCOUNT have EMIT logs but intended actions and audible TTS are unconfirmed;
neither is a successful HELLO. The single raw HELLO is not a verified correct trial.
Its top5: HELLO .9134132, CARD .040901717, COIN .01719468, NO .00580324,
CASH .005668343. Margin .8725115; pose15/15, left15/15, right0, motion1.0217956;
MediaPipe median123.651ms/p95138.703ms; TFLite.765196ms. Gate rejected it because
.9134132 < .95. This does not justify lowering the gate, especially given wrong or
unintended high-confidence accepts elsewhere.

Multiple events timed out at the unchanged8s limit or were incomplete. Without
ground-truth action/time boundaries, they cannot all be assigned to a model defect.

## Temporal findings and unresolved layers

Model48 means48 resampled trajectory points, NOT48 camera frames or a fixed6s window.
Live analyzer samples were approximately8-12fps, sometimes lower during events.
Completed-event durations varied1.065-7.433s in the table. Training extracts complete
motion envelopes from25fps source videos before resample48; audited development
spans were.16-2.96s. Longer live events suggest a temporal mismatch to investigate,
not proof that temporal normalization is broken. Neutral dominance was not measured.

Unresolved: physical post-fix anatomy, controlled gesture identity, tracking continuity,
event completion/neutral release, training-vs-device trajectory/domain parity, and
rejection of non-sign motion. It is premature to label the model broken or the gate
too strict. Repeated wrong raw predictions on clean, confirmed signs were not proven.

## Missing qualification and preservation

No formal45-positive/30-negative battery, no five-controlled-HELLO result, no YES/NO
control, no physical A/B, no valid aggregate accuracy/negative false-accept rate or
median/p95 result latency. No DEMO_READY claim. No Domain-C comparison completed.

Preserved model/gates/labels, StandardFSL105, Mapua14, demo10, Avatar and mini-camera
layout. No retired workspace access. Raw screenshots/logs/APK are excluded from Git.
Local evidence: reports/device_tests/samsung/hello_20260921/ and
reports/device_tests/samsung/anatomy_temporal_fix_20260921/ including
check_done_log_03, wrapup_final_runtime and wrapup_stop_app with command metadata.

## Request for the next reviewer

Review this evidence before proposing another change. Do not claim success, lower
thresholds or retrain from these unscored events. If the owner resumes, first agree
on one simple, ground-truth-labelled test protocol, confirm anatomy once, and capture
individual complete events with raw landmarks/timestamps for failure localization.
Testing is paused by owner; do not automatically resume or request more signs.
