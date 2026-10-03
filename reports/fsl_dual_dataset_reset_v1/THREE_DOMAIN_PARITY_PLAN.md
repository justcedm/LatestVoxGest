# Three-domain landmark parity plan

## Decision this plan supports

Determine whether the deployed Android MediaPipe Tasks landmark domain is
closer to either published Mapua representation before choosing a new Samsung
candidate:

- **A — supplied NPY:** original Mapua `float64 [75,225]` raw landmarks.
- **B — Python Holistic:** same Mapua MP4 decoded by MediaPipe 0.10.9 Holistic.
- **C — Samsung Tasks:** live HandLandmarker and PoseLandmarker results captured
  before VoxGest spatial normalization.

No A-versus-B development result is sufficient to predict C. The Android model
and active profile remain unchanged until C is measured.

## Future Samsung capture contract

Instrumentation must be debug-only and must capture one deliberate sign per
event. It will log landmarks immediately after Tasks inference and before
`StandardFullSign225FeatureBuilder` normalization. It must not log camera pixels.

Each event record will contain:

- schema version, device model, Android build fingerprint, app commit;
- active recognition profile and expected label supplied by the operator;
- camera ID/lens facing, sensor orientation, display rotation, model-input
  mirroring state, and preview mirroring state;
- event ID, monotonic frame timestamp, event start/end timestamps, and frame
  count;
- pose `33x3`, anatomical-left hand `21x3`, anatomical-right hand `21x3`;
- explicit pose/left/right presence booleans and Tasks confidence scores;
- raw event duration, motion start/end, interpolation count, and resample indices;
- the resulting canonical `float32 [48,225]` tensor hash.

Anatomical slots must be assigned once from Tasks handedness in unmirrored model
coordinates. The capture must never infer an anatomical swap from preview
mirroring.

## Matched sampling

Start with the eight diagnostic concepts `HOW_MANY`, `HOW_MUCH`, `CASH`, `CARD`,
`COIN`, `NO`, `YES`, and `HELLO`, then cover all Practical15 concepts. Capture
at least five complete events per concept and 30 negative/non-sign events using
the frozen runtime thresholds. Every event follows:

`hands-down neutral -> one sign once -> hands-down neutral -> wait for re-arm`

Do not tune thresholds during collection. Preserve rejected events because
domain measurements are independent of gate acceptance.

## Measurements

Compute the following for A, B, and C before and after canonical normalization:

1. Coordinate distributions by landmark block and axis: min, max, mean,
   standard deviation, p01/p05/p50/p95/p99, and out-of-frame rate.
2. Pose scale: shoulder width, hip width, shoulder-to-hip distance, and their
   frame-to-frame coefficient of variation.
3. Hand scale: wrist-to-middle-MCP distance per anatomical slot before scaling.
4. Pose, left-hand, right-hand, either-hand, and both-hands presence rates;
   internal gap counts and maximum gap lengths.
5. Left/right slot occupancy and unexpected slot transitions within an event.
6. Raw and canonical trajectory motion: mean, median, p95, maximum frame-delta
   L2, wrist path length, hand-center path length, and static-to-active ratio.
7. Event duration, source frame count, motion-envelope start/end, retained-frame
   fraction, and exact 48-frame resample indices.
8. Normalized tensor distances: MAE, RMSE, Pearson, cosine, blockwise MAE, and
   dynamic-time-warped diagnostic distance. DTW is diagnostic only and never
   replaces the runtime 48-frame contract.

For A versus B use same-source clip pairs. C has no pixel-identical Mapua pair,
so compare class-conditional distributions, robust standardized distances, and
nearest-neighbor/domain-classifier separability. Report signer/domain confounding
explicitly.

## Analysis outputs

- one event-level CSV with anonymous event IDs and no private paths;
- one class/domain aggregate CSV;
- blockwise distribution plots stored outside Git, hash-linked from a report;
- a three-domain report with bootstrap confidence intervals over events;
- a simple domain classifier trained only as a shift diagnostic, with grouped
  folds that keep all frames from an event together.

## Decision rule

Recommend A or B as the next Samsung candidate only if it wins the fixed
development comparison and is not materially farther from C on canonical tensor
distance, presence behavior, scale, and temporal motion. Conflicting evidence
means retain the current Android model and investigate the failing layer; it does
not authorize threshold weakening or blind retraining.

STATUS=PLAN_READY; SAMSUNG_DOMAIN_C_NOT_CAPTURED; ANDROID_NOT_MODIFIED
