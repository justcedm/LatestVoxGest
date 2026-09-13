# Samsung OLD rolling48 HELLO attempts 1-5 diagnostic - 2026-09-14

## Scope and evidence boundary

- Device: Samsung SM-A566B, serial `R5GYC0M1M4P`.
- Runtime: `MAPUA14_RESCUE_V1`, rolling latest 48 FullSign225 frames.
- Expected label for every scored attempt: `HELLO`.
- Evidence root outside Git:
  `C:\VOXGEST_TRAINING\MAPUA14_RESCUE_V1\evidence\live_segment_ab_20260914`.
- The OLD series stopped after five attempts by explicit direction. Attempts 6-10
  do not exist.
- Trial markers bracket the interactive exchange, not the exact physical sign
  start/end. Marker-to-result values are therefore retained as audit timestamps
  but are not reported as post-sign latency.
- A missing field is written as `NOT_CAPTURED`; no value is inferred.

## Attempt 1

```text
ATTEMPT=1
EXPECTED=HELLO
USER_DISTANCE=NOT_CAPTURED; operator observed the signer was not framed and the camera initially showed wall/ceiling
TIME_TO_FIRST_RAW_HELLO_MS=NOT_CAPTURED; no raw inference occurred
RAW_TOP1_SEQUENCE=EMPTY
RAW_TOP1_SCORE_SEQUENCE=EMPTY
TOP3_SEQUENCE=EMPTY
GATE_DECISIONS=NOT_RUN
REJECTION_REASONS=IDLE_NO_LANDMARKS:299; WAITING_FOR_NEUTRAL_RELEASE:69; STARTUP_NEUTRAL_ARMING:17
WINDOW_DURATION_MS=NOT_CAPTURED
MEDIAPIPE_FPS=median_of_snapshots:12.110; range:9.832..12.185; snapshots:6
MEDIAPIPE_LATENCY=total_median_ms median_of_snapshots:79.516 range:77.307..81.645; total_p95_ms median_of_snapshots:106.310 range:91.071..119.771
TFLITE_LATENCY=NOT_RUN
POSE_PRESENCE=NOT_CAPTURED
LEFT_HAND_PRESENCE=NOT_CAPTURED
RIGHT_HAND_PRESENCE=NOT_CAPTURED
ANY_HAND_PRESENCE=NOT_CAPTURED
TRACKING_DROPOUT=NOT_CAPTURED; event log recorded IDLE_NO_LANDMARKS:299
ACCEPTED_TOKEN=NONE
TIME_TO_ACCEPT_MS=NOT_CAPTURED; no token accepted
OUTCOME=TRACKING_FAILURE_NO_INFERENCE
```

Marker interval: `05:26:57.508` to `05:27:31.411` (33,903 ms).
Log SHA-256:
`485D842DC998517B4BDC04B10CE1CFF88B867ABC1872E3531F1B07BDF2777303`.

## Attempt 2

```text
ATTEMPT=2
EXPECTED=HELLO
USER_DISTANCE=NOT_CAPTURED; user reported FULL FRAME READY before the attempt
TIME_TO_FIRST_RAW_HELLO_MS=NOT_CAPTURED; trial-marker-to-first-raw was 22306 ms, but sign start/end was not instrumented
RAW_TOP1_SEQUENCE=HELLO -> HELLO
RAW_TOP1_SCORE_SEQUENCE=0.99999964 -> 0.99999964
TOP3_SEQUENCE=[HELLO:0.99999964,EIGHT:1.5906258E-7,FOUR:8.938964E-8] -> [HELLO:0.99999964,EIGHT:1.5556309E-7,FOUR:8.8195506E-8]
GATE_DECISIONS=REJECT -> ACCEPT
REJECTION_REASONS=TEMPORAL_STABILITY -> ACCEPTED; event states also recorded CURRENT_FRAME_UNUSABLE:218, IDLE_NO_LANDMARKS:193, WAITING_FOR_LANDMARK_RELEASE:10, LANDMARK_RELEASE:4
WINDOW_DURATION_MS=5500 -> 5499
MEDIAPIPE_FPS=median_of_snapshots:10.065; range:8.212..10.902; snapshots:13
MEDIAPIPE_LATENCY=total_median_ms median_of_snapshots:90.738 range:85.043..108.754; total_p95_ms median_of_snapshots:127.537 range:99.128..133.209
TFLITE_LATENCY=15.162774 ms -> 0.936484 ms
POSE_PRESENCE=1.0 on both inferred windows
LEFT_HAND_PRESENCE=NOT_CAPTURED
RIGHT_HAND_PRESENCE=NOT_CAPTURED
ANY_HAND_PRESENCE=1.0 on both inferred windows
TRACKING_DROPOUT=NOT_CAPTURED
ACCEPTED_TOKEN=HELLO; one MAPUA14_EMIT
TIME_TO_ACCEPT_MS=NOT_CAPTURED; trial-marker-to-accept was 22447 ms, but sign start/end was not instrumented
OUTCOME=CORRECT_RAW_AND_CORRECT_ACCEPT; LIVE_LATENCY_NOT_QUALIFIED
```

