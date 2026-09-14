# VoxGest App Developer - Start Here

Date: 2026-09-14

Repository: `https://github.com/justcedm/LatestVoxGest.git`

Avatar branch: `avatar/astra-calibration-20260914`

This is the entry point for a developer using a brand-new ChatGPT Plus and Codex/Astra account. Do not depend on the previous owner's chat history. GitHub and the files listed below are the source of truth.

## A. Install and use

Required:

- Windows PowerShell 5.1 or newer
- Git for Windows
- ChatGPT Desktop with Codex/Astra access
- Blender 3.2 for revalidating the historical Avatar pipeline, unless the current handoff records a separately validated version
- Android Studio/JDK/SDK already required by `android_dry_run`
- a physical Android device for `DEVICE_PASS`

The takeover ZIP automates the safe folder and repository setup. Extract it on `C:` and run its root scripts in this order:

```powershell
powershell -ExecutionPolicy Bypass -File .\VERIFY_PACKAGE.ps1
powershell -ExecutionPolicy Bypass -File .\INSTALL_AND_BOOTSTRAP.ps1
```

Stop on any verification failure. Do not improvise around a failed checksum or missing file.

## B. Safe local directories

Use only safe `C:` paths:

- repository: `C:\VOXGEST_APPDEV\LatestVoxGest`
- copied knowledge: `C:\VOXGEST_APPDEV\KNOWLEDGE`
- purchased/private Avatar input: `C:\VOXGEST_AVATAR_INPUTS`
- generated Avatar work: `C:\VOXGEST_AVATAR_WORK`
- external evidence: `C:\VOXGEST_APPDEV\EVIDENCE`

The historical `D:` workspace is prohibited. Purchased Avatar source, raw videos, evidence media, APKs, caches, checkpoints, credentials, and secrets stay outside Git.

## C. Exact manual Git setup

Use this only if the package installer is unavailable:

```powershell
New-Item -ItemType Directory -Force -Path C:\VOXGEST_APPDEV | Out-Null
Set-Location C:\VOXGEST_APPDEV
git clone https://github.com/justcedm/LatestVoxGest.git LatestVoxGest
Set-Location C:\VOXGEST_APPDEV\LatestVoxGest
git fetch origin --prune
git switch --track origin/avatar/astra-calibration-20260914
git remote -v
git branch --show-current
git status --short
```

If the repository already exists, do not clone over it. Inspect `git status --short`; never reset, discard, or switch over a dirty worktree automatically.

## D. Purchased Avatar location

The developer already owns the purchased Avatar. Place or reference it under `C:\VOXGEST_AVATAR_INPUTS` or another safe `C:` path. Do not copy it into the repository, takeover ZIP, ChatGPT upload, or GitHub.

Read `avatar_handoff/LOCAL_ASSET_EXPECTATIONS.md` before moving or renaming anything.

## E. Inventory local assets

From the repository:

```powershell
powershell -ExecutionPolicy Bypass -File .\avatar_handoff\scripts\inventory_avatar_assets.ps1 `
  -RepoRoot C:\VOXGEST_APPDEV\LatestVoxGest `
  -AvatarInputs C:\VOXGEST_AVATAR_INPUTS `
  -AvatarWork C:\VOXGEST_AVATAR_WORK\20260914
```

The script inventories and hashes small enough matches. It does not copy or commit purchased assets.

## F. Open Codex/Astra

Open exactly `C:\VOXGEST_APPDEV\LatestVoxGest` in ChatGPT Desktop/Codex. Do not open the old workspace. Confirm the displayed branch is `avatar/astra-calibration-20260914`.

## G. First message for Astra/Codex

Paste this exact message:

> Read `avatar_handoff/APPDEV_ASTRA_BOOTSTRAP_PROMPT.md` completely, then follow it exactly. Treat GitHub and repository files as current truth; do not rely on chat memory. Do not access D:, do not modify recognition behavior, do not calibrate before Phase 0, and do not claim historical Avatar evidence as a current pass.

`APPDEV_ASTRA_BOOTSTRAP_PROMPT.md` routes Astra to the full executable `ASTRA_FIRST_RUN_PROMPT.md`.

## H. First message for normal ChatGPT/Sol project chat

Paste this exact message:

> Read the attached or repository copy of `avatar_handoff/APPDEV_CHATGPT_SOL_BOOTSTRAP.md` completely and adopt it as the VoxGest project bootstrap. Before making any current-state claim, inspect GitHub, the relevant branch, its live handoff, and its latest commits. Do not depend on prior chat memory.

This chat is the coordination/recognition context. Avatar implementation remains owned by Astra on the dedicated Avatar branch.

## I. Verify GitHub push access

After the clean Phase 0 audit documentation is ready:

```powershell
git remote get-url origin
git branch --show-current
git status --short
git push --dry-run origin avatar/astra-calibration-20260914
```

The remote must be `https://github.com/justcedm/LatestVoxGest.git`. A dry run verifies access without creating a commit. Do not force-push.

## J. Resume after reset or restart

Paste the contents of `avatar_handoff/ASTRA_RESUME_PROMPT.md`. Astra must fetch, verify the branch, read `reports/ASTRA_LIVE_HANDOFF.md`, inspect recent Avatar commits and relevant evidence, then continue `NEXT_EXACT_ACTION`. It must not reconstruct completed work from memory.

## K. Current truth order

1. current checked-out branch and HEAD
2. `reports/ASTRA_LIVE_HANDOFF.md` for Avatar status
3. latest Avatar commits and dated evidence reports
4. `docs/AVATAR_ARCHITECTURE_DECISIONS.md`
5. `avatar_handoff/ACCEPTANCE_GATES.md`
6. `reports/CODEX_LIVE_HANDOFF.md` on the current recognition branch for recognition status
7. `docs/PROJECT_HISTORY_JUNE_SEPT_2026.md` for concise history only

Historical evidence is context, not a current pass.

## L. Never modify or claim

- Never access the historical `D:` workspace.
- Never commit purchased Avatar source or private assets.
- Never cross-edit recognition training, model, MediaPipe, temporal/gating, or output behavior from the Avatar lane.
- Never retrain from the Avatar lane.
- Never redesign unrelated UI.
- Never silently mirror raw225/FullSign225 geometry or swap anatomical hand slots.
- Never overwrite the frozen solver or CORE3 fallback.
- Never fabricate facial/non-manual FSL grammar.
- Never claim qualified linguistic validation without qualified FSL review evidence.
- Never claim `DEVICE_PASS`, `listen_ready`, or smooth human motion without the corresponding current evidence gates.

The first engineering action is always Phase 0 inventory/audit and a pushed handoff checkpoint, not mass calibration.
