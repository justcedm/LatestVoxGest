# Phrase Live Runtime Calibration Report

Source logs:
- `reports\live_word_test_log_20260525_111019.json`
- `reports\live_word_test_log_20260525_111332.json`

## Summary Buckets

- Stable labels: fullsign225:LIVE, fullsign225:MY, fullsign225:NOTHING, fullsign225:OKAY, onehand162:MY, onehand162:NOTHING
- Partially stable labels: fullsign225:YOU, fullsign225:YOUR, onehand162:NAME, onehand162:WHAT, onehand162:YOUR
- Unstable labels: fullsign225:NAME, fullsign225:STUDENT, fullsign225:WHAT, fullsign225:WHERE
- Gate-blocked labels: fullsign225:NOTHING, fullsign225:OKAY, fullsign225:YOU, onehand162:WHAT
- Capture-blocked labels: fullsign225:NAME, fullsign225:STUDENT, fullsign225:WHAT, fullsign225:WHERE, onehand162:MY, onehand162:NAME, onehand162:NOTHING, onehand162:WHAT

## Per Log Details

### `reports\live_word_test_log_20260525_111019.json`

| Label | Status | Trials | Matched | Accepted | False Accepts | Gate Blocked | Capture Blocked | Top Confusions |
|---|---|---:|---:|---:|---:|---:|---:|---|
| MY | stable | 7 | 5 | 5 | 0 | 0 | 2 | :2 |
| NAME | partial | 8 | 0 | 6 | 6 | 0 | 2 | NOTHING:6, :2 |
| NOTHING | stable | 5 | 5 | 0 | 0 | 0 | 5 | :5 |
| WHAT | partial | 7 | 3 | 2 | 0 | 3 | 3 | :3, MY:1 |
| YOUR | partial | 8 | 0 | 8 | 8 | 0 | 0 | NOTHING:8 |

### `reports\live_word_test_log_20260525_111332.json`

| Label | Status | Trials | Matched | Accepted | False Accepts | Gate Blocked | Capture Blocked | Top Confusions |
|---|---|---:|---:|---:|---:|---:|---:|---|
| LIVE | stable | 1 | 1 | 1 | 0 | 0 | 0 | - |
| MY | stable | 1 | 1 | 1 | 0 | 0 | 0 | - |
| NAME | unstable | 1 | 0 | 0 | 0 | 0 | 1 | :1 |
| NOTHING | stable | 1 | 1 | 0 | 0 | 2 | 0 | LIVE:1 |
| OKAY | stable | 1 | 1 | 0 | 0 | 2 | 0 | - |
| STUDENT | unstable | 1 | 0 | 0 | 0 | 0 | 1 | :1 |
| WHAT | unstable | 1 | 0 | 0 | 0 | 0 | 1 | :1 |
| WHERE | unstable | 1 | 0 | 0 | 0 | 0 | 1 | :1 |
| YOU | partial | 1 | 0 | 0 | 0 | 1 | 0 | MY:1 |
| YOUR | partial | 1 | 0 | 1 | 1 | 0 | 0 | WHAT:1 |

## Demo Runtime Decision

- Do not retrain from these logs alone.
- Use `VOXGEST_GATE_PROFILE=demo` only for live calibration/demo testing.
- Use `VOXGEST_LIVE_TEST_CAPTURE_MODE=manual_capture` for phrase signs so the signer controls the 30-frame capture window.
- Keep `NOTHING` as no-output. It must not update text, speech, avatar, or history.
- Use Android phrase/token buttons as the safest presentation path while live phrase labels are still unstable.
