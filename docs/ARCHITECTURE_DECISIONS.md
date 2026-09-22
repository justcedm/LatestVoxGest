# VoxGest Architecture Decisions

This log contains only decisions that are authoritative for the recovered
project. New entries must state evidence and consequences; proposals and
experiments belong in separate reports.

## ADR-001 — GitHub source of truth

- Status: Accepted
- Date: 2026-09-12
- Decision: https://github.com/justcedm/LatestVoxGest.git is the shared
  upstream for the recovered repository.
- Evidence: GitHub refs exactly match recovered commits
  b6cf9eea25d100f52dfdb46b2713b695e0d39b3a (main) and
  552689a5e329a50be9f52229127d721a55aa6e57
  (github-clean-upload). The other candidate repositories do not share
  those refs.
- Consequence: origin must point to this GitHub repository. The retired
  D: clone is not an upstream and must not be accessed.

## ADR-002 — Recovery isolation and history safety

- Status: Accepted
- Date: 2026-09-12
- Decision: Preserve the reconstructed working state on
  recognition/recovery-20260912.
- Consequence: Publish this as a normal new branch. Never force-push or
  rewrite the existing GitHub branches.

## ADR-003 — Versioned artifact boundary

- Status: Accepted
- Date: 2026-09-12
- Decision: Version source, tests, small authoritative reports, runtime
  manifests/labels, final deployable TFLite models, and required app assets.
  Keep raw datasets, downloaded archives, APK/AAB outputs, Gradle/Python
  environments and caches, Android secrets, device evidence captures,
  recovery patch snapshots, and intermediate model checkpoints out of Git.
- Consequence: .gitignore is part of the source-of-truth contract. Final
  deployable artifacts are allowed only after size and secret audits.

## ADR-004 — Normal and legacy recognition lanes are separate

- Status: Accepted
- Date: 2026-09-12
- Decision: The normal Sign screen targets STANDARD_FSL_FULLSIGN225, with
  float32 input [1,20,225], output [1,105], and the 105-label FullSign225
  bundle. The accessible OneHand162/legacy demo lane remains explicit and
  separate.
- Evidence: GradingRecognitionProfiles.NORMAL_SIGN_PROFILE, the standard
  artifact gate, and passing GradingCameraAndProfileTest contracts.
- Consequence: A blocked Standard artifact must fail closed; it must not
  silently fall back to or be filtered as the legacy demo path.

## ADR-005 — Camera-coordinate and time contracts

- Status: Accepted
- Date: 2026-09-12
- Decision: A front-camera preview may be mirrored for the signer, but
  FullSign225 inference stays canonical and unmirrored. Feature layout is
  pose 99, anatomical left hand 63, anatomical right hand 63, with no slot
  swapping after anatomical resolution. Monotonic event timing remains
  (System.nanoTime() / 1_000_000L).
- Consequence: Display transforms must never mutate ML coordinates or the
  proven feature contract.

## ADR-006 — Qualification precedes retraining

- Status: Accepted
- Date: 2026-09-12
- Decision: Do not start new dataset/model training until runtime routing,
  artifact/feature/TFLite parity, rejection reasons, camera freshness,
  duplicate suppression, and physical Samsung trials are complete.
- Consequence: Correct raw top-1 predictions that are rejected require gate
  or routing repair, not retraining. Preserve the deployed model as rollback.

## ADR-007 — Presentation subsystem freeze

- Status: Accepted
- Date: 2026-09-12
- Decision: The current white/teal UI and CORE3 Avatar presentation are
  frozen during recognition qualification.
- Consequence: Recognition or repository-hardening tasks must not redesign
  Listen, Avatar, or UI styling without a new explicit decision.

## ADR-008 — Live task continuity

- Status: Accepted
- Date: 2026-09-12
- Decision: reports/CODEX_LIVE_HANDOFF.md is the canonical operational
  checkpoint and must be updated by every meaningful future task.
- Consequence: Each update records the required state, evidence, failures,
  current hypothesis, next action, and protected areas before handoff.

## ADR-009 — Raw-video dataset evidence boundary

- Status: Accepted
- Date: 2026-09-12
- Decision: Dataset quality and cross-dataset comparisons use decoded raw video
  processed by one disclosed method. Third-party landmark arrays and historical
  post-filtered feature summaries are not substitutes for same-method raw-video
  measurements.
- Consequence: The Mapúa audit may establish technical computer-vision
  usability only. FSL-105 raw quality remains blocked while its 2,130 referenced
  MOV files are absent, and no computer-vision audit claims linguistic
  correctness.

## ADR-010 — Stable recognition identity and presentation ontology

- Status: Accepted
- Date: 2026-09-12
- Decision: Model/source labels remain stable identifiers. English and Filipino
  display text are presentation fields joined through a canonical concept; a
  Filipino display translation does not replace an FSL label or imply that
  Filipino grammar equals FSL grammar.
- Consequence: Text overlap is never sufficient to merge datasets. All overlap
  equivalence and the presentation ontology require qualified linguistic/FSL
  review, especially WELCOME versus YOURE WELCOME.

## ADR-011 — Complete-trajectory temporal experiments

- Status: Accepted
- Date: 2026-09-12
- Decision: Temporal-length studies resample the complete detected sign-motion
  trajectory to 20, 32, or 48 normalized timeline positions. They never take
  only the first N camera frames.
- Consequence: Reconstruction retention is supporting evidence, not a model
  winner. Temporal length is selected by a controlled validation macro-F1 study,
  sealed test evaluation, export parity, and physical Samsung results before any
  Android change.

## ADR-012 — Non-sign data is rejection/OOD evidence

- Status: Accepted
- Date: 2026-09-12
- Decision: Idle, open palm, random motion, grooming, pointing, frame-entry/exit,
  partial/incorrect attempts, and conversational gestures are non-sign/OOD audit
  categories—not FSL vocabulary labels.
- Consequence: Their expected semantic-token count is zero. They may calibrate
  or evaluate rejection only under participant/source-disjoint splits and may
  never be emitted as recognized words.

## ADR-013 - Mapua-14 rescue experiment is authorized and isolated

- Status: Accepted
- Date: 2026-09-12
- Decision: Explicit authorization was granted for one controlled PASS-only
  engineering experiment over the 14 exact-text Mapua/FSL-105 overlap labels.
  The frozen source-video split uses a 15% sealed clip test and four-fold
  development comparison of exactly RD-TCN32, GRU32, RD-TCN48, and GRU48.
- Boundary: Signer IDs are unavailable, so all offline results are
  EXPLORATORY_CLIP_LEVEL_METRICS and are not signer-independent evidence.
  Raw videos, re-extracted features, caches, and checkpoints stay outside Git
  under C:\VOXGEST_TRAINING\MAPUA14_RESCUE_V1.
- Consequence: Any passing model is exposed only as the debug/diagnostic
  MAPUA14_RESCUE_V1 profile. Standard FSL-105 remains unchanged and is the
  rollback; promotion requires physical unseen-Samsung evidence.
