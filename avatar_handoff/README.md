# VoxGest Astra Avatar Handoff — 2026-09-14

This folder is the dedicated handoff for the Android developer's **Codex/Astra** Avatar lane.

## Mission

Calibrate, QA, export, and prove the VoxGest FSL Avatar pipeline without changing the recognition lane.

Primary product flow:

`hearing speech -> STT -> supported FSL concept -> verified Avatar Action -> FSL user`

This is **not** unrestricted English/Filipino-to-FSL grammar generation.

## Important: purchased Avatar source

The app developer already owns/possesses the purchased Avatar source. It does **not** need to be transferred through ChatGPT, GitHub, or the knowledge ZIP.

Astra should discover it locally, keep it outside Git, hash/reference it, and work from a versioned safe C: copy.

## Use ChatGPT Desktop -> Codex

Open the local repository folder in Codex. Codex should operate against the local repo/terminal and use Blender through scripts/command line where practical. Visual acceptance still requires render/source comparison and human review; do not assume GUI automation is available.

## Shared source of truth

Repository: `https://github.com/justcedm/LatestVoxGest.git`

Base branch: `recognition/recovery-20260912`

Dedicated Avatar branch: `avatar/astra-calibration-20260914`

Canonical Avatar handoff: `reports/ASTRA_LIVE_HANDOFF.md`

Avatar decisions: `docs/AVATAR_ARCHITECTURE_DECISIONS.md`

Recognition handoff remains separate: `reports/CODEX_LIVE_HANDOFF.md`

Project history: `docs/PROJECT_HISTORY_JUNE_SEPT_2026.md`

Documentation policy: `docs/DOCUMENTATION_POLICY.md`

Fresh ChatGPT/Sol bootstrap: `docs/NEW_CHATGPT_PROJECT_BOOTSTRAP.md`

App-developer entry point: `avatar_handoff/APPDEV_START_HERE.md`

Official Astra bootstrap: `avatar_handoff/APPDEV_ASTRA_BOOTSTRAP_PROMPT.md`

Context-reset recovery: `avatar_handoff/ASTRA_RESUME_PROMPT.md`

User-facing vocabulary priority: `avatar_handoff/USER_FACING_VOCABULARY_PRIORITY.md`

Avatar visual target: `avatar_handoff/AVATAR_PRESENTATION_SPEC.md`

## Storage rule

**Do not access or write the old D: workspace.**

Use safe C: paths only. Recommended:

- repo: `C:\VOXGEST_ASTRA\LatestVoxGest`
- private/purchased inputs: `C:\VOXGEST_AVATAR_INPUTS`
- external working area: `C:\VOXGEST_AVATAR_WORK\20260914`
- evidence: `C:\VOXGEST_AVATAR_WORK\20260914\evidence`

Do not commit purchased `.blend` files, raw datasets, bulk landmark arrays, APKs, caches, checkpoints, secrets, or large evidence media.

## Fastest setup

Clone the dedicated branch:

```powershell
New-Item -ItemType Directory -Force C:\VOXGEST_ASTRA | Out-Null
Set-Location C:\VOXGEST_ASTRA
git clone --branch avatar/astra-calibration-20260914 --single-branch https://github.com/justcedm/LatestVoxGest.git
Set-Location .\LatestVoxGest
```

Create a knowledge-only handoff ZIP at any time:

```powershell
powershell -ExecutionPolicy Bypass -File .\avatar_handoff\scripts\build_appdev_knowledge_zip.ps1
```

Default ZIP output:

`C:\VOXGEST_HANDOFF\VOXGEST_APPDEV_TAKEOVER.zip`

The ZIP has the deterministic root `VOXGEST_APPDEV_TAKEOVER/`, checksum verifier, safe installer, categorized knowledge, current prompts, project history, documentation policy, handoffs, decision logs, selected small Avatar/Android/FSL text/code evidence, and Git repository metadata. It intentionally excludes the purchased Avatar source, raw datasets, models, APKs and large media.

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

1. Clone the branch with the commands above.
2. Put the developer's existing purchased/private Avatar material under safe C: storage.
3. Run `avatar_handoff/scripts/inventory_avatar_assets.ps1`.
4. Open the repo in ChatGPT Desktop -> Codex.
5. Read `docs/NEW_CHATGPT_PROJECT_BOOTSTRAP.md` if this is a fresh ChatGPT account.
6. Paste `avatar_handoff/APPDEV_ASTRA_BOOTSTRAP_PROMPT.md`; it requires Astra to read and execute `ASTRA_FIRST_RUN_PROMPT.md`.
7. Astra updates and pushes `reports/ASTRA_LIVE_HANDOFF.md` at each milestone.

## Calibration priority

First revalidate current CORE3:

`HELLO -> MILK -> RICE -> neutral`

Then immediately move to **source-clean, high-value everyday FSL concepts** from `USER_FACING_VOCABULARY_PRIORITY.md` using quality-first fast-fail processing.

Required per-sign gates:

source integrity -> Blender Action -> mechanical QA -> visual QA -> export QA -> Android playback -> physical-device proof.

## Presentation target

The purchased Avatar remains the character. Present it lighter, brighter and comforting: frontal, centered, head-to-waist/upper-hip, both hands/fingers clearly visible, soft pale/neutral background, soft frontal lighting, minimal shadow. See `AVATAR_PRESENTATION_SPEC.md`.

## Bilingual product rule

Each supported canonical Avatar concept must expose separate English and Filipino presentation/speech aliases. These aliases do not rename the FSL action. Unsupported or ambiguous speech must not fabricate an Avatar action.

## GitHub update behavior

GitHub is **near-real-time at commit/push granularity**, not a live screen stream. Astra must push after initial audit, CORE3 regression, every +3 accepted user-facing signs, every export batch, Android/device checkpoints, major blockers, before long work, before strategy/solver changes, before context resets, immediately on usage/context warnings, and before stopping. If none occurs, push meaningful completed work at least about every 30 minutes; never create empty commits.

Documentation is part of completion: every meaningful milestone updates the live handoff and receives a 1–2 line project-history entry.

When the project lead says **"check Astra"**, inspect this branch plus `reports/ASTRA_LIVE_HANDOFF.md`.
