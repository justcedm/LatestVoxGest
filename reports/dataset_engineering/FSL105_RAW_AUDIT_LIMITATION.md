# FSL-105 Raw-Audit Limitation

`FSL105_RAW_SOURCE_FOUND=NO`

`FSL105_RAW_AUDIT=BLOCKED_RAW_SOURCE_MISSING`

The safe C: search covered the current user Downloads directory,
`C:\VOXGEST_DATASETS`, `C:\VOXGEST_RECOVERY_20260910`, and the historical
`C:\BSIT 3RD YEAR` root. It found no FSL-105 MOV, `clips.zip`, DOI-named archive,
or equivalent raw-media tree. The recovery repository contains only labels and
split references: 1,704 train rows plus 426 test rows, 2,130 total unique MOV
references across 105 labels.

The available historical evidence is not an apples-to-apples raw-video audit:

- `reports/fsl105_dataset_audit.json` describes a post-filtered 64-class
  OneHand162 feature corpus, not the current 105-class FullSign225 raw video. It
  reports pose presence mean 1.0 and selected-hand presence mean
  0.9590474416294088 across accepted 20-frame sequences.
- `docs/FSL105_DATASET_READINESS.md` reports 1,038 videos processed, 1,032
  contributing videos, 16,146 source-valid sequences, and 16,104 eligible
  sequences after excluding 42 duplicate-content sequences. Those values apply
  only to that historical 64-class feature experiment.
- The old handedness audit is incomplete/internally inconsistent and is not used
  as raw-quality evidence.

Consequently, FSL-105 pose/hand/dropout, blur, framing, speed, and complete-motion
rates are `N/A_RAW_VIDEO_MISSING`. Actual cross-source trajectory comparison for
the 14 exact-text candidates, including any decision to merge them, remains
blocked. WELCOME and YOURE WELCOME are not treated as equivalent.
