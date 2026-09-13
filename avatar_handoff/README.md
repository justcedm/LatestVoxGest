# VoxGest Astra Avatar Handoff — 2026-09-14

This folder is the dedicated handoff for the Android developer's **Codex/Astra** Avatar lane.

## Mission

Calibrate, QA, export, and prove the VoxGest FSL Avatar pipeline without changing the recognition lane.

Primary product flow:

`hearing speech -> STT -> supported FSL concept -> verified Avatar Action -> FSL user`

This is **not** unrestricted English/Filipino-to-FSL grammar generation.

## Use ChatGPT Desktop -> Codex

Open the local repository folder in Codex. Codex should operate against the local repo/terminal and use Blender through scripts/command line where practical. Visual acceptance still requires render/source comparison and human review; do not assume GUI automation is available.

## Shared source of truth

Repository: `https://github.com/justcedm/LatestVoxGest.git`

Base branch: `recognition/recovery-20260912`

Dedicated Avatar branch: `avatar/astra-calibration-20260914`

Canonical Avatar handoff: `reports/ASTRA_LIVE_HANDOFF.md`

Avatar decisions: `docs/AVATAR_ARCHITECTURE_DECISIONS.md`

Recognition handoff remains separate: `reports/CODEX_LIVE_HANDOFF.md`

## Storage rule

**Do not access or write the old D: workspace.**

Use safe C: paths only. Recommended:

- repo: `C:\VOXGEST_ASTRA\LatestVoxGest`
- private/purchased inputs: `C:\VOXGEST_AVATAR_INPUTS`
- external working area: `C:\VOXGEST_AVATAR_WORK\20260914`
- evidence: `C:\VOXGEST_AVATAR_WORK\20260914\evidence`

Do not commit purchased `.blend` files, raw datasets, bulk landmark arrays, APKs, caches, checkpoints, secrets, or large evidence media.

## Historical state to REVALIDATE

Previous Avatar work reported:

- Blender 3.2-compatible pipeline
- raw225 geometry: pose `[0:99]`, anatomical LEFT `[99:162]`, anatomical RIGHT `[162:225]`
- no silent mirroring or L/R slot swap
- frozen solver `retarget_general_B32_release_candidate_v1`
- CORE3 visual-pass concepts: HELLO, MILK, RICE
- runtime clip names: `FSL_HELLO`, `FSL_MILK`, `FSL_RICE`
- runtime file `voxgest_avatar_B32_CORE3_RC2.glb`
- historical runtime size: 28,123,308 bytes
- Android Filament runtime
- prior SurfaceView-under-Compose-Dialog z-order failure; TextureView became the safer host
- prior native SIGSEGV during teardown; cleanup must be idempotent

These are historical facts, **not current pass evidence** after workspace migration.

## Start sequence

1. Clone/fetch this branch using `scripts/bootstrap_astra.ps1`.
2. Place private/purchased Avatar inputs under safe C: storage.
3. Run `scripts/inventory_avatar_assets.ps1`.
4. Open the repo in ChatGPT Desktop -> Codex.
5. Make Codex read:
   - this file
   - `avatar_handoff/AGENTS_AVATAR.md`
   - `avatar_handoff/ASTRA_FIRST_RUN_PROMPT.md`
   - `avatar_handoff/ACCEPTANCE_GATES.md`
   - `avatar_handoff/GITHUB_PROTOCOL.md`
6. Paste `ASTRA_FIRST_RUN_PROMPT.md` to Astra.
7. Astra updates and pushes `reports/ASTRA_LIVE_HANDOFF.md` at each milestone.

## Do not expand yet

First prove current CORE3:

`HELLO -> MILK -> RICE -> neutral`

Required gates:

source integrity -> Blender Action -> mechanical QA -> visual QA -> export QA -> Android playback -> repeated Back/reopen -> physical-device proof.

Only then expand vocabulary.

## GitHub update behavior

GitHub is **near-real-time at commit/push granularity**, not a live screen stream. Astra must push after initial audit, CORE3 regression, every +5 passes, each export batch, Android/device checkpoints, blockers, and before context resets/stopping.

When the project lead says **"check Astra"**, inspect this branch plus `reports/ASTRA_LIVE_HANDOFF.md`.