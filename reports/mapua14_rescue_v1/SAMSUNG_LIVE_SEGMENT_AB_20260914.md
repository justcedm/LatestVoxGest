# Samsung OLD rolling48 vs NEW segmented/resampled48 A/B - 2026-09-14

Status: OLD series stopped after five scored attempts; NEW five-attempt physical
qualification is prepared and awaiting the user.

## Fixed experiment boundary

- Device target: Samsung SM-A566B (`R5GYC0M1M4P`).
- OLD profile: `MAPUA14_RESCUE_V1` rolling latest 48 frames.
- NEW profile: `MAPUA14_LIVE_SEGMENT_V1` completed event trajectory followed by
  audited linear resampling to exactly 48 FullSign225 frames.
- Classifier asset is unchanged in both lanes; SHA-256
  `f850c5d414c5c253ef9131bae5a85bb3ed5ad5412abdf9936df510c6ec043dcc`.
- Label asset is unchanged; SHA-256
  `af398236fd62da6c5bafbe0b60d21bc8a155c48aeb45987090c1b14a20cb9ef0`.
- Confidence, margin, tracking-quality, duration, and freshness thresholds are
  frozen for this comparison. No retraining or threshold tuning is permitted.
- Raw classifier correctness and acceptance-gate performance are reported
  separately.

## Series-1 baseline

The preserved Series-1 log contains four HELLO windows. All four raw top-1
predictions were HELLO at approximately 0.999999 confidence, all four were
rejected for `TEMPORAL_STABILITY`, and each rolling window spanned about
5.8-6.0 seconds. Thus the directly evidenced primary failure is correct raw
classification blocked by the old temporal gate; rolling48 versus the training
complete-trajectory distribution is the architectural contributor under test.

## Build evidence

- `:app:compileDebugKotlin`: PASS after diagnostic-only timestamp/log fields.
- Six relevant segmentation/gate/hand/camera/mirror suites were force-rerun:
  24 tests, 0 failures, 0 errors, 0 skipped.
- Full `:app:testDebugUnitTest`: PASS after a forced rerun: 115 tests across
  30 suites, 0 failures, 0 errors, 0 skipped.
- `:app:assembleDebug`: PASS after a forced rebuild.
- Prepared debug APK SHA-256:
  `bdf8c859b9b71d84e6afa00ce6b16a548b9a908e027a4c9dfb00204554504fc5`.
- Feature/TFLite parity is fail-closed at NEW-profile startup and must be
  captured again from the installed Samsung log.

## HELLO results

| Metric | OLD rolling48 | NEW segmented/resampled48 |
|---|---:|---:|
| Attempts | 5; series stopped | PENDING; test 5 first |
| Raw top-1 correct | 3/5 attempts | PENDING |
| Accepted correct | 3/5 attempts | PENDING |
| Wrong accepted | 0/5 scored; separate canceled positioning false accepts exist | PENDING |
| Attempts with no inference | 2/5 | PENDING |
| Correct raw windows rejected | 3 `TEMPORAL_STABILITY` windows before later acceptance | PENDING |
| Tracking/readiness failures | Attempts 1 and 5 | PENDING |
| Exact end-of-sign to first raw | NOT_CAPTURED | PENDING; instrumented |
| Exact end-of-sign to accepted | NOT_CAPTURED | PENDING; instrumented |
| User-observed result delay | Attempt 3 ~40 s; Attempt 4 ~15 s; Attempt 5 >60 s/no result | PENDING |
| Median/P95 result latency | NOT_CAPTURED | PENDING; instrumented |

The detailed reconstruction, including every requested field and evidence hash,
is in `SAMSUNG_OLD_1_5_DIAGNOSTIC_20260914.md`. Every scored OLD inference that
ran predicted HELLO correctly, while warmed TFLite latency was below 1 ms. The
dominant evidenced failure is pre-inference readiness/landmark/event-gate
instability and rolling-window formation, not classifier execution time.

