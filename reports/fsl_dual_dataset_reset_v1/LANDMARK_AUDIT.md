# Landmark and complete-motion audit

STATUS=MAPUA_CANDIDATE_POOL_AUDITED; FSL105_PENDING_RAW

MediaPipe is used only as the landmark extractor. The classifier is trained on
canonical complete-event FullSign225 trajectories. All 394 selected PASS archives
were independently hash checked, opened, shape/dtype checked, and tested for
finite values before this report was written.

## Aggregate evidence

- Candidate labels: 15.
- Selected PASS clips: 394.
- Development/sealed: 334/60.
- Development fold counts: {'0': 82, '1': 85, '2': 83, '3': 84}.
- Complete-event trajectory frames: p05=30, median=62, p95=75.
- Tensor contract: float32 [48,225], finite, canonical unmirrored.
- Source-related partition/fold crossings: 0.
- FSL-105 metrics: PENDING_RAW; no historical OneHand162 proxy was mixed in.

## Lowest complete-event usable rates

| Class | PASS/raw | Usable rate | Any-hand | Internal dropout |
|---|---:|---:|---:|---:|
| HOW_MUCH | 12/44 | 0.272727 | 0.579394 | 0.059697 |
| HELLO | 19/49 | 0.387755 | 0.604082 | 0.058776 |
| PLEASE | 17/42 | 0.404762 | 0.882222 | 0.006349 |
| HOW_MANY | 20/44 | 0.454545 | 0.620303 | 0.052121 |
| THANK_YOU | 24/42 | 0.571429 | 0.687302 | 0.038413 |

## Representative evidence

For every class, one weakest and one strongest selected complete trajectory is
listed in LANDMARK_REPRESENTATIVE_EVIDENCE.csv with source-relative identity,
motion start/middle/end indices, pose/hand counts, and feature hashes. Contact
sheets are rendered outside Git under the experiment evidence directory: True.
They contain the three boundary frames with MediaPipe pose/hand overlays and are
hash-linked by the CSV. Raw pixels never enter the classifier.

## Decision boundary

This audit establishes computer-vision and tensor viability only. It does not
establish signer-independent generalization, linguistic equivalence with FSL-105,
Android parity, or live usability.
