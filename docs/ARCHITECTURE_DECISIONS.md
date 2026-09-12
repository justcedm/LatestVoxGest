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