## Distance observation

Attempt 4 at a qualitatively closer distance correlated with higher median
MediaPipe throughput (`10.785` versus Attempt 3 `9.889` FPS) and lower median of
reported MediaPipe total-median snapshots (`91.565` versus `107.824` ms). But
Attempt 5 at the same reported closer distance produced no inference, with 588
`WAITING_FOR_NEUTRAL_RELEASE` and 487 `IDLE_NO_LANDMARKS` events.

`DISTANCE_EFFECT_CLASSIFICATION=G_MIXED` for supported temporal-gate and camera
pipeline effects. The causal proximity mechanism is `H_INSUFFICIENT_EVIDENCE`
because physical distance, hand bounding size, per-frame confidence, and
per-frame left/right presence were not recorded.

## NEW runtime audit

- Required state flow is present: IDLE -> ARMING -> CAPTURING -> FINALIZING ->
  INFERENCE -> WAIT_FOR_RELEASE -> IDLE.
- Frames must be strictly chronological. Finalization occurs before inference.
- The adaptive complete motion envelope is linearly resampled to exactly
  `[1,48,225]`; no first-48/latest-48 selection exists in the NEW path.
- Dynamic motion, static hold, neutral-return, and capture-limit completion
  paths exist. Capture-limit events reject rather than impersonating a valid
  completion.
- `WAIT_FOR_RELEASE` requires a neutral release before the same sign can re-arm.
- Anatomical hand identity uses reported side, bounded wrist continuity, and
  pose-wrist anchors. Ambiguous collision/reacquisition fails closed; image-X
  crossing does not assign slots.
- Preview mirroring is display-only. MediaPipe analysis and FullSign225 input
  are canonical/unmirrored.
- Bounded one-to-three-frame internal gaps are interpolated only because this is
  the audited training preprocessing policy. Raw presence ratios are computed
  before interpolation, so interpolation cannot make gate quality appear
  better than observed tracking.
- RD-TCN48 SHA-256 remains
  `f850c5d414c5c253ef9131bae5a85bb3ed5ad5412abdf9936df510c6ec043dcc`.
- Label SHA-256 remains
  `af398236fd62da6c5bafbe0b60d21bc8a155c48aeb45987090c1b14a20cb9ef0`.

Diagnostic logging now records frame/process state timestamps, sign start,
estimated sign end, completion detection, resampling completion, result time,
captured/envelope/resampled frame counts, raw top-1/top-2/top-3/top-5,
probabilities, margin, gate outcome/reason, raw presence ratios, hand-identity
diagnostics, MediaPipe performance, TFLite latency, end-to-raw latency, and
end-to-accepted latency. The required `(System.nanoTime() / 1_000_000L)` clock
expression is preserved.

## Prepared NEW physical protocol

1. Install the prepared APK and launch with diagnostics plus exact profile
   `MAPUA14_LIVE_SEGMENT_V1`.
2. Capture startup feature parity, TFLite parity, model/label/profile identity,
   camera source, and mirror status before a scored attempt.
3. Establish one consistent user distance/framing and record it qualitatively.
4. For each of five HELLO attempts: neutral -> one deliberate HELLO -> neutral
   -> wait. Do not repeat inside an attempt.
5. Reset the app/composer between attempts so accepted words cannot accumulate.
6. Stop if NEW repeatedly exceeds 10 seconds or fails frequently; diagnose by
   raw result, finalization, tracking, gate, and scheduling layer before tuning.
7. Continue to ten NEW HELLO attempts only if the first five clearly improve on
   OLD. No 14-class expansion occurs before that decision.

No live recognition fix is claimed. No NEW physical attempt has been performed.

## Device preparation status

After the successful rebuild, `adb devices -l` returned an empty inventory.
Installation and NEW-profile startup were therefore not performed. The APK and
protocol are ready locally, but device readiness remains pending Samsung USB/ADB
reconnection and authorization.