Log SHA-256:
`00957BF14EDE407C8FBED3A28D0AD157AEBDE05D1A588A154A88E90AA9B6374F`.

## Attempt 3

```text
ATTEMPT=3
EXPECTED=HELLO
USER_DISTANCE=NOT_CAPTURED
TIME_TO_FIRST_RAW_HELLO_MS=NOT_CAPTURED; user observed approximately 40000 ms; trial-marker-to-first-raw was 315105 ms and includes interactive delay
RAW_TOP1_SEQUENCE=HELLO -> HELLO
RAW_TOP1_SCORE_SEQUENCE=0.99999654 -> 0.9999968
TOP3_SEQUENCE=[HELLO:0.99999654,EIGHT:1.3669777E-6,TWO:9.0669874E-7] -> [HELLO:0.9999968,EIGHT:1.2695302E-6,TWO:8.1131776E-7]
GATE_DECISIONS=REJECT -> ACCEPT
REJECTION_REASONS=TEMPORAL_STABILITY -> ACCEPTED; event states also recorded CURRENT_FRAME_UNUSABLE:462, WAITING_FOR_NEUTRAL_RELEASE:131, WAITING_FOR_LANDMARK_RELEASE:43, IDLE_NO_LANDMARKS:30, STARTUP_NEUTRAL_ARMING:6, LANDMARK_RELEASE:1
WINDOW_DURATION_MS=5795 -> 5778
MEDIAPIPE_FPS=median_of_snapshots:9.889; range:7.301..11.924; snapshots:20
MEDIAPIPE_LATENCY=total_median_ms median_of_snapshots:107.824 range:83.662..135.301; total_p95_ms median_of_snapshots:132.575 range:97.523..167.472
TFLITE_LATENCY=0.836485 ms -> 0.689687 ms
POSE_PRESENCE=1.0 on both inferred windows
LEFT_HAND_PRESENCE=NOT_CAPTURED
RIGHT_HAND_PRESENCE=NOT_CAPTURED
ANY_HAND_PRESENCE=1.0 on both inferred windows
TRACKING_DROPOUT=NOT_CAPTURED
ACCEPTED_TOKEN=HELLO; one MAPUA14_EMIT
TIME_TO_ACCEPT_MS=NOT_CAPTURED; user observed approximately 40000 ms; trial-marker-to-accept was 315231 ms and includes interactive delay
OUTCOME=CORRECT_RAW_AND_CORRECT_ACCEPT_BUT_USER_OBSERVED_APPROX_40_SECONDS
```

Log SHA-256:
`B7FD72574C73EE924705A9F81BFD506FFA207DB7E9BA7C6674AFF691E0564BF5`.

## Attempt 4

