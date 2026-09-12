# VoxGest Live Handoff

This is the canonical operational checkpoint. Every meaningful future task
must update all fields below before handoff. Record failed checks explicitly;
do not replace missing evidence with assumptions.

TIMESTAMP=2026-09-12T14:43:49+08:00

BRANCH=recognition/recovery-20260912

COMMIT=PENDING_DATASET_PACKAGE_COMMIT

SOURCE_BASE_COMMIT=5b3b241020fef1518b5006b06115661371441c8f

HANDOFF_UPDATE_COMMIT=PENDING_DATASET_PACKAGE_COMMIT

BRANCH_HEAD=PENDING_DATASET_PACKAGE_COMMIT

CURRENT_GOAL=Complete the Mapúa raw-video audit and publish a safe retraining-decision package without training or changing Android/UI/Avatar.

WORK_COMPLETED=

- Resumed the saved raw MediaPipe checkpoint at 970/1,107 and processed only
  the 137 missing videos. Final presence audit: 1,107 unique rows, zero errors.
- Added a separate resumable raw-video motion pass because the prior checkpoint
  did not retain trajectories. Completed all 1,107 videos and simulated complete
  trajectory resampling at 20, 32, and 48 samples.
- Assigned per-video PASS/REVIEW/REJECT_TECHNICAL with exact technical reasons;
  preserved all raw files and all external per-video evidence outside Git.
- Audited exact hashes and nine-frame perceptual near-duplicate candidates.
- Searched safe C: locations for FSL-105 raw clips/archive; none were found.
- Built the complete 116-row Mapúa/FSL-105 mapping, 117-concept review-gated
  presentation ontology, controlled A/B/C benchmark specification, and non-sign
  OOD capture protocol.
- Recorded authoritative dataset/ontology/temporal/OOD decisions. No training
  or Android, Listen, Avatar, model, feature, or UI edit was made.

FILES_CHANGED=

- .gitignore
- docs/ARCHITECTURE_DECISIONS.md
- docs/NONSIGN_CAPTURE_PROTOCOL.md
- reports/CODEX_LIVE_HANDOFF.md
- reports/dataset_engineering/CONTROLLED_BENCHMARK_SPEC.md
- reports/dataset_engineering/FILIPINO_PRESENTATION_ONTOLOGY.csv
- reports/dataset_engineering/FILIPINO_PRESENTATION_ONTOLOGY.md
- reports/dataset_engineering/FSL105_RAW_AUDIT_LIMITATION.md
- reports/dataset_engineering/MAPUA_CLASS_QUALITY_SUMMARY.csv
- reports/dataset_engineering/MAPUA_FSL105_LABEL_MAPPING.csv
- reports/dataset_engineering/MAPUA_RAW_AUDIT_SUMMARY.md
- reports/dataset_engineering/TEMPORAL_SPEED_STUDY.md

COMMANDS/TESTS=

- Resume script imported 970 unique checkpoint rows and reported
  `mediapipe 1107/1107`; final unique rows 1,107 and errors 0.
- Raw motion script reported `motion 1107/1107`; unique rows 1,107 and errors 0.
- External finalizer hashed 1,107/1,107 MP4s and verified class/status sums.
- Safe C: FSL-105 search: Downloads, C:\VOXGEST_DATASETS,
  C:\VOXGEST_RECOVERY_20260910, and C:\BSIT 3RD YEAR; raw match count 0.
- Mapping schema/count: 116 rows; statuses 14 exact, 1 semantic candidate,
  11 Mapúa-only, 90 FSL105-only.
- Ontology: 117 unique canonical IDs; zero empty Filipino display cells; all
  review-gated.
- Class summary: 26 rows; VIDEO_COUNT sum 1,107; technical-status sum 1,107.
- `git diff --name-only -- android_dry_run`: empty.

DATASET_STATUS=

- Mapúa Transactional FSL v1 external workspace:
  C:\VOXGEST_DATASETS\TRANSACTIONAL_FSL_V1. Raw audit complete: 1,107 MP4,
  26 classes, 83,025 decoded frames, zero corrupted videos, all 640×480,
  25 FPS, and 3.0 seconds.
- Source/copy SHA-256:
  51333B36E8CCA082BC5ECB1B53B0242E1C91D9D00393C2A75E18DCE27CEB48DE.
