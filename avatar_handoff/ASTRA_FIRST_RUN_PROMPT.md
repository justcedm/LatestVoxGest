# ASTRA FIRST-RUN PROMPT

Paste everything below into the app developer's Codex session after opening the local VoxGest repository.

---

Continue VoxGest as the dedicated **Astra Avatar engineer**.

FIRST: do not edit anything yet.

Read these repository files completely:
- `avatar_handoff/README.md`
- `avatar_handoff/AGENTS_AVATAR.md`
- `avatar_handoff/ACCEPTANCE_GATES.md`
- `avatar_handoff/GITHUB_PROTOCOL.md`
- `reports/ASTRA_LIVE_HANDOFF.md` if it exists
- `docs/AVATAR_ARCHITECTURE_DECISIONS.md` if it exists

AUTHORITATIVE GITHUB:
`https://github.com/justcedm/LatestVoxGest.git`

BASE:
`origin/recognition/recovery-20260912`

YOUR BRANCH:
`avatar/astra-calibration-20260914`

CANONICAL LIVE HANDOFF:
`reports/ASTRA_LIVE_HANDOFF.md`

DECISION LOG:
`docs/AVATAR_ARCHITECTURE_DECISIONS.md`

CRITICAL STORAGE RULE:
Never access or write the old D: workspace. Safe C: paths only.

OWNERSHIP:
This sprint owns Avatar calibration, Blender Actions, rig/bone-map work, mechanical QA, visual-QA evidence, runtime Avatar export, Avatar manifests, Avatar-only Android renderer/playback integration, performance/stability tests, and Avatar reports.

Do NOT modify recognition model/training/MediaPipe/gating behavior or unrelated UI.

Do NOT assume historical Avatar success is current truth.

Historical items to REVALIDATE:
- Blender 3.2-compatible pipeline
- raw225 anatomical contract: pose 99 + LEFT 63 + RIGHT 63
- no silent mirroring or hand-slot swapping
- `retarget_general_B32_release_candidate_v1`
- CORE3 clips: `FSL_HELLO`, `FSL_MILK`, `FSL_RICE`
- `voxgest_avatar_B32_CORE3_RC2.glb`
- historical GLB size 28,123,308 bytes
- Filament Android runtime
- historical SurfaceView/Compose z-order failure led toward TextureView
- historical teardown/double-destroy caused native SIGSEGV and requires idempotent cleanup

PHASE 0 — AUDIT ONLY:
1. Verify current branch/base/HEAD, remotes, and working tree.
2. Inventory Avatar-related files already committed in the repository.
3. Inventory safe local C: Avatar inputs: purchased rig, Blender executable, frozen retargeter, bone map/configs, QA tools, approved FSL reference/trajectory material, CORE3 runtime package/manifests.
4. Hash important immutable/local inputs.
5. Explicitly list missing assets and which phase each blocks.
6. Run the current Android baseline unit tests/build without changing recognition.
7. Create/update `reports/ASTRA_LIVE_HANDOFF.md`.
8. Create/update `docs/AVATAR_ARCHITECTURE_DECISIONS.md`.
9. Commit and push the audit checkpoint.

STOP if required calibration source assets are missing. Never recreate a purchased asset or authoritative FSL reference from memory.

PHASE 1 — CORE3 REGRESSION:
Before any vocabulary expansion prove:
`HELLO -> MILK -> RICE -> neutral`

For each verify:
- source/reference status
- Blender Action exists and is version-correct
- mechanical QA
- source-vs-Avatar visual QA
- export verification
- runtime manifest mapping
- Android visible load/playback
- repeat playback
- Back/reopen stability

Do not claim Samsung/device PASS until physically tested.

Run repeated open/play/back/reopen testing and explicitly watch for Java crashes, native SIGSEGV, ANR, OOM, blank viewport, and memory growth.

PHASE 2 — EXPANSION:
Only after CORE3 passes current gates, rank practical source-clean canonical FSL concepts.
Use the frozen solver for ordinary signs.
Fast-fail bad sources and difficult one-off retargets.
Do not make solver v2 unless >=3 clean signs expose the same systematic solver defect.

CHECKPOINT AND PUSH:
- initial audit
- CORE3 regression result
- every +5 accepted signs
- each runtime export batch
- Android/device checkpoint
- any major blocker
- before long jobs, context resets, or stopping

At each checkpoint update `reports/ASTRA_LIVE_HANDOFF.md` first.

GIT SAFETY:
- explicit staging only
- no force push
- no history rewrite
- never commit purchased `.blend`, raw datasets/videos, bulk raw225 caches, APK/AAB, build caches, model checkpoints, credentials, or large evidence media

RETURN STATUS IN THIS EXACT COMPACT FORM:

`ASTRA_STATUS=`
`BRANCH=`
`HEAD=`
`PHASE=`
`CORE3_STATUS=`
`NEW_RUNTIME_READY=`
`RUNTIME_READY_SIGNS=`
`ANDROID_STATUS=`
`SAMSUNG_PHYSICAL_GATE=`
`BLOCKERS=`
`HANDOFF_UPDATED=`
`PUSHED=`
`NEXT_EXACT_COMMAND=`

Do not begin by redesigning the system. Begin by proving the current assets and pipeline.
