# Mapúa Transactional FSL v1 Raw-Video Audit

`MAPUA_AUDIT_COMPLETE=YES`

`MAPUA_TOTAL_VIDEOS=1107`

`MAPUA_TOTAL_CLASSES=26`

`MAPUA_PASS=670`

`MAPUA_REVIEW=408`

`MAPUA_REJECT_TECHNICAL=29`

This is a technical computer-vision usability audit, not a determination of FSL
linguistic correctness. No file was deleted, no model was trained, and no
Android/UI/Avatar source was changed.

## Provenance and content

- Source archive: `Transactional Filipino Sign Language Dataset.zip`
- Source and preserved-copy SHA-256:
  `51333B36E8CCA082BC5ECB1B53B0242E1C91D9D00393C2A75E18DCE27CEB48DE`
- ZIP safety: 2,214 entries, zero unsafe traversal paths, zero extraction
  collisions.
- Extracted pairing: 1,107 MP4 + 1,107 paired NPY, with no missing/orphan pair.
  The supplied NPY arrays were inventoried but were not trusted as raw-video
  truth for this audit.
- Corrupted/unreadable MP4: 0. Declared/decoded frame mismatches: 0.
- Resolution: 1,107/1,107 at 640×480.
- FPS: 1,107/1,107 at 25 FPS.
- Duration: all 3.0 seconds (min/median/max 3.0); unusually short/long: 0/0.
- Signer count: `NOT_RECOVERABLE_NO_SIGNER_METADATA`. Visually distinct people
  exist, but inspection cannot assign reliable signer IDs.

Exact class counts are: AGAIN 44; CARD 44; CASH 38; COIN 44; DISCOUNT 44;
EIGHT 44; FIVE 44; FOUR 44; HELLO 49; HOW_MANY 44; HOW_MUCH 44; NINE 44;
NO 42; ONE 44; PLEASE 42; PROBLEM 42; RECEIPT 42; SEVEN 42; SIX 42; TEN 40;
THANK_YOU 42; THREE 40; TWO 42; WAIT 40; WELCOME 40; YES 40. The committed
26-row CSV contains the complete per-class quality and motion distributions.

## Raw MediaPipe measurements

MediaPipe Holistic 0.10.14 processed every unmirrored raw frame with model
complexity 1, minimum detection confidence 0.45, and tracking confidence 0.40.
All ratios below are decoded-frame-weighted over 83,025 frames:

| Measure | Rate |
| --- | ---: |
| Pose detection | 1.000000 |
| Any-hand detection | 0.779283 |
| Anatomical-left slot detection | 0.765059 |
| Anatomical-right slot detection | 0.169997 |
| Both-hand detection | 0.155772 |
| Internal landmark dropout | 0.022066 |
| Body in frame | 1.000000 |
| Any hand in frame | 0.774827 |

Slot rates describe MediaPipe output only; they are not claims about signer
handedness. Canonical anatomical slots were not mirrored or swapped.

## Motion and temporal evidence

Motion is measured as MediaPipe normalized-coordinate RMS displacement per
second. The active envelope runs from the first to last detected movement with
boundary padding constrained to detected-hand frames. This is a reproducible CV
proxy for a complete tracked trajectory, not proof that a linguistically complete
sign was performed.

| Measure | Min | P05 | P25 | Median | P75 | P95 | Max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Sign motion duration (ms) | 280 | 640 | 1,640 | 2,400 | 2,920 | 3,000 | 3,000 |
| Landmark frames in motion span | 7 | 15 | 38 | 58 | 72 | 75 | 75 |
| Peak velocity (P95 within clip) | 0.016368 | 0.029594 | 0.431277 | 0.852170 | 1.255813 | 2.028431 | 2.970136 |
| Median landmark velocity | 0.005924 | 0.011900 | 0.035208 | 0.171760 | 0.285776 | 0.515539 | 1.542788 |

Thirty-two clips failed the conservative complete-tracking proxy; 29 received a
review reason and three crossed the severe incomplete-trajectory rejection gate.
Per-class P05/median/P95 values for all four motion measures are in
`MAPUA_CLASS_QUALITY_SUMMARY.csv`.

## Fast-sign finding

Using the lower quartile of class-median detected motion duration (≤2,325 ms),
the descriptive fast group is COIN, DISCOUNT, HELLO, HOW_MANY, HOW_MUCH,
THANK_YOU, and WELCOME (307 videos). Fast duration was never a rejection rule.

For that group, pose detection is 1.000000, any-hand detection 0.642128,
any-hand-in-frame 0.629403, internal dropout 0.047123, and the tracked-trajectory
completion rate 0.931596. Technical status is 141 PASS, 145 REVIEW, and 21
REJECT_TECHNICAL. The evidence is therefore mixed: pose is stable and most
trajectories complete, while hand tracking/framing deserves targeted review in
the weaker clips. Fast signing itself is not classified as bad data.

## Technical triage and content flags

The transparent, conservative triage rules reject decode errors, severe pose or
hand failure, >25% internal dropout, fewer than eight motion-span landmark
frames, <50% motion-span hand tracking, or severe blur combined with low hand
detection. Lower hand/framing rates, >10% dropout, low blur score, incomplete
trajectory proxy, and duplicate candidates trigger REVIEW. A clip can carry
multiple reasons.

- Heavy internal dropout (>10%): 90 videos; severe (>25%): 21.
- Low any-hand detection (<50%): 166; severe (<15%): 2.
- Low-blur-score bottom 5%: 56; severe-blur plus tracking-failure rejects: 6.
- Exact raw-video duplicate groups: 0.
- Perceptual near-duplicate candidates: 66 pairs affecting 95 videos—24
  same-class and 42 cross-class pairs. These are low-distance nine-frame dHash
  candidates, not proven duplicates; neutral frames/backgrounds can create false
  candidates, so all require human review.
- Representative visual audit: 78 videos, three per class. Eighteen classes were
  rated GOOD, six USABLE, and CASH/PROBLEM QUESTIONABLE. CASH_20 has a short
  central action with long neutral lead/tail; rapid PROBLEM phases show blur.
- Sample frames contain embedded `REPLAY: <CLASS>` UI text. That is a serious
  leakage risk for pixel models; controlled landmark experiments must not use it
  as a cue.

The external evidence retained outside Git includes per-video presence metrics,
motion/retention measurements, SHA-256, technical decision/reasons, exact-hash
groups, and near-duplicate candidates under
`C:\VOXGEST_DATASETS\TRANSACTIONAL_FSL_V1\audit`. Only concise summaries are
versioned here.

## Environment limitation

The raw run used OpenCV package 4.13.0.92 and NumPy 2.4.6. NumPy differs from the
repository's older 1.26.4 pin; this is disclosed environment drift. The audit is
deterministic enough for triage evidence, but any future training extraction must
run in a freshly locked/versioned environment and preserve golden feature parity.