```text
ATTEMPT=4
EXPECTED=HELLO
USER_DISTANCE=CLOSER_TO_CAMERA; qualitative user report only, no measured distance
TIME_TO_FIRST_RAW_HELLO_MS=NOT_CAPTURED; user observed approximately 15000 ms; trial-marker-to-first-raw was 39131 ms and includes interactive delay
RAW_TOP1_SEQUENCE=HELLO -> HELLO
RAW_TOP1_SCORE_SEQUENCE=0.99999964 -> 0.99999964
TOP3_SEQUENCE=[HELLO:0.99999964,EIGHT:1.497667E-7,TWO:7.07597E-8] -> [HELLO:0.99999964,EIGHT:1.5193396E-7,TWO:7.2207904E-8]
GATE_DECISIONS=REJECT -> ACCEPT
REJECTION_REASONS=TEMPORAL_STABILITY -> ACCEPTED; event states also recorded CURRENT_FRAME_UNUSABLE:480, IDLE_NO_LANDMARKS:119, WAITING_FOR_NEUTRAL_RELEASE:92, WAITING_FOR_LANDMARK_RELEASE:18, LANDMARK_RELEASE:4, STARTUP_NEUTRAL_ARMING:1
WINDOW_DURATION_MS=5732 -> 5728
MEDIAPIPE_FPS=median_of_snapshots:10.785; range:7.680..11.270; snapshots:16
MEDIAPIPE_LATENCY=total_median_ms median_of_snapshots:91.565 range:84.814..135.610; total_p95_ms median_of_snapshots:122.988 range:103.804..173.628
TFLITE_LATENCY=0.952930 ms -> 0.723946 ms
POSE_PRESENCE=1.0 on both inferred windows
LEFT_HAND_PRESENCE=NOT_CAPTURED
RIGHT_HAND_PRESENCE=NOT_CAPTURED
ANY_HAND_PRESENCE=1.0 on both inferred windows
TRACKING_DROPOUT=NOT_CAPTURED
ACCEPTED_TOKEN=HELLO; one MAPUA14_EMIT
TIME_TO_ACCEPT_MS=NOT_CAPTURED; user observed approximately 15000 ms; trial-marker-to-accept was 39257 ms and includes interactive delay
OUTCOME=CORRECT_RAW_AND_CORRECT_ACCEPT_BUT_USER_OBSERVED_APPROX_15_SECONDS
```

Log SHA-256:
`01CF14E3E1A7E6CD1E8B9A6C2C05E40FA78625E2D6CB32AA4ED79FF3D6DE549E`.

## Attempt 5

```text
ATTEMPT=5
EXPECTED=HELLO
USER_DISTANCE=SAME_CLOSER_DISTANCE_AS_ATTEMPT_4; qualitative user report only, no measured distance
TIME_TO_FIRST_RAW_HELLO_MS=NOT_CAPTURED; no raw inference occurred after more than 60000 ms user-observed wait
RAW_TOP1_SEQUENCE=EMPTY
RAW_TOP1_SCORE_SEQUENCE=EMPTY
TOP3_SEQUENCE=EMPTY
GATE_DECISIONS=NOT_RUN
REJECTION_REASONS=WAITING_FOR_NEUTRAL_RELEASE:588; IDLE_NO_LANDMARKS:487
WINDOW_DURATION_MS=NOT_CAPTURED
MEDIAPIPE_FPS=median_of_snapshots:8.896; range:7.121..11.704; snapshots:23
MEDIAPIPE_LATENCY=total_median_ms median_of_snapshots:109.716 range:84.389..141.226; total_p95_ms median_of_snapshots:131.007 range:97.799..172.023
TFLITE_LATENCY=NOT_RUN
POSE_PRESENCE=NOT_CAPTURED
LEFT_HAND_PRESENCE=NOT_CAPTURED
RIGHT_HAND_PRESENCE=NOT_CAPTURED
ANY_HAND_PRESENCE=NOT_CAPTURED
TRACKING_DROPOUT=NOT_CAPTURED; event log recorded IDLE_NO_LANDMARKS:487
ACCEPTED_TOKEN=NONE
TIME_TO_ACCEPT_MS=NOT_CAPTURED; no token accepted
OUTCOME=NO_INFERENCE_NO_ACCEPT_AFTER_MORE_THAN_60_SECONDS
```

Marker interval: `05:52:09.626` to `05:54:29.616` (139,990 ms).
Log SHA-256:
`5B12D520AA3982D1D08035502F901B0AB548EB153C2876DF66F5064C626FC461`.

