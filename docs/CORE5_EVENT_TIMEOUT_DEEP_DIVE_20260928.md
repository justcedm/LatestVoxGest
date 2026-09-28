# VoxGest Core5 EVENT_TIMEOUT Deep-Dive Plan — 2026-09-28

## Objective

Strengthen the current five-word FSL recognition pipeline on Samsung SM-A566B, with particular focus on automatic event completion and unsupported-input rejection.

Current Core5:

1. HELLO
2. THANK YOU
3. YES
4. NO
5. UNDERSTAND

Do not expand vocabulary until this pipeline is stable enough for survey use.

## Evidence already established

- Five timely manually-ended HELLO events were raw/accepted HELLO.
- Four additional HELLO events timed out but still had raw top-1 HELLO.
- The same nine HELLO tensors were top-1 HELLO in Baseline, Native48 and SIM10.
- UNDERSTAND auto-released correctly in the unchanged-boundary battery.
- NO had classifier-correct saved tensors but automatic timeout behavior.
- THANK YOU release depended in part on whether hand landmarks disappeared.
- YES has a separate model/domain weakness; SIM10 was weaker than Baseline/Native48 on saved Samsung YES tensors.
- The unchanged gate falsely accepted unsupported motion in the preserved negative battery.
- A prior debug motion-end rule using a full-225 feature-delta threshold of 0.08 did not end the saved automatic events; full-vector landmark jitter made a simple threshold unsafe.

Therefore EVENT_TIMEOUT and OOD rejection are distinct from raw classification.

## Core design change to investigate

Do not use full 225-dimensional feature delta as the sole event-end signal.

Build a timestamp-aware motion descriptor from robust, interpretable geometry:

### 1. Hand translation energy
- use x/y only initially;
- palm/wrist center relative to torso anchor;
- normalize by shoulder width or another stable body scale;
- compute speed using real timestamps.

### 2. Hand articulation energy
- finger landmarks relative to wrist/palm;
- normalize by current hand scale;
- use robust median/percentile joint speed rather than maximum or full-vector L2.

### 3. Arm/pose energy
- wrist and elbow motion relative to shoulder midpoint/torso anchor;
- use selected pose joints only.

### 4. Presence/quality
- anatomical L/R presence;
- pose presence;
- tracking gaps;
- severe occlusion/framing indicators.

Avoid Z as the primary boundary signal until its live jitter is measured separately.

## Proposed boundary-v2 state machine

All durations must be timestamp-based.

### IDLE
Maintain recent neutral/noise statistics when quality is sufficient.

### ARMED / PRE-ROLL
Keep a short ring buffer before a sign starts so preparation frames are not cut off.

### CAPTURING
Begin after either:
- meaningful motion exceeds an adaptive start threshold; or
- a valid hand appears and subsequent motion confirms activity.

Require a minimum active-motion budget so static/random detection does not create a complete sign immediately.

### END_PENDING
After minimum event duration, enter END_PENDING when robust motion falls below an adaptive low threshold.

Use hysteresis:
- START threshold = high threshold;
- END threshold = lower threshold.

Require a continuous low-motion dwell, measured in milliseconds rather than result count.

If motion rises above the cancel threshold during dwell, return to CAPTURING.

### END
Finalize using either:
- visible-hand motion-settle dwell;
- sustained no-hand disappearance;
- MANUAL_END in diagnostic mode.

Keep a short post-roll tail before finalizing.

### TIMEOUT
Retain a maximum safety timeout, but treat it as abnormal/failure evidence, not the ordinary way an event ends.

## Adaptive thresholding

Do not hardcode one magic threshold from intuition.

Estimate live/recorded noise using robust statistics:
- median motion;
- MAD (median absolute deviation);
- percentiles.

Candidate thresholds should be searched offline against preserved Samsung events.

Use leave-one-event-out or another small-sample holdout procedure where practical so one event does not directly tune and validate itself.

## Low-latency filtering

If smoothing is needed, prefer a timestamp-aware low-latency filter on motion descriptors rather than aggressively smoothing classifier landmarks.

A One-Euro-style or adaptive low-pass filter may be evaluated for the event detector only.

Do not alter the 48×225 classifier input pipeline as part of boundary-v2 unless separately authorized and validated.

## Rejection strengthening — proposed second stage

The Core5 classifier is closed-set, so it will always choose one of five classes.

A production acceptance path should combine:

1. capture-quality gate;
2. event-completion validity;
3. Core5 classifier output;
4. confidence/margin diagnostics;
5. an independent in-vocabulary/OOD check.

### FSL-native OOD training source worth investigating

Without using operator-labelled Samsung diagnostics as training data:

- positives: FSL-105 Core5 sign envelopes;
- OOD negatives: FSL-105 classes outside Core5;
- neutral negatives: pre-sign/post-sign sections from FSL-105 source clips.

This can support a separate binary Core5-in-vocabulary rejector or another evidence-backed rejection method.

Do not silently convert this proposal into production. First build offline splits and compare false accept / false reject behavior.

## Class-conditioned diagnostics

For each Core5 class, compute training/reference distributions for:
- source envelope duration;
- hand-presence ratio;
- one/two-hand usage;
- normalized path length;
- motion-energy statistics;
- start/end relative hand position;
- articulation amount.

Use these initially for diagnostics and explainable rejection research, not rigid production thresholds until device validation supports them.

## 60 Hz visual skeleton

Keep display smoothness separate from recognition.

- MediaPipe result cadence remains whatever the device can actually produce.
- Recognition uses real timestamped landmarks.
- UI may interpolate/smooth the latest landmarks for a target 60 Hz visual overlay.
- UI-interpolated landmarks must not feed the classifier.

## Immediate Astra/Codex sequence

1. Preserve/push the current recognition checkpoint before new edits.
2. Parse all saved manual/auto/negative events.
3. Produce per-event timeline plots/tables for:
   - selected x/y motion energy;
   - presence;
   - tracking gaps;
   - raw prediction;
   - gate/end reason.
4. Derive adaptive noise floors and candidate hysteresis/dwell ranges.
5. Replay candidate boundary algorithms offline.
6. Choose a debug-only boundary-v2 only if it improves saved-event behavior without obvious early cuts.
7. Run Samsung A/B with a small controlled battery.
8. Separately evaluate an FSL-105-derived OOD rejector if time permits.
9. Do not retrain the five-class classifier merely to fix EVENT_TIMEOUT.

## Device A/B target after offline qualification

At minimum:

- HELLO ×3
- THANK YOU ×3
- YES ×3
- NO ×3
- UNDERSTAND ×3
- arbitrary motion ×3
- neutral ×3

Record:
- raw class;
- confidence vector;
- boundary end reason;
- event duration;
- last meaningful motion time;
- low-motion dwell;
- hand presence;
- accepted/rejected output;
- false extra events.

## Success criteria

Boundary-v2 success is not “fewer timeouts” alone.

It should:
- end deliberate signs without requiring the hands to vanish;
- avoid splitting a sign during normal holds;
- avoid extra events after a completed sign;
- preserve classifier tensors/contracts;
- produce explainable timing evidence;
- not worsen false accepts.

OOD/rejection success is separate and must be measured separately.
