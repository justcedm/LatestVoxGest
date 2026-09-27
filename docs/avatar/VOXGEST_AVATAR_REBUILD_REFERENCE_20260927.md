# VoxGest Avatar Rebuild Reference — 2026-09-27

> **Purpose:** Canonical engineering handoff for Earle's Codex/Astra sessions. Read this file before doing any new VoxGest avatar calibration work.

## 1. Current decision

The **known-good Core3 avatar is the canonical VoxGest avatar platform**.

Do **not** treat the Earle Supervised9 GLB as a replacement character/platform. Treat the Earle package as a **motion/reference donor** that must be reconstructed or retargeted onto the known-good Core3 rig.

Target architecture:

```text
ONE VOXGEST CHARACTER
ONE CORE3 SKELETON
ONE ANDROID RENDERER
ONE CALIBRATION STANDARD
        |
        +-- HELLO      (known-good existing Action)
        +-- MILK       (known-good existing Action)
        +-- RICE       (known-good existing Action)
        +-- THANK_YOU  (reconstruct)
        +-- YES        (reconstruct)
        +-- NO         (reconstruct)
        +-- IM_FINE    (reconstruct)
        +-- HOW_ARE_YOU
        +-- UNDERSTAND
        +-- GOOD_EVENING
        +-- KNOW
        +-- WRONG
```

## 2. Known-good reference

Canonical Android/Core3 asset:

```text
android_dry_run/app/src/main/assets/avatar/core3/voxgest_avatar_B32_CORE3_RC2.glb
```

Known-good Actions:

- HELLO
- MILK
- RICE

Historical calibration/source material may also exist locally under:

```text
D:\VOXGEST_AVATAR_WORK\20260906\output
```

Treat that historical folder as read-only evidence.

## 3. Earle Supervised9 package

Current nine FSL words:

1. THANK YOU
2. YES
3. NO
4. IM FINE
5. HOW ARE YOU
6. UNDERSTAND
7. GOOD EVENING
8. KNOW
9. WRONG

Original Earle GLB SHA256:

```text
38869F60FF9FE91BED7308BE96B573A0483906C840F5927DC46780A0D00DB3A9
```

Existing Androidfix derivative SHA256:

```text
F1765BE502AB5AECBD11E0F0C443A1382D70603B6E7FCFA973144A247D0E6374
```

The original GLB must remain immutable.

## 4. Proven structural rendering issue

The original Supervised9 GLB produced catastrophic Android rendering: oversized dark/black geometry obscuring the avatar.

Forensic comparison found:

- skinned body/head geometry effectively scaled around **0.108**,
- some static geometry such as hair remained near original scale,
- this created roughly a **9x relative scale mismatch**,
- material/transparency differences also existed around cornea, eyelashes, and hair.

The androidfix derivative attempted to correct:
- hair/node scale alignment,
- cornea alpha/transparency behavior.

**Important:** fixing scale/materials does not prove the nine animations are calibrated correctly. Motion quality is a separate problem.

## 5. Recovery work already completed

The interrupted Avatar integration was audited and repaired.

Completed engineering recovery included:

- rejected global LISTEN switch to Supervised9 was reverted,
- Core3 production behavior was preserved,
- original and derivative GLB identities were separated,
- derivative-specific provenance manifest was introduced,
- source manifest validates the original GLB,
- runtime derivative validation checks the Androidfix asset,
- isolated Supervised9 diagnostic activity remains separate from production LISTEN,
- Android build succeeded,
- 143/143 unit tests passed,
- Supervised9-specific tests passed 14/14.

A previous recovery build reported APK SHA256:

```text
6FBF1C0ED078201A629095065C9CCE3569137EE662C181DB034DAA0EE6FFD8C9
```

This proves code/build/provenance integrity only. It does **not** prove visual or linguistic animation quality.

## 6. What must be investigated next

The central engineering question is:

> **How were HELLO, MILK and RICE authored/calibrated so that they remain smooth and Android-compatible, and how do the nine Earle Actions differ from that successful recipe?**

Do not guess. Measure and compare.

Audit the following for Core3 HELLO/MILK/RICE and each Supervised9 Action:

### Rig / structure
- mesh objects
- armature object
- object transforms
- armature transforms
- object scale
- bone scale
- bone hierarchy
- parent relationships
- rest pose
- bind pose
- bone local orientation
- wrist/finger bone layout

### Animation
- Action name
- FPS
- start/end frame
- duration
- animated bones
- keyframe count per bone
- rotation mode
- location curves
- scale curves
- interpolation type
- extrapolation
- Bezier handle behavior
- root/hip motion
- neutral lead-in
- neutral lead-out
- Action/NLA usage

### Motion
- shoulder trajectory
- elbow trajectory
- wrist trajectory
- palm orientation
- finger curl
- finger spread
- velocity/acceleration
- holds
- transition back to neutral

### Export
- coordinate system
- transform application
- GLB export options
- skin/joint export
- animation export
- material handling
- alpha/blend behavior

