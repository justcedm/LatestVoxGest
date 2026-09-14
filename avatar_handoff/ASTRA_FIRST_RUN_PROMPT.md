# ASTRA FIRST-RUN PROMPT

Paste everything below into the app developer's Codex session after opening the local VoxGest repository.

---

Continue VoxGest as the dedicated **Astra Avatar engineer**.

TIME IS CRITICAL. Optimize for reliable **user-facing everyday communication signs**, not arbitrary vocabulary count.

FIRST: do not edit anything yet.

Read these files completely and treat them as your startup knowledge base:

- `avatar_handoff/README.md`
- `avatar_handoff/AGENTS_AVATAR.md`
- `avatar_handoff/ACCEPTANCE_GATES.md`
- `avatar_handoff/GITHUB_PROTOCOL.md`
- `avatar_handoff/USER_FACING_VOCABULARY_PRIORITY.md`
- `avatar_handoff/AVATAR_PRESENTATION_SPEC.md`
- `docs/PROJECT_HISTORY_JUNE_SEPT_2026.md`
- `docs/DOCUMENTATION_POLICY.md`
- `reports/ASTRA_LIVE_HANDOFF.md`
- `docs/AVATAR_ARCHITECTURE_DECISIONS.md`

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

PROJECT HISTORY:
`docs/PROJECT_HISTORY_JUNE_SEPT_2026.md`

CRITICAL STORAGE RULE:
Never access or write the old D: workspace. Safe C: paths only.

IMPORTANT LOCAL-ASSET FACT:
The app developer already owns/possesses the purchased Avatar source. Do not require that purchased source to be committed, uploaded, or transferred through GitHub. Discover it locally and hash/reference it from safe C: storage.

OWNERSHIP:
This sprint owns Avatar calibration, Blender Actions, rig/bone-map work, mechanical QA, visual QA evidence, runtime Avatar export, Avatar manifests, Avatar-only Android renderer/playback integration, performance/stability tests, bilingual speech-to-supported-concept mapping for Avatar playback, and Avatar documentation.

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

RETARGETING REGRESSIONS THAT MUST NEVER RETURN:
- Blender scripts must work whether or not `sys.argv` contains `--`; never assume `sys.argv.index("--")` is always valid.
- upper arm = shoulder -> elbow
- forearm = elbow -> wrist
- never restore the historical incorrect elbow -> wrist upper-arm mapping.

AVATAR VISUAL TARGET:
- keep the purchased character
- lighter, brighter, comforting presentation
- centered frontal pose
- default crop head-to-waist / upper hip
- both hands completely visible
- fingers readable
- enough side space for two-hand signing
- soft light/neutral or pale-blue background
- soft frontal lighting, gentle fill, low shadow
- no cinematic lighting, camera orbit, shake, or perspective that makes the hands too small
- readability priority: hands/fingers > wrists/forearms > elbows > face > torso > lower body
- neutral/natural face unless validated evidence supports more; do not invent FSL facial/NMM grammar

USER-FACING VOCABULARY PRIORITY:
After CORE3 revalidation, process the cleanest high-value everyday concepts first. Start with concepts such as THANK YOU, YES, NO, PLEASE, HELP, SORRY, HOW ARE YOU, IM FINE, UNDERSTAND, DON'T UNDERSTAND, KNOW, DON'T KNOW, NAME, MY, YOUR, STOP, DOCTOR, WATER, YOURE WELCOME, GOOD MORNING, GOOD AFTERNOON, GOOD EVENING. Only use a concept if the exact canonical FSL-105 class exists and the source is acceptable. Fast-fail poor candidates and move on.

BILINGUAL RULE:
Every supported Avatar concept must expose:
- `canonical_action`
- `english_display`
- `filipino_display`
- `english_speech_aliases`
- `filipino_speech_aliases`
- `action_name`
- `validation_status`
- `listen_ready`

English/Filipino are presentation/resolver strings, not renamed FSL action IDs.
Unsupported or ambiguous speech must not fabricate an Avatar action.

Examples:
- HELLO -> Hello -> Kumusta
- THANK YOU -> Thank you -> Salamat
- YES -> Yes -> Oo
- NO -> No -> Hindi
- HELP -> Help -> Tulong
- WATER -> Water -> Tubig
- DOCTOR -> Doctor -> Doktor
- MILK -> Milk -> Gatas
- RICE -> Rice -> Kanin/Bigas

PHASE 0 — AUDIT ONLY:
1. Verify current branch/base/HEAD, remotes, and working tree.
2. Inventory Avatar-related files already committed in the repository.
3. Inventory safe local C: Avatar inputs: purchased rig, Blender executable, frozen retargeter, bone map/configs, QA tools, approved FSL reference/trajectory material, CORE3 runtime package/manifests.
4. Hash important immutable/local inputs.
5. Explicitly list missing assets and which phase each blocks.
6. Run the current Android baseline unit tests/build without changing recognition.
7. Update `reports/ASTRA_LIVE_HANDOFF.md`.
8. Update `docs/AVATAR_ARCHITECTURE_DECISIONS.md` where a real decision occurs.
9. Append a 1–2 line milestone to `docs/PROJECT_HISTORY_JUNE_SEPT_2026.md` whenever a meaningful project milestone is completed.
10. Commit and push the audit checkpoint.

STOP if required calibration source assets are missing. Never recreate a purchased asset or authoritative FSL reference from memory.

PHASE 1 — CORE3 REGRESSION:
Before vocabulary expansion prove:
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

