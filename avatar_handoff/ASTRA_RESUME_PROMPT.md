# Astra Resume Prompt

Paste everything below after a context reset, ChatGPT close, machine restart, or later return.

---

Resume the existing VoxGest Avatar work from GitHub and local evidence. Do not restart completed calibration from memory.

Repository: `C:\VOXGEST_APPDEV\LatestVoxGest`

Remote: `https://github.com/justcedm/LatestVoxGest.git`

Required branch: `avatar/astra-calibration-20260914`

Never access D:. Do not modify recognition behavior, retrain, redesign unrelated UI, overwrite the frozen solver/CORE3 fallback, or commit purchased/private Avatar assets.

Proceed in order:

1. Run `git status --short`, `git branch --show-current`, `git rev-parse HEAD`, and `git remote -v`.
2. If the worktree is dirty, inspect and preserve it. Do not reset, discard, regenerate, or switch over it.
3. Run `git fetch origin --prune`. Verify the required branch and compare local/remote HEAD. Use only fast-forward integration after reviewing any divergence.
4. Read `reports/ASTRA_LIVE_HANDOFF.md` completely.
5. Inspect the latest Avatar commits with `git log --date=iso-strict --oneline -15` and `git diff`/`git show` as relevant.
6. Read only the current detailed report and `docs/AVATAR_ARCHITECTURE_DECISIONS.md` entries relevant to the handoff's current phase/blocker.
7. Inspect the latest safe-C local inventory, normally `C:\VOXGEST_AVATAR_WORK\20260914\ASTRA_LOCAL_ASSET_INVENTORY.csv`. Re-run `avatar_handoff/scripts/inventory_avatar_assets.ps1` only if local assets may have changed.
8. Verify protected items before edits: purchased source remains outside Git, `retarget_general_B32_release_candidate_v1` is preserved, CORE3 fallback is preserved, and recognition files are untouched.
9. Continue the handoff's `NEXT_EXACT_ACTION`/`NEXT_EXACT_TASK`. If it is missing, stale, or contradicted by evidence, stop and record the exact blocker instead of guessing.
10. At the next mandatory checkpoint, update the live handoff/evidence, explicitly stage files, run `git diff --cached --check`, commit, push, and verify remote branch HEAD.

Return only a compact status with branch, HEAD, phase, evidence inspected, next action, blockers, and push state.

---
