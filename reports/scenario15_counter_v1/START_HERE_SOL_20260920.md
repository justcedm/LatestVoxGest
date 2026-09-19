# SUPERSEDED FOR EXECUTION — ONE-DAY WAR ROOM ACTIVE

Use this file only as background. The active execution authority is:

`reports/scenario15_counter_v1/ONE_DAY_WAR_ROOM_PROMPT_20260920.md`

The one-day prompt supersedes the slower sequencing below. Do not spend time on multi-day planning.

---

# START HERE — Sol Scenario-15 Execution

You are resuming VoxGest under final-defense deadline pressure.

## Repository authority
- Repo: `justcedm/LatestVoxGest`
- Branch: `recognition/scenario15-counter-v1`
- This branch must contain commit `0084b39eb1e00a0fd42c5c3d675ebcb8e2744dd8` or later.
- Read completely before coding:
  1. `reports/scenario15_counter_v1/SOL_MASTER_PROMPT_20260920.md`
  2. `reports/scenario15_counter_v1/SCENARIO15_VOCABULARY_MANIFEST.csv`
  3. `reports/CODEX_LIVE_HANDOFF.md`
  4. `reports/mapua14_rescue_v1/SUMMARY.md`
  5. `reports/mapua14_rescue_v1/FOUR_DAY_ENGINEERING_SPRINT_PROMPT_20260916.md`
  6. `docs/ARCHITECTURE_DECISIONS.md`

The Scenario-15 master prompt supersedes the old broad-recognition objective.

## Frozen product scope
One controlled real-life scene:
**Greeting + name exchange + small retail/convenience-store counter interaction.**

Exact user-facing concepts:
HELLO, WHAT, YOUR, NAME, MY, YES, NO, THANK_YOU, PLEASE, MILK, RICE, HOW_MUCH, CASH, CARD, RECEIPT.

Do not expand beyond 15 without explicit owner approval.

Name behavior is first-class:
- WHAT + YOUR + NAME -> “What is your name?”
- MY + NAME -> controlled name-entry mode -> “My name is <explicitly entered name>”
- an explicitly supplied session name may later be used in “Hello, <name>”
- never hallucinate a name;
- arbitrary names are not classifier classes;
- do not claim unrestricted fingerspelling/alphabet recognition unless separately validated.

## Immediate engineering objective
Make the phone and training pipeline describe the SAME complete physical sign.

Training already uses complete-motion trajectory normalization. The rescue Android path previously used a rolling frame window. Audit this first.

Target:
`IDLE -> PRIMING -> SIGN_ACTIVE -> END/RELEASE -> complete trajectory -> resample to 48 -> FullSign225 -> classifier -> reject/accept`

Canonical input:
`[1,48,225]`
pose99 | anatomical-left63 | anatomical-right63
unmirrored ML input, no anatomical slot swap.

Do not retrain blindly before runtime/train temporal parity is measured.

## Execution order
1. Verify branch, HEAD, remotes, clean worktree.
2. Run tests + debug build.
3. Audit current Android routing for Scenario-15 and WHAT/YOUR/NAME/MY.
4. Inventory source data for all 15. Mapua covers nine transactional concepts. Search SAFE C: only for MILK/RICE and name-lane evidence. Never D:.
5. Implement/prove complete-event temporal capture/resampling in the experimental lane if mismatch exists.
6. Create Python/Android parity fixtures and diagnostics.
7. Build the Mapua transactional baseline with RD-TCN48 unless evidence shows it is unsuitable.
8. Physically test raw Samsung classifier output BEFORE gate tuning.
9. Capture targeted Samsung calibration only for weak/missing concepts.
10. Integrate the exact 15-concept user-facing routing and deterministic phrase composition.
11. Run negatives/OOD; NOTHING remains rejection, never a vocabulary class.
12. Qualify each concept; never call a word DEMO_READY from offline metrics alone.

## Protected systems
Do NOT modify:
- Avatar calibration/assets (Earle/Astra owns Avatar)
- Standard FSL-105 assets
- Mapua14 rollback
- unrelated UI styling
- Listen motion assets
- retired D: workspace

Preserve protected timing behavior and canonical unmirrored FullSign225.

## Git discipline
At every meaningful checkpoint:
- update `reports/CODEX_LIVE_HANDOFF.md`;
- update Scenario-15 evidence under `reports/scenario15_counter_v1/`;
- explicit staging only;
- `git diff --cached --check`;
- commit;
- push;
- verify remote branch HEAD.
Never force-push. Never commit raw datasets, captures, caches, checkpoints, APKs, secrets, or private Avatar assets.

## Autonomy
Continue autonomously through ordinary engineering decisions covered by the master prompt. Do not wait for permission for tests, instrumentation, parity work, or evidence-driven fixes.

If physical human signing is required, prepare everything first and return ONE exact test instruction to the owner.

## First response
Return only:
`BRANCH=`
`HEAD=`
`WORKTREE=`
`SAMSUNG_CONNECTED=YES/NO`
`MASTER_PROMPT_READ=YES/NO`
`MANIFEST_READ=YES/NO`
`CURRENT_BLOCKER=`
`NEXT_EXACT_ACTION=`

Then start work.
