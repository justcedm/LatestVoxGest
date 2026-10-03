# Isolated Tasks LIVE_STREAM experiment — 2026-09-21

Status: implemented and compiled, **non-default, no physical performance claim**.
`ExperimentalLiveStreamLandmarks` is a debug-guarded laboratory adapter, deliberately
not registered in any recognition profile. All existing camera lanes still use VIDEO.

## Contract

- Caller submits CameraX RGBA `ImageProxy`; conversion copies it into an owned upright,
  unmirrored bitmap before returning. Caller closes the proxy.
- Source nanoseconds become milliseconds. Duplicate/regressing millisecond timestamps
  are dropped, not relabeled. Both Tasks receive exactly the same accepted timestamp.
- One in-flight image plus one replaceable latest bitmap; no queue of camera images.
  A single owner worker serializes initialization, dispatch, joins and close.
- `ExactTimestampPairer` keys by source timestamp, bounds capacity, rejects late/duplicate
  callbacks, and drains in timestamp order. Generic helper also supports multiple pending
  timestamps for deterministic delayed/out-of-order tests; adapter capacity is one.
- Pending results expire after 1,500 ms. Missing callback stops the experiment with
  `UNMATCHED_FRAME_TIMEOUT_RESTART_REQUIRED`; it never invents a pose/hand pair.
- On timeout/error, close both Tasks before releasing the retained input. Successful
  pairs retain their MPImage until both callbacks arrive; submitted bitmaps are never
  manually recycled. Only never-submitted replaced bitmaps are recycled directly.
- Callbacks marshal to the owner worker; Tasks are never closed from a detector callback.
- Same 0.45 detection/presence/tracking settings and anatomical reported-side swap as
  stable unmirrored Tasks. Unknown anatomy stays unassigned, never guessed from X.
- Pair, hand and pose callback latency plus replaced-frame count are reported separately.
  These are callback wall latencies, not native operator timing or classifier latency.

Google documents `detectAsync`, timestamped result callbacks, and dropping inputs when
the detector is busy. The bounded pair admission prevents independently busy Tasks from
silently mixing different frames. [Official hand-landmarker guide](https://ai.google.dev/edge/mediapipe/solutions/vision/hand_landmarker/android).

## Offline proof

`ExactTimestampPairerTest`: nine deterministic cases: exact pair, delayed detector,
missing result expiration, stale callback, out-of-order completion, monotonic submission,
bounded frame drops, generation reset, and expiration unblocking a newer complete pair.
This tests join logic, not JNI scheduling or bitmap native lifetime on a device.

## Physical promotion gate

Use a dedicated debug harness to instantiate the adapter; do not substitute it into
Practical15 first. Feed the same lens/orientation policy with a fresh collector per run.
Measure VIDEO versus LIVE_STREAM on Samsung and another phone: captured frames/sec,
pair/drop count, tracking presence, p50/p95 detector and result latency, thermal behavior,
and identical-sign raw predictions. Exercise background/foreground, camera switch,
slow callback, timeout and close while busy. Missing callbacks require destroying and
recreating the adapter. No state/result may survive recreation.

The adapter is ready for harness integration, not enabled by an intent or user toggle.
No physical results, speedup claim, or default change is supported yet.