## Scored OLD summary

| Metric | OLD rolling48 result |
|---|---:|
| Scored HELLO attempts | 5 |
| Attempts with correct raw top-1 | 3/5 |
| Attempts with accepted correct HELLO | 3/5 |
| Wrong accepted in scored attempts | 0/5 |
| Attempts with no inference | 2/5 |
| Correct raw windows rejected for temporal stability | 3 windows |
| Exact post-sign latency | NOT_CAPTURED |
| User-observed delay | Attempt 3 ~40 s; Attempt 4 ~15 s; Attempt 5 >60 s/no result |

Every scored inference that ran had raw top-1 `HELLO`. The warmed TFLite calls
were below 1 ms. The primary failure is therefore before or around readiness,
rolling-window formation, and release/stability state handling—not classifier
execution time. This does not prove that the model generalizes beyond HELLO.

## Distance-effect analysis

| Observation | Attempt 3 | Attempt 4 closer | Attempt 5 same closer |
|---|---:|---:|---:|
| Correct raw/accepted | yes/yes | yes/yes | no inference/no accept |
| User-observed result | ~40 s | ~15 s | >60 s, none |
| Median MediaPipe FPS across snapshots | 9.889 | 10.785 | 8.896 |
| Median of MediaPipe total-median snapshots | 107.824 ms | 91.565 ms | 109.716 ms |
| Median of camera-to-landmark-median snapshots | 190.668 ms | 185.407 ms | 182.576 ms |
| Dominant recorded states | unusable/current-frame and release waits | unusable/current-frame and release waits | neutral-release wait and no landmarks |

`DISTANCE_EFFECT_CLASSIFICATION=G_MIXED` with directly supported components
`D_TEMPORAL_GATE_BEHAVIOR` and `F_CAMERA_PIPELINE_LATENCY`. Attempt 4 correlates
with higher MediaPipe FPS and lower MediaPipe processing latency than Attempt 3,
but Attempt 5 failed at the same closer distance. Per-frame hand size, tracking
confidence, and exact physical distance were not captured, so the causal claim
that proximity itself improved recognition is `H_INSUFFICIENT_EVIDENCE`.

`ATTEMPT4_REASON_FASTER=INSUFFICIENT_EVIDENCE_FOR_CAUSATION`; the recorded
correlates are improved MediaPipe throughput/latency and eventual formation of
two usable HELLO windows. It still required a 5.7-second rolling window and a
second overlapping inference for `TEMPORAL_STABILITY`.

`ATTEMPT5_REASON_NO_RESULT=PRE_INFERENCE_STATE_FAILURE`; there is no
`MAPUA14_LIVE` or TFLite call. The runtime recorded 588
`WAITING_FOR_NEUTRAL_RELEASE` events and 487 `IDLE_NO_LANDMARKS` events.

## Unscored false-accept and presentation evidence

A separate marker opened for OLD Attempt 3 was canceled before scoring because
the user did not perform the instructed sign. During that positioning interval,
the OLD runtime nevertheless emitted one `HELLO` and one `THANK_YOU`. The log is
preserved as `voxgest_old_unscored_positioning_false_accept.log`, SHA-256
`6352055193807FD315D640E01DF8DE9AA9243AB9ED56334A20256E7735FA7E52`.
These are not included in the five HELLO scores, but they are valid false-accept
evidence. The retained composer output also produced visible repeated English
and Filipino words. Existing Clear controls did not remove the stale recognition
banner; a controlled force-stop/relaunch produced `No message yet`. No UI code
was changed in this task.

## Decision

- OLD is not usable: correct raw classification, when reached, does not reach
  the user reliably within the 1-3 second post-sign target.
- Do not retrain or tune thresholds from this evidence.
- Test `MAPUA14_LIVE_SEGMENT_V1` next for five HELLO attempts at one consistent,
  recorded framing distance. Stop if it repeatedly exceeds 10 seconds or fails.
- Report raw classifier, event finalization, gate outcome, and accepted semantic
  token separately.

