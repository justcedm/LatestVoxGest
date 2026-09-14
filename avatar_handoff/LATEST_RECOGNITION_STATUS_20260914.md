# Recognition Context Snapshot for Avatar Takeover

Snapshot date: 2026-09-14

Source inspected read-only: `origin/recognition/mapua14-live-segment-v1:reports/CODEX_LIVE_HANDOFF.md`

This file is orientation context, not the canonical recognition handoff. Before making any recognition claim, fetch GitHub and read `reports/CODEX_LIVE_HANDOFF.md` from the current recognition branch.

## Safe high-level status

- Standard FSL-105 remains protected and separate from the experimental Mapua-14 lane.
- The experimental RD-TCN48 model/labels remained byte-identical during live-segmentation hardening; no retraining or threshold tuning occurred.
- Five OLD rolling48 HELLO attempts were captured: raw top-1 correct 3/5, accepted correct 3/5, and no inference 2/5. User-observed delays ranged from about 15 seconds to over 60 seconds.
- A separate canceled positioning interval emitted false-positive semantic tokens; raw classifier results and acceptance/output behavior must remain reported separately.
- NEW completed-event/resampled48 source, unit tests, and debug build were prepared. No NEW Samsung attempt was captured because ADB disconnected.
- Live recognition is not fixed or physically qualified. Do not use this Avatar takeover task to change, tune, retrain, or promote recognition.

## Ownership consequence

The Avatar branch may read recognition interfaces needed for integration, but must not modify recognition model/training, FullSign225 feature construction, MediaPipe, temporal segmentation, acceptance gates, or semantic output behavior. Record a cross-lane request instead.
