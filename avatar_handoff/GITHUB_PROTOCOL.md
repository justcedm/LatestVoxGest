# Astra GitHub Live Update Protocol

GitHub is the communication bus between Astra, the project lead, and the recognition/review lane.

## Dedicated branch

`avatar/astra-calibration-20260914`

Do not perform Avatar calibration directly on the recognition branch.

## Canonical status files

Avatar live handoff:
`reports/ASTRA_LIVE_HANDOFF.md`

Avatar decisions:
`docs/AVATAR_ARCHITECTURE_DECISIONS.md`

Recognition status remains separate:
`reports/CODEX_LIVE_HANDOFF.md`

## Push cadence

Astra must update the live handoff and push a checkpoint:
- after initial environment/asset audit
- after CORE3 regression
- after every +3 accepted user-facing signs during rapid calibration
- after every runtime export batch
- after an Android integration milestone
- after a physical-device evidence milestone
- on discovery of a major blocker
- before starting a long Blender/export/build task
- before changing strategy or solver
- before a context reset or new chat
- immediately when a usage/context-limit warning appears
- before stopping for any reason

If none occurs during a long session, checkpoint at least about every 30 minutes when meaningful completed work exists. Never create an empty/no-op checkpoint.

This is near-real-time at **push granularity**, not a live screen stream.

Important work must never exist only in chat context. If context becomes constrained, checkpoint and push first, then summarize. Do not depend on knowing an exact remaining-token number.

At every checkpoint:

1. update `reports/ASTRA_LIVE_HANDOFF.md`
2. update the dated detailed report when evidence was generated
3. append only a concise 1-2 line project-history milestone when appropriate
4. update the Avatar ADR only for a real authoritative decision
5. explicitly `git add -- <paths>`
6. run `git diff --cached --check` and inspect staged paths
7. commit and push normally
8. verify the remote Avatar branch HEAD

## Handoff minimum fields

- `UPDATED_AT=`
- `BRANCH=`
- `BASE_COMMIT=`
- `HEAD=`
- `WORKTREE=`
- `PHASE=`
- `FROZEN_SOLVER=`
- `CORE3_FALLBACK_PRESERVED=`
- `SOURCE_ASSETS=`
- `CORE3_STATUS=`
- `RUNTIME_READY_SIGNS=`
- `REVIEW_REQUIRED_SIGNS=`
- `ANDROID_STATUS=`
- `SAMSUNG_PHYSICAL_GATE=`
- `BLOCKERS=`
- `CHANGED_FILES=`
- `NEXT_EXACT_COMMAND=`
- `NEXT_EXACT_TASK=`
- `NEXT_EXACT_ACTION=`
- `CLAIMS_STILL_PROHIBITED=`

## Commit rules

Before staging:
`git status --short`

Stage only explicit files:
`git add -- <path1> <path2> ...`

Validate:
`git diff --cached --check`
`git diff --cached --name-only`

Commit examples:
- `avatar(audit): inventory safe calibration assets`
- `avatar(calibration): revalidate core3 actions`
- `avatar(qa): checkpoint five visual passes`
- `avatar(export): verify runtime batch 02`
- `avatar(android): harden filament teardown`
- `docs(astra): update live handoff`

Push normally:
`git push`

Forbidden:
- force push
- history rewrite
- broad staging of generated directories
- purchased Avatar source files
- raw FSL datasets/videos
- bulk raw225 features
- APK/AAB
- build/cache/checkpoint directories
- secrets/credentials
- large render/video evidence

Large/private evidence stays in the external safe C: workspace; Git stores only concise hashes/manifests/status references.

## Project-lead review

When the project lead says **"check Astra"**, inspect:
1. branch tip
2. `reports/ASTRA_LIVE_HANDOFF.md`
3. latest Avatar commits/diffs
4. acceptance evidence references
5. Android/device gate status

Never infer local unpushed progress from GitHub.