## 7. Preferred reconstruction strategy

Preferred order:

1. Preserve the Core3 character/rig unchanged.
2. Reverse-engineer the Core3 calibration conventions.
3. Compare Supervised9 motion against those conventions.
4. Build an explicit bone map if needed.
5. Retarget/reconstruct **motion only** onto the Core3 skeleton.
6. Preserve HELLO/MILK/RICE as regression references.
7. Prototype **THANK YOU only** first.
8. Export and test it on Android in an isolated engineering activity.
9. Only after THANK YOU passes, repeat the same recipe for the remaining eight words.

Do not rebuild all nine before the prototype is proven.

## 8. THANK YOU prototype gate

Create a new derivative Blender source, never overwriting any prior source:

```text
voxgest_core3_supervised9_rebuild_v1.blend
```

First added Action:

```text
THANK_YOU
```

Prototype requirements:

- Core3 armature stays compatible.
- No arbitrary global scaling.
- No animated armature-object scale unless Core3 proves this is required.
- No bone renaming.
- No hierarchy changes.
- No bind-pose changes without explicit evidence.
- Preserve HELLO/MILK/RICE unchanged.
- Preserve individual finger articulation.
- Preserve wrist/palm orientation.
- Match the successful Core3 interpolation conventions.
- Match Core3 neutral-entry and neutral-exit behavior.
- Avoid global aggressive smoothing.

Proposed first candidate export:

```text
voxgest_avatar_CORE3_PLUS_THANKYOU_RC1.glb
```

It should contain at least:

- HELLO
- MILK
- RICE
- THANK_YOU

## 9. Android prototype qualification

Do not modify production LISTEN.

Use an isolated diagnostic activity.

First test neutral rendering:

- full avatar visible,
- correct body/head proportions,
- hair aligned,
- no black shell,
- face visible,
- eyes/cornea acceptable,
- no crash.

Only if neutral passes, play:

- HELLO
- MILK
- RICE
- THANK_YOU

Regression rule:

**HELLO/MILK/RICE must remain visually equivalent to the known-good Core3 behavior.**

THANK_YOU must:

- start correctly,
- complete correctly,
- preserve sane body/hand deformation,
- avoid snapping/exploding,
- return to neutral.

## 10. Future calibration workflow

The long-term calibration process must become repeatable:

```text
VALIDATED FSL REFERENCE
        |
        v
CORE3 CALIBRATION TEMPLATE
        |
        v
CREATE ONE NEW ACTION
        |
        v
shoulder -> elbow -> wrist -> palm -> fingers
        |
        v
neutral -> sign -> neutral
        |
        v
automated rig/curve audit
        |
        v
isolated GLB export
        |
        v
Android isolated playback
        |
        v
human/reference validation
        |
        v
approve into catalog
```

Create a reusable protocol:

```text
VOXGEST_AVATAR_CALIBRATION_PROTOCOL_V1.md
```

It should specify:

- source/reference requirements,
- canonical rig/template,
- transform restrictions,
- FPS,
- Action naming,
- neutral lead-in/out,
- arm/wrist/palm/finger calibration,
- interpolation rules,
- export settings,
- Android qualification,
- provenance/hash recording.

Reusable Blender utilities are encouraged for mechanical checks only:

- skeleton audit,
- transform audit,
- Action audit,
- bone mapping,
- curve/interpolation inspection,
- invalid scale detection,
- animation manifest generation,
- GLB validation.

Do **not** automate linguistic decisions. FSL correctness must remain tied to validated reference evidence.

## 11. Safety rules for Astra/Codex

- Do not touch recognition branches/worktrees.
- Do not modify the production LISTEN UI during reconstruction.
- Do not overwrite the original Core3 GLB.
- Do not overwrite the original Earle GLB.
- Do not delete historical evidence.
- Do not use `git reset --hard` or `git clean`.
- Do not claim a motion is linguistically valid FSL merely because it plays correctly.
- Create derivative assets and record SHA256 for every candidate.

## 12. Required resumability

Before substantial work, create/update:

```text
reports/earle9_rebuild/ASTRA_AVATAR_REBUILD_CHECKPOINT.md
reports/earle9_rebuild/ASTRA_AVATAR_REBUILD_STATE.json
```

After every major phase record:

- timestamp,
- branch/worktree/HEAD,
- current source file,
- Actions inspected/rebuilt,
- scripts/commands,
- files created/changed,
- hashes,
- tests,
- visual findings,
- failures,
- unresolved issues,
- exact next action.

If model/token limits are near exhaustion: save Blender, save scripts, update both checkpoint files, then stop safely.

## 13. Current engineering position

The project should no longer follow this pattern:

```text
new word -> new avatar -> new rig -> new export behavior -> new Android failure
```

The desired pattern is:

```text
fixed Core3 avatar
fixed Core3 skeleton
fixed Android renderer
fixed calibration rules
        +
one new Action at a time
```

That is the canonical direction for the nine-word rebuild and all future VoxGest avatar calibration work.
