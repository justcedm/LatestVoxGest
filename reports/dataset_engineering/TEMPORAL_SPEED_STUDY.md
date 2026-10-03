# Mapúa Complete-Trajectory Temporal Study

`TEMPORAL_LENGTH_RECOMMENDATION=EXPERIMENT_REQUIRED`

## Method

For each of 1,107 raw MP4 clips, MediaPipe was run on all 75 frames. The detected
motion envelope was bounded by tracked hand motion and constrained to detected
hand frames. Missing coordinate values with at least two observations were
linearly interpolated along time. The complete envelope—not the first N camera
frames—was then resampled to 20, 32, and 48 normalized timeline positions.

Trajectory-information retention is `1 - reconstruction SSE / temporal-variance
SSE`: the fixed-length representation is interpolated back to the original
motion-span length and compared with its input trajectory. This evaluates
geometric reconstruction only. It does not measure label discrimination,
generalization, calibration, TFLite latency, or physical-device recognition.

## Dataset-wide result

| Samples | P05 retention | P25 | Median | P75 | P95 | Mean |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 20 | 0.736334 | 0.868000 | 0.931925 | 0.970834 | 0.994370 | 0.907778 |
| 32 | 0.879146 | 0.940921 | 0.969269 | 0.988687 | 0.998231 | 0.958784 |
| 48 | 0.942894 | 0.971982 | 0.986112 | 0.995076 | 0.999189 | 0.980481 |

All three use the complete detected trajectory, so variable signing duration is
normalized rather than truncated. As expected, 48 samples reconstructs the
source path most closely, while 32 materially improves the lower tail over 20.
That monotonic interpolation result is not sufficient to promote 48: longer
inputs can change training stability, data efficiency, model size, latency, and
live window duration.

## Recommendation

Keep 20, 32, and 48 in the controlled candidate grid with identical splits and
training budgets. Treat 32 as a reasonable balanced hypothesis, not a selected
winner. Select length by validation macro-F1, then verify the single frozen
winner on held-out test, TFLite export/parity, and physical Samsung latency and
recognition. No Android window, model, or runtime contract changes are authorized
by this reconstruction study.

Per-class retention medians and motion-duration/landmark/velocity P05, median,
and P95 distributions are in `MAPUA_CLASS_QUALITY_SUMMARY.csv`.
