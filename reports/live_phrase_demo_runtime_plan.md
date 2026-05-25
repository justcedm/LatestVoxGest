# VoxGest Demo-Safe Live Runtime Plan

- Generated: `2026-05-25T11:29:39`
- Gate profile: `VOXGEST_GATE_PROFILE=demo`
- Capture mode: `VOXGEST_LIVE_TEST_CAPTURE_MODE=manual_capture`
- Capture timing: wait for 10 stable hand frames, then record exactly 30 frames
- Strict gate remains the default when `VOXGEST_GATE_PROFILE` is unset
- `NOTHING` remains no-output

## Logs Analyzed

- `reports\live_word_test_log_20260525_111019.json`
- `reports\live_word_test_log_20260525_111332.json`

## Runtime Plan

1. Use demo gate only during adviser/panel live testing.
2. Start each word with SPACE/C so the model captures the intended 30-frame gesture.
3. Keep LOW_HAND_PRESENCE, NO_HAND, and BAD_SEQUENCE blocked.
4. Treat UNSTABLE_LANDMARKS, LOW_MOTION, and LOW_WRIST_PATH as demo warnings so low-motion signs can still be classified.
5. Prefer stable labels for the first pass, then partial labels if panelists ask for a broader demo.

## onehand162

- Stable labels: `MY, NOTHING`
- Partially stable labels: `WHAT`
- Unstable labels: `NAME, YOUR`
- Gate-blocked labels: `WHAT`
- Capture-blocked labels: `MY, NAME, NOTHING, WHAT`

| Label | Trials | Match | Accepted Correct | False Accepts | Gate Blocked | Capture Blocked | Top Confusions | Top Failures |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| MY | 7 | 71% | 71% | 0 | 0 | 2 | (blocked):2 | quality:UNSTABLE_LANDMARKS:2, wrong_label::2 |
| NAME | 8 | 0% | 0% | 6 | 0 | 2 | NOTHING:6, (blocked):2 | wrong_label:NOTHING:6, wrong_label::2, quality:LOW_HAND_PRESENCE:1, quality:UNSTABLE_LANDMARKS:1 |
| NOTHING | 5 | 100% | 0% | 0 | 0 | 5 | (blocked):5 | quality:BAD_SEQUENCE:3, quality:UNSTABLE_LANDMARKS:2 |
| WHAT | 7 | 43% | 29% | 0 | 1 | 3 | (blocked):3, MY:1 | quality:UNSTABLE_LANDMARKS:3, wrong_label::3, low_conf<0.65:2, low_margin<0.12:1, wrong_label:MY:1 |
| YOUR | 8 | 0% | 0% | 8 | 0 | 0 | NOTHING:8 | wrong_label:NOTHING:8 |

## fullsign225

- Stable labels: `LIVE, MY, NOTHING`
- Partially stable labels: `OKAY`
- Unstable labels: `NAME, STUDENT, WHAT, WHERE, YOU, YOUR`
- Gate-blocked labels: `OKAY`
- Capture-blocked labels: `NAME, STUDENT, WHAT, WHERE`

| Label | Trials | Match | Accepted Correct | False Accepts | Gate Blocked | Capture Blocked | Top Confusions | Top Failures |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| LIVE | 1 | 100% | 100% | 0 | 0 | 0 | - | - |
| MY | 1 | 100% | 100% | 0 | 0 | 0 | - | - |
| NAME | 1 | 0% | 0% | 0 | 0 | 1 | (blocked):1 | quality:UNSTABLE_LANDMARKS:1, wrong_label::1 |
| NOTHING | 1 | 100% | 0% | 0 | 0 | 0 | LIVE:1 | low_conf<0.65:1, low_margin<0.12:1 |
| OKAY | 1 | 100% | 0% | 0 | 1 | 0 | - | low_conf<0.65:1, low_motion<0.030:1 |
| STUDENT | 1 | 0% | 0% | 0 | 0 | 1 | (blocked):1 | quality:LOW_HAND_PRESENCE:1, wrong_label::1 |
| WHAT | 1 | 0% | 0% | 0 | 0 | 1 | (blocked):1 | quality:BAD_SEQUENCE:1, wrong_label::1 |
| WHERE | 1 | 0% | 0% | 0 | 0 | 1 | (blocked):1 | quality:LOW_HAND_PRESENCE:1, wrong_label::1 |
| YOU | 1 | 0% | 0% | 0 | 0 | 0 | MY:1 | low_motion<0.030:1, wrong_label:MY:1 |
| YOUR | 1 | 0% | 0% | 1 | 0 | 0 | WHAT:1 | wrong_label:WHAT:1 |
