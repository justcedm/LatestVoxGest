# VoxGest Documentation Policy

Date adopted: 2026-09-14

## Objective

No important VoxGest engineering decision should exist only in ChatGPT/Codex history, screenshots, or one developer's local machine. GitHub is the shared documentary source of truth.

## Required documentation layers

### 1. Lane live handoff

Recognition:
`reports/CODEX_LIVE_HANDOFF.md`

Avatar:
`reports/ASTRA_LIVE_HANDOFF.md`

Update after each meaningful checkpoint and before a long context reset or stop.

### 2. Project history

`docs/PROJECT_HISTORY_JUNE_SEPT_2026.md`

Append a 1–2 line summary for every milestone that materially changes architecture, evidence, capability, limitation, data, runtime behavior, or testing status.

### 3. Architecture decisions

Recognition/system:
`docs/ARCHITECTURE_DECISIONS.md`

Avatar:
`docs/AVATAR_ARCHITECTURE_DECISIONS.md`

Use an append-only ADR style for consequential decisions. Record what was chosen, why, alternatives, evidence, and rollback implications.

### 4. Detailed milestone report

Create a report when work produces meaningful evidence. Recommended naming:

`reports/<LANE>_<TOPIC>_YYYYMMDD.md`

Examples:

- `reports/AVATAR_CORE3_REGRESSION_20260914.md`
- `reports/AVATAR_BATCH02_QA_20260914.md`
- `reports/SAMSUNG_MAPUA14_LIVE_TEST_20260913.md`

## Minimum content for every detailed report

- date/time
- owner/lane
- branch
- HEAD commit
- purpose
- what changed
- what did not change / protected areas
- evidence and tests
- exact device/tool versions when relevant
- PASS/FAIL/OPEN statuses
- failures/blockers
- known limitations
- next exact action

## Claims discipline

Never write `PASS`, `verified`, `ready`, `working`, `FSL correct`, or `device tested` without naming the gate/evidence that supports it.

Use these distinctions:

- `IMPLEMENTED` = code/assets exist
- `DESKTOP_TESTED` = automated/local non-device test passed
- `VISUAL_PASS` = source-vs-Avatar engineering visual review passed
- `ANDROID_BUILD_PASS` = Android build/tests passed
- `DEVICE_PASS` = actual physical-device evidence passed
- `LINGUISTICALLY_VALIDATED` = qualified FSL review evidence exists

One status does not imply another.

## Git discipline

- explicit staging only
- no force push
- no history rewrite
- no secrets
- no raw datasets/videos
- no purchased Avatar source
- no APK/AAB/build cache
- no huge evidence media unless explicitly approved

Large evidence stays outside Git; Git reports record safe file paths, hashes, summaries and conclusions.

## Every milestone must update history

After a detailed report is completed, add a 1–2 line entry to the project history. This keeps the repository readable without requiring a reviewer to open every report.

## Cross-agent rule

Sol and Astra use separate technical branches but the same GitHub repository. Each agent must keep its own live handoff current. Reviewers may inspect both branches without asking a developer to paste Codex output.

## Paper synchronization

When an engineering milestone changes a claim that affects the thesis, mark the report:

`PAPER_IMPACT=YES`

and include:

`PAPER_SECTIONS_TO_REVIEW=`

Examples: Chapter 1 Scope, Chapter 3 Methodology, Chapter 4 Results, Limitations.

This prevents the manuscript from drifting away from the actual system.