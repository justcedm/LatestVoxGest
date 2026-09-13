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
- after every +5 accepted signs
- after each runtime export batch
- after meaningful Android renderer/playback changes
- after each physical-device gate
- when a blocker changes direction
- before long tasks/context resets
- before stopping work

This is near-real-time at **push granularity**, not a live screen stream.

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