For human-motion `VISUAL_PASS`, compare source and Avatar at normal speed and inspect slower only as QA. Preserve validated master timing, preparation, stroke, hold, recovery, continuous shoulder/elbow/wrist/finger movement, readable handshape holds, physically coherent arms/body contribution, and smooth neutral return. Inspect F-curves/keyframes and wrist/finger velocity for discontinuity. Never shorten signs for UI responsiveness or accept robotic snapping, finger jitter, wrist teleporting, elbow popping, over-smoothing, or forced identical timing.

Record flags exactly when present: `POSE_POP`, `WRIST_SNAP`, `FINGER_JITTER`, `HAND_INTERSECTION`, `ARM_CHAIN_DISTORTION`, `UNNATURAL_SPEED`, `TIMING_MISMATCH`, `LEFT_RIGHT_ERROR`, `BODY_DRIFT`, `CAMERA_CROP`, `SOURCE_MISMATCH`.

`listen_ready=true` requires `SOURCE_PASS + MECHANICAL_PASS + HUMAN_MOTION VISUAL_PASS + EXPORT_PASS` for the same Action. Qualified FSL linguistic validation is separate and must not be claimed without qualified review evidence.

PHASE 2 — RAPID USER-FACING EXPANSION:
Only after CORE3 passes current gates:

1. Rank source-clean user-facing canonical classes by communication value and source quality.
2. Process the highest-value clean signs first.
3. Use the frozen solver for ordinary signs.
4. Fast-fail bad sources and difficult one-off retargets.
5. Do not create solver v2 unless >=3 clean signs expose the same systematic solver defect.
6. Batch-export only verified passes.
7. Update the bilingual resolver/manifest only for verified supported actions.
8. Prefer 10 excellent everyday signs over 25 weak signs.

If time and source quality allow, continue useful everyday expansion after the priority set is healthy. Do not stop merely because a target count was reached when sources remain clean, the pipeline is stable, and accepted Actions remain protected.

PHASE 3 - RUNTIME EXPORT:
1. Export only versioned Actions that passed all four `listen_ready` gates.
2. Preserve the current CORE3 fallback and master Actions.
3. Verify clip/action names, duration, timing, neutral entry/return, finger deformation, cross-clip isolation, manifest mapping, and checksum.
4. Populate the bilingual resolver fields without renaming canonical Actions.
5. Unsupported or ambiguous speech must resolve to no fabricated animation.
6. Commit and push every export batch before Android integration.

PHASE 4 - ANDROID VALIDATION:
1. Keep recognition behavior untouched and the purchased Avatar demand-loaded.
2. Validate Filament load/render/action/repeat/neutral transitions without reloading the GLB between supported Actions.
3. Guard TextureView/Compose visibility and idempotent native cleanup.
4. Run unit tests/build and at least 10 open/play/back/reopen cycles, recording crash, SIGSEGV, ANR, OOM, blank-viewport, and memory evidence.
5. Do not claim device success from emulator/JVM/build evidence.
6. Commit and push the Android integration milestone.

PHASE 5 - PHYSICAL-DEVICE VALIDATION:
1. Install the exact built revision on the available physical Samsung.
2. Record device, Android version, branch, HEAD, build hash/variant, tested Actions/repetitions, visible framing, motion flags, playback transitions, stability, memory, and external evidence path/hash.
3. Re-test CORE3 plus the new verified batch and bilingual resolver behavior.
4. Set `DEVICE_PASS` only from actual passing device evidence; keep `LINGUISTICALLY_VALIDATED` separate.
5. Update reports, commit, push, and verify the remote branch HEAD.

CHECKPOINT AND PUSH:
- initial audit
- CORE3 regression result
- every +3 accepted user-facing signs
- every runtime export batch
- Android integration and physical-device evidence milestones
- discovery of any major blocker
- before long Blender/export/build jobs
- before changing strategy/solver
- before context reset/new chat
- immediately on usage/context-limit warning
- before stopping for any reason

If none occurs, checkpoint meaningful completed work at least about every 30 minutes. Never create an empty/no-op commit. When context becomes constrained, checkpoint and push first, then summarize.

At each checkpoint:
1. update `reports/ASTRA_LIVE_HANDOFF.md`
2. update a detailed milestone report when needed
3. append the 1–2 line project-history summary
4. update the Avatar ADR only if an authoritative decision changed
5. explicitly stage paths and run `git diff --cached --check`
6. commit and push to GitHub
7. verify the remote Avatar branch HEAD

GIT SAFETY:
- explicit staging only
- no force push
- no history rewrite
- never commit purchased `.blend`, raw datasets/videos, bulk raw225 caches, APK/AAB, build caches, model checkpoints, credentials, or large evidence media

DOCUMENTATION IS PART OF DONE:
A technical change is not complete until its status/evidence is reflected in GitHub documentation. Follow `docs/DOCUMENTATION_POLICY.md`.

RETURN STATUS IN THIS EXACT COMPACT FORM:

`ASTRA_STATUS=`
`BRANCH=`
`HEAD=`
`PHASE=`
`CORE3_STATUS=`
`USER_FACING_SIGNS_ATTEMPTED=`
`RUNTIME_READY_SIGNS=`
`BILINGUAL_MAP_STATUS=`
`AVATAR_PRESENTATION_STATUS=`
`ANDROID_STATUS=`
`SAMSUNG_PHYSICAL_GATE=`
`BLOCKERS=`
`HANDOFF_UPDATED=`
`PROJECT_HISTORY_UPDATED=`
`PUSHED=`
`NEXT_EXACT_COMMAND=`

Do not begin by redesigning the system. Begin by proving the current assets and pipeline, then move quickly through the highest-value everyday signs.
