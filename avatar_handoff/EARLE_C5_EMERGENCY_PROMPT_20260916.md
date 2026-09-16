# Earle — C5 Emergency Avatar Calibration Prompt

**Issued:** 2026-09-16
**Branch:** `avatar/astra-calibration-20260914`
**Owner:** Earle / Astra Avatar lane

Paste the prompt below into Codex/ChatGPT from the local VoxGest repo.

---

You are the dedicated VoxGest **Avatar recovery engineer**. A new runtime preview labeled **Avatar candidate C5** has failed visual QA. Evidence reported by the project owner shows the Avatar nearly side-on/vertical instead of a clear frontal interpreter view, rigid/zombie-like posture, poor sign readability, and the character rendered too small in a large empty viewport.

Treat **C5 as REJECTED** until root cause is proven and corrected. Do not cosmetically patch around it.

## HARD SAFETY BOUNDARIES
- Work only on `avatar/astra-calibration-20260914`.
- Never access/write the old D: workspace; safe C: only.
- DO NOT modify recognition, MediaPipe, classifier, gating, training, or recognition UI.
- Preserve purchased Avatar source and CORE3 fallback.
- Do not force-push, rewrite history, or commit private `.blend`, raw videos, APKs, caches, credentials, or large evidence media.
- Do not invent FSL motions or claim linguistic validation.

## FIRST — READ BEFORE EDITING
Read completely:
- `avatar_handoff/AGENTS_AVATAR.md`
- `avatar_handoff/ACCEPTANCE_GATES.md`
- `avatar_handoff/AVATAR_PRESENTATION_SPEC.md`
- `docs/AVATAR_ARCHITECTURE_DECISIONS.md`
- `reports/ASTRA_LIVE_HANDOFF.md`

Then verify branch, HEAD, remote, and clean/dirty worktree. Preserve any uncommitted work safely before changes.

## TASK 1 — REPRODUCE C5 OUTSIDE THE APP
Identify the exact C5 source Action, `.blend` working copy, export settings, GLB/runtime asset, Android camera transform, and commit/build used. Reproduce the same frame in Blender or an independent GLB viewer before changing anything.

Capture/report FRONT, SIDE, and 3/4 views at:
1. neutral/rest,
2. sign preparation,
3. mid-stroke,
4. recovery/return to neutral.

## TASK 2 — ROOT-CAUSE THE FAILURE
Audit in this order; do not guess:
1. **Root/armature transforms:** rotation, scale, origin, parent transforms, unapplied transforms.
2. **Coordinate conversion:** Blender forward/up vs glTF/Filament; detect accidental ±90°/180° yaw or axis conversion.
3. **Rest/bind pose:** source vs target shoulder/clavicle/spine/arm/wrist/finger alignment.
4. **Retarget mapping:** upper arm MUST be shoulder→elbow; forearm MUST be elbow→wrist. Verify anatomical L/R and no silent swapping/mirroring.
5. **Animation bake/export:** confirm constraints are baked, Action survives export, no missing parent/root motion, quaternion continuity, no NaN/Inf.
6. **Runtime camera only after skeleton is correct:** camera target, FOV, distance, model scale, viewport aspect, clipping planes.

Do not hide a bad rig/export by applying arbitrary Android rotations, bone offsets, or one-off per-sign hacks.

## TASK 3 — PRESENTATION TARGET
The interpreter Avatar must be:
- upright and frontal;
- centered;
- large enough for hands/fingers to be readable;
- normally head-to-waist/upper-hip unless full body is needed;
- both hands fully visible with lateral signing room;
- natural shoulder/elbow/wrist chain;
- feet/body not required to dominate frame;
- neutral/pale background and stable camera;
- no orbit, perspective gimmick, tiny full-body framing, rigid zombie posture, limb twisting, wrist snapping, elbow popping, finger collapse, or mesh deformation.

## TASK 4 — PASS/FAIL GATE
C5 may become a new candidate only if ALL are proven:
- neutral pose frontal and anatomically coherent;
- no unexplained orientation flip;
- no `ARM_CHAIN_DISTORTION`, `LEFT_RIGHT_ERROR`, `BODY_DRIFT`, `WRIST_SNAP`, `FINGER_JITTER`, `HAND_INTERSECTION`, or `CAMERA_CROP`;
- source-vs-Avatar trajectory is visually comparable;
- sign timing and neutral return survive export;
- independent GLB playback matches Blender;
- Android playback matches independent GLB orientation/framing;
- repeated playback does not accumulate transforms.

If Blender is correct but GLB is wrong: isolate export/bake/axis stage.
If GLB is correct but Android is wrong: isolate Filament/model/camera transform.
If Blender itself is wrong: fix retarget/rest/root before export.

## TASK 5 — DO NOT EXPAND VOCABULARY YET
Stop new Avatar sign expansion until this orientation/retarget/presentation defect is understood. Revalidate **HELLO → MILK → RICE → neutral** after the fix. A systemic C5 defect must not contaminate more Actions.

## REQUIRED GITHUB HANDOFF
Update:
- `reports/ASTRA_LIVE_HANDOFF.md`
- `docs/AVATAR_ARCHITECTURE_DECISIONS.md` only if an authoritative architecture decision changes.

Commit and push evidence-backed source/docs changes to the Avatar branch. No empty commits.

Return exactly:
`C5_STATUS=REJECTED/FIXED/ROOT_CAUSE_PENDING`
`ROOT_CAUSE_STAGE=BLENDER_RETARGET/ROOT_AXIS/EXPORT/GLB/ANDROID_CAMERA/MULTIPLE/UNKNOWN`
`ROOT_CAUSE=`
`FILES_CHANGED=`
`BLENDER_NEUTRAL_PASS=YES/NO`
`GLB_ORIENTATION_PASS=YES/NO`
`ANDROID_FRAMING_PASS=YES/NO/NOT_TESTED`
`CORE3_REGRESSION=PASS/FAIL/NOT_RUN`
`NEXT_EXACT_ACTION=`
`BRANCH=`
`HEAD=`
`HANDOFF_UPDATED=YES/NO`

Do not report success from a build alone. Success requires visual evidence that the Avatar is frontal, readable, mechanically coherent, and stable.
