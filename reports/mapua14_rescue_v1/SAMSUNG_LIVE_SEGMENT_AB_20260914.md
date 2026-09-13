# Samsung OLD rolling48 vs NEW segmented/resampled48 A/B - 2026-09-14

Status: APK built; device A/B evidence pending.

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

- `:app:compileDebugKotlin`: PASS.
- `:app:testDebugUnitTest`: PASS (115 tests, 0 failures, 0 errors, 0 skipped).
- `:app:assembleDebug`: PASS.
- Debug APK SHA-256:
  `f1d66b42e7b9074705b39e5340d4924b34ba8a24b9d527fcf5ae591aa5fb826f`.
- Feature/TFLite parity is fail-closed at NEW-profile startup and must be
  captured again from the installed Samsung log.

## HELLO results

| Metric | OLD rolling48 | NEW segmented/resampled48 |
|---|---:|---:|
| Attempts | PENDING | PENDING |
| Raw top-1 correct | PENDING | PENDING |
| Accepted correct | PENDING | PENDING |
| Wrong accepted | PENDING | PENDING |
| Rejected correct | PENDING | PENDING |
| Tracking failures | PENDING | PENDING |
| Time to first correct raw | PENDING | PENDING |
| Time to first accepted | PENDING | PENDING |
| Median result latency | PENDING | PENDING |
| P95 result latency | PENDING | PENDING |

No live recognition fix is claimed until the 10+10 physical attempt logs are
captured and reconciled with operator markers.
