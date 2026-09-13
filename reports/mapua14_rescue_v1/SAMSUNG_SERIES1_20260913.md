# Samsung Series 1 — 2026-09-13

Status: evidence preserved before live-runtime source changes.

## Evidence boundary

- Device: Samsung SM-A566B, serial `R5GYC0M1M4P`, Android 16 / API 36.
- Filtered raw log (outside Git):
  `C:\VOXGEST_TRAINING\MAPUA14_RESCUE_V1\evidence\series1_20260913\voxgest_mapua14_series1_20260913.log`
- Raw log SHA-256:
  `66d60fcbdc0db039068d3b0d16eaea65d3e93198a243eb95d094cabf962a6848`
- The UI hierarchy and one screenshot are preserved beside the log. They are
  intentionally excluded from Git.
- The log has no explicit operator `PERFORM` or gesture-start marker. Timing
  below therefore uses the captured rolling-window boundary and is labeled as
  inferred.

## Captured HELLO raw sequence

| Time | Raw top-1 | Score | Raw top-2 | Raw top-3 | Window (ms) | Gate | Reason | TFLite (ms) |
|---|---|---:|---|---|---:|---|---|---:|
| 12:19:44.424 | HELLO | 0.9999995 | FOUR 2.7067617E-7 | TWO 1.4300082E-7 | 5829 | REJECT | TEMPORAL_STABILITY | 1.049571 |
| 12:19:52.801 | HELLO | 0.9999989 | FOUR 5.250676E-7 | TWO 2.747483E-7 | 5895 | REJECT | TEMPORAL_STABILITY | 0.906641 |
| 12:20:05.286 | HELLO | 0.9999987 | FOUR 6.22969E-7 | TWO 3.9645704E-7 | 5963 | REJECT | TEMPORAL_STABILITY | 0.802891 |
| 12:20:11.448 | HELLO | 0.9999989 | FOUR 4.6812804E-7 | TWO 2.736252E-7 | 5895 | REJECT | TEMPORAL_STABILITY | 1.025156 |

The exact sequence is `HELLO → HELLO → HELLO → HELLO`. Mean top-1 score is
approximately `0.9999990`. Every inference reports `tracking_pose=1.0` and
`tracking_any_hand=1.0`.

Nearby performance samples report MediaPipe landmark throughput of
`7.854–8.141 FPS` (mean approximately `7.988 FPS`) and camera throughput of
`14.928–14.942 FPS`. Per-inference TFLite latency is `0.802891–1.049571 ms`.

## Timing

- Inferred first captured window start: `12:19:38.595`.
- Time from that boundary to first correct raw prediction: `5829 ms`.
- From the last explicit idle event at `12:19:37.829`: `6595 ms`.
- Time to first accepted HELLO: **NOT OBSERVED / UNAVAILABLE**.

The user reported that HELLO eventually appeared as KUMUSTA after more than a
minute. The preserved buffer contains neither an accepted/emitted HELLO nor the
text KUMUSTA, so that elapsed time cannot be reconstructed honestly. The only
captured acceptance is later, unrelated/unknown activity classified as TWO at
12:26:48.612 with score `0.7798257`; the saved UI hierarchy also shows TWO.

## Failure classification

`SERIES1_PRIMARY_FAILURE=B_CLASSIFIER_CORRECT_GATE_REJECTION`

The four captured HELLO predictions are consistently correct and extremely
high-confidence, but all are blocked solely by `TEMPORAL_STABILITY`. This is
not classifier instability for the evidenced HELLO bout.

`LIVE_TEMPORAL_DISTRIBUTION_MISMATCH` is a confirmed architectural risk: the
training pipeline detects the complete sign interval and resamples it to 48
temporal positions, while the Series-1 Android lane waits for a rolling 48-frame
camera window lasting roughly 5.8–6.0 seconds. It is treated as a contributing
cause, not substituted for the directly observed gate reason.

Tracking is also a whole-session secondary failure: after the HELLO sequence,
the log contains a long run of `CURRENT_FRAME_UNUSABLE` events. It did not cause
the four HELLO inference rejections, whose tracking ratios were both 1.0.