- Technical decisions: PASS 670, REVIEW 408, REJECT_TECHNICAL 29. Exact video
  duplicate groups 0; 66 perceptual candidates affect 95 videos and require
  human review.
- Signer count is not recoverable from supplied metadata.
- FSL-105 remains 1,704 train + 426 test references, 105 labels. Raw MOV files
  are absent; same-method raw audit and overlap trajectory comparison are
  BLOCKED_RAW_SOURCE_MISSING.

TRAINING_STATUS=NOT_STARTED. READY_FOR_CONTROLLED_TRAINING=NO. No training process was launched.

SAMSUNG_STATUS=No device work was requested or performed in this dataset-audit task. Preserve prior status: physical recognition qualification remains incomplete; do not claim current Samsung evidence.

METRICS=

- Mapúa raw pose/any-hand/left/right/both-hand detection:
  1.000000 / 0.779283 / 0.765059 / 0.169997 / 0.155772.
- Internal landmark dropout/body-in-frame/hand-in-frame:
  0.022066 / 1.000000 / 0.774827.
- Detected motion duration ms: P05 640, median 2,400, P95 3,000.
- Landmark frames: P05 15, median 58, P95 75.
- Temporal retained-variation median: 20=0.931925, 32=0.969269,
  48=0.986112. Recommendation: EXPERIMENT_REQUIRED.
- Fast descriptive classes: COIN, DISCOUNT, HELLO, HOW_MANY, HOW_MUCH,
  THANK_YOU, WELCOME. Their trajectory-completion rate is 0.931596; speed alone
  is never a failure criterion.
- Existing deployed FSL-105 offline metrics remain unchanged: test accuracy
  0.9364375461936437, macro-F1 0.9385262654349393, TFLite top-1 agreement 1.0.

FAILURES=

- FSL-105 raw source is missing, blocking same-method raw audit and MODEL_A/C.
- Mapúa signer IDs are absent, blocking defensible signer-disjoint MODEL_B/C
  splits until independent grouping/annotation.
- Text matches do not prove sign equivalence; 14 exact-text overlaps and
  WELCOME versus YOURE WELCOME require qualified FSL review.
- 29 Mapúa videos cross conservative technical-reject gates; 408 require review.
- The 66 perceptual near-duplicate pairs are candidates, not confirmed
  duplicates; 42 are cross-class and may reflect common neutral/background
  frames.
- Audit NumPy 2.4.6 differs from repository pin 1.26.4; future training
  extraction requires a locked environment and golden feature parity.
- Physical Samsung recognition/negative/duplicate/composer qualification remains
  outside this task and incomplete.

CURRENT_HYPOTHESIS=Mapúa provides technically usable raw evidence for a controlled experiment, but the current evidence cannot justify merging sources or retraining: raw FSL-105 clips, Mapúa signer groups, linguistic equivalence review, and explicit training authorization are missing. The 20-frame representation loses more reconstructed trajectory variation than 32/48, but model selection is still experimental.

NEXT_ACTION=Human-review the 29 rejects, 408 review clips, and 66 perceptual candidates; recover/annotate Mapúa signer groups; restore the original FSL-105 raw MOV source; obtain qualified FSL review of all overlap candidates and the presentation ontology; then request explicit approval before executing the existing audited A/B/C RD-TCN/GRU temporal grid.

DO_NOT_MODIFY=

- Do not access or write the retired D: workspace.
- Do not force-push or rewrite GitHub history.
- Do not stage raw datasets, archives, per-frame evidence, APK/AAB/build output,
  environments, caches, secrets, device evidence, patches, or checkpoints.
- Do not train until explicitly approved after the listed blockers are resolved.
- Do not redesign or modify the frozen white/teal UI, Listen, or CORE3 Avatar.
- Do not replace the deployed FSL-105 model; preserve it as rollback.
- Do not mirror canonical ML input or swap anatomical hand slots.
- Preserve (System.nanoTime() / 1_000_000L).

## Future update contract

Every meaningful task must refresh: TIMESTAMP, BRANCH, COMMIT,
SOURCE_BASE_COMMIT, HANDOFF_UPDATE_COMMIT, BRANCH_HEAD, CURRENT_GOAL,
WORK_COMPLETED, FILES_CHANGED, COMMANDS/TESTS, DATASET_STATUS, TRAINING_STATUS,
SAMSUNG_STATUS, METRICS, FAILURES, CURRENT_HYPOTHESIS, NEXT_ACTION, and
DO_NOT_MODIFY.
