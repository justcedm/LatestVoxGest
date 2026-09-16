# C5 emergency root-cause audit — 2026-09-16

Instruction authority: `d5325c02448bfaed392b37c7052e925134317180`,
`avatar_handoff/EARLE_C5_EMERGENCY_PROMPT_20260916.md`, explicitly invoked by Earle.
All five prerequisite documents were read completely. C5 remains **REJECTED**.
No camera/Android rotation, calibration repair, recognition change, or new candidate was made during this emergency audit.

## Checkout and preservation

The existing checkout has unrelated/uncommitted work and its Git metadata is not writable in this session.
It was preserved. A fresh checkout of the exact remote Avatar branch was made at:
`C:\Users\Erl\Documents\Codex\2026-09-16\you-are-continuing-the-voxgest-avatar\work\emergency_repo`.
It started clean at the instruction commit; origin is `https://github.com/justcedm/LatestVoxGest.git`.
All operations used C: only. No force push, private asset commit, or recognition change.

Below, `LOCAL` means
`C:\Users\Erl\Documents\Codex\2026-09-16\you-are-continuing-the-voxgest-avatar`.
Purchased sources, the historical runtime, C5 and the earlier experimental C7 derivative remain separate.
Their hashes were checked; C5 and the frozen C7 artifact are unchanged after diagnosis.

## 1. Exact candidate and reproduction

| Artifact | Local path relative to LOCAL | SHA-256 |
|---|---|---|
| C5 working blend | work/calibration/c5/avatar_exp_c5.blend | 6a6101330f11460a219494c7f230a7f243f1854c04024558de4b536419755616 |
| C5 runtime GLB | outputs/calibration_today/c5/voxgest_priority3_exp_c5.glb | 8d0880af0da743d05e32c4088b4d4937585f9fbe4eeed649af6b990490f25c84 |
| C5 experimental solver | work/calibration/code/retarget_source_fk_c5.py | 736c494f5d9a4f204c8a13dafee97ad732cc84d34cc25c640225c8df27319e36 |
| Pre-emergency C7 review derivative, NOT accepted | outputs/calibration_today/c7/voxgest_priority3_exp_c7_review.glb | 4580ad49a2839043d3b4c4df87333f092f3c08f033bf231d1c493779b19a5fbd |

C5 Actions: `FSL_THANK_YOU__EXP_C5` (243 frames), `FSL_YES__EXP_C5` (243),
`FSL_NO__EXP_C5` (244), at 60 FPS. Full raw225 example-0 sources are respectively
`THANK YOU/clips__7__0`, `YES/clips__15__0`, `NO/clips__14__0` under the recovered
`SOURCE_HANDOFF/fsl105_avatar_landmarks` directory. Configuration and source hashes are indexed in the evidence manifest.
These are experimental Actions, not frozen B32 or authoritative CORE3 masters.

C5 was generated locally outside Git and reviewed in the localhost model-viewer page.
There is **no C5 Android build, APK, integration commit, physical-device test, or Android C5 camera transform**.
Do not attribute the supplied screenshot to Filament. Its crop contains no selected sign/time value;
the exact screenshot frame cannot be recovered from the pixels alone.

Reproduction used Blender 3.2.0, factory startup, disabled auto-execution, no saves:
36 original-Blender renders and 36 reimported-C5-GLB renders, covering all three Actions,
front/side/three-quarter at frame 1, preparation sample 100, stroke sample 160, and last frame.
Phase names are diagnostic sampling labels, not qualified FSL phase annotations. In THANK YOU,
frame 100 is still at the preparation boundary; it must not be treated as proof of source preparation fidelity.
Local `work/emergency_evidence/C5_stage_comparison.jpg` juxtaposes all 24 THANK YOU views.
The neutral and stroke frontal views reproduce the missing half without changing model orientation.
Workbench deliberately isolates geometry; these views do not validate materials or normal-speed motion.

Original exporter: `work/calibration/code/export_review_c5.py`.
Explicit settings: GLB, selected visible meshes and rig, animations, forced sampling, NLA strips,
deform bones, no cameras/lights/morph targets, no frame-range restriction. Subdivision was disabled.
Installed exporter defaults, independently read from Blender RNA:
`export_apply=False`, `export_colors=True`, `export_yup=True`, `export_skins=True`, `export_all_influences=False`.

## 2. Ordered root-cause audit

### 2.1 Root transforms

C5 armature has no parent; translation and Euler rotation are zero; uniform scale is
0.1081087738275528. Matrix is diagonal with that scale. GLB scene root retains the same
uniform scale and has no rotation/translation entry. No clip animates the scene root.
There is no evidence of a whole-model 90/180-degree yaw causing the apparent vertical cut.
Non-unit scale is recorded, not blindly applied to a skinned rig.

### 2.2 Coordinate conversion

Exporter uses normal Blender Z-up to glTF Y-up conversion. Front is Blender -Y / glTF +Z.
Independent raw GLB skinning and reimport agree that the body lacks negative-X geometry;
this is not a view-dependent near clipping or orientation symptom.
Native Blender body X extent at neutral is [-0.214200, 0.192738] m.
C5 GLB body X extent is [-0.000485, 0.192738] m. Shoes and mirrored eyes lose their opposite side too.
The identical front/side/three-quarter diagnostic cameras make the stage difference visible.

### 2.3 Rest/bind and rig state — BLENDER_RETARGET failure

Compared original protected `Final-27-06-2022.blend` with C5: 525 source bones become 146;
78 retained bones change parent. Retained rest-matrix maximum element difference is 4.733913e-6,
so preserving rest geometry did not preserve the control/deformation system.
Upper arms are reparented from ORG shoulders to DEF-spine.003; constraints/drivers and control bones
were removed, bendy-bone segments collapsed to one, and bone-parented static pieces detached.
This is not an equivalent bake of the purchased rig or frozen B32 behavior.

The solver uses rest matrices from a reimported historical GLB as neutral orientation bases without
proving their local-axis equivalence to the purchased rig. Its `.L`/`.R` suffix condition then applies
neutral blending to non-arm bones. At frame 1, each shoulder, breast and pelvis local quaternion has
approximately **90 degrees** of rotation. Pelvis/breast/shoulder curves change during the sign despite
having no corresponding source-driven body motion. Both sides are contaminated in THANK YOU;
right-side non-arm curves change in YES and NO.

**Causal ablation, in memory only:** mute non-arm quaternion curves and set those pose matrices to
identity, leaving the arm curves and mesh weights intact. Across frames
1/100/125/145/160/180/200/220/243, the lower-shirt ROI (world Z < 1.02704 m, 426 vertices)
goes from **0.080337 m** maximum movement to **0.0 m**. Full-shirt maximum remains about
0.160372 m because the arm-driven sleeve region still moves. This proves the non-arm pose contamination
causes the sampled lower-shirt disturbance; it does not certify shoulders, collision, skinning or a replacement solver.

Original constraints and joint axes need a rig-native neutral/bake contract. Do not compensate with arbitrary
inverse-90-degree offsets. Pre-emergency C6/C7 filtering, geometry baking and weight edits remain unapproved
experiments. In particular, the earlier shirt-weight patch is not required to explain the isolated C5 fault.

### 2.4 Retarget mapping

C5 code uses pose LEFT 11→13→15, RIGHT 12→14→16: upper arm shoulder→elbow,
forearm elbow→wrist. Hands use raw225 LEFT [99:162], RIGHT [162:225]; input_mirrored is asserted false.
No slot swap appears in this code. This is mapping inspection, not LEFT_RIGHT visual PASS.
Global coordinates are (x*width, z*width, -y*height), not classifier window resampling.
Torso/shoulder motion is not faithfully sourced. Out-of-view handshape uses nearest observed samples.
YES additionally replaces hand articulation with a median source interval (125–190).
These limitations and the parent/neutral faults prevent source-comparable human-motion acceptance.

### 2.5 Animation bake/export — EXPORT failure

**Missing Mirror evaluation:** native body modifier stack contains enabled Mirror→Armature→Subdivision→Mask.
With `export_apply=False`, C5 exports the unevaluated one-sided mesh. Blender body has 13,221 evaluated
vertices in the diagnostic state; exported body has 8,790 vertices including glTF seams. Counts are not
direct topology equality tests, but the signed extents and three views prove the missing half.
The exporter also disables subdivision. Export must preserve the evaluated mesh/modifier result and skin
contract, not simply rotate the one-sided output.

**Unintended color attributes:** 42 COLOR attributes exist across primitives. Body COLOR_0 has black RGB
at 76.645% of exported vertices. These authoring colors modulate material color and explain the black
skin in the browser. Earlier material-only replacement did not fix it; removing the attributes in the
unapproved C7 experiment did. Historical CORE3 has no such COLOR attributes. This is separate from orientation.

All three C5 clips survive as 438 TRS channels per clip. Samples are finite and strictly time-increasing;
quaternion norm maximum error is 1.192093e-7. Maximum adjacent quaternion steps are
THANK YOU 17.6014°, YES 16.0486°, NO 18.7080°. These are measurements, not generic threshold passes.
First key is 0.016667 s; last is 4.05 s / 4.066667 s. Source sample span is 242/60 or 243/60 s;
viewer time zero therefore adds a one-frame offset to direct source seeking.
No global speed-up was applied. Neutral correctness and full hand deformation do not pass merely because keys survive.
Constraints were removed before retargeting, not faithfully evaluated and baked from the source rig.
Independent skinning over every exported frame measures **0.088389 m** maximum lower-shirt displacement
for each C5 clip (452 runtime vertices below Y=1.02704 m); Blender sampled ablation and runtime all-frame
measurements have different topology/sample sets and must not be equated numerically.

### 2.6 Runtime camera — deferred acceptance

Skeleton/export have failed, so no production camera correction was attempted.
Original local browser settings were frontal orbit 0°/90°/2 m, target (0,1.22,0), FOV 30°,
viewport 520 px high / 390 px in narrow layout, camera-controls enabled, automatic model-viewer limits.
Tiny framing was observed; actual orbit after user interaction/default limit clamping was not captured in
the screenshot. It cannot be reconstructed exactly or blamed on Android. Later C7 minimum-distance overrides
are not evidence that C5 passed.

At instruction HEAD, actual Android CORE3 host is `Core3FilamentHostView.kt`:
Filament/TextureView, no orbit manipulator; 42 mm focal length, near 0.05 m, far 20 m.
Camera targets asset-bounds upper body (waist fraction .43, headroom .035), distance is
max(height*1.07,width*1.85), eye lift .015*height, +Z-facing with +Y up.
The dialog allowlist is HELLO/MILK/RICE, not these C5 words. No Android source was changed.
Android C5 playback, clipping/aspect behavior, ten reopen cycles and accumulating-transform tests are NOT_TESTED.

## 3. Gates and next work

| Gate | Result |
|---|---|
| C5 neutral anatomy | FAIL: imported neutral axes contaminate non-arm pose |
| C5 independent GLB matches Blender | FAIL: missing Mirror geometry, material-mask export |
| Whole-model yaw explanation | Not supported by root matrices or matched views |
| ARM_CHAIN_DISTORTION / BODY_DRIFT | Unresolved / demonstrated non-arm shirt movement |
| Other motion, handedness, collision, crop flags | NOT fully evaluated; no PASS |
| Natural source timing and neutral return | Unaccepted; finite keys are insufficient |
| Android framing/repeat/device | NOT_TESTED |
| New runtime-ready signs | 0; all listen_ready=false |
| HELLO→MILK→RICE→neutral | NOT_RUN: no corrected accepted candidate; authoritative CORE3 masters/frozen solver missing |

The recovery audit inventoried and hashed all 6,531 files; all three recovered blends lack
FSL_HELLO/FSL_MILK/FSL_RICE Actions. Frozen solver expected SHA-256
`e0f71510a454db80eb6d47950c9a3ed44da06ac43f5f3a50b1f2f2b2e3dce4e2` was not recovered.
Recovered first-pass script/configs are not equivalent. See the historical recovery audit, included with
its original pre-experiment timestamp; its statement that calibration had not occurred applies only to that milestone.
Historical CORE3 GLB remains unchanged, SHA-256
`30f13fb65e7557992a8c3109460a790a69161e69f3ba9771a1e64a33f98294f8`.

Next engineering action: recover the matching frozen solver and editable CORE3 Actions; establish a neutral
pose using that rig's own local bases and evaluated deformation system, then verify an export with Mirror and
other required modifiers intact and authoring mask colors excluded. Preserve this rejected C5 as regression evidence.
Do not promote the pre-emergency C7 workaround. Re-run HELLO→MILK→RICE→neutral in order, normal-speed
source-vs-Avatar review, independent export comparison, then Android framing/repeat/device gates.
No vocabulary expansion until those gates are satisfied. If prerequisites remain missing, retain this exact blocker.

## Evidence and reproducibility

`reports/AVATAR_C5_EMERGENCY_EVIDENCE_20260916.json` indexes hashes, local paths, condensed measurements,
and all 72 renders without publishing private media or bone geometry. Complete local evidence resides in
`LOCAL/work/emergency_evidence`; original exports/configs remain under `LOCAL/work/calibration`.
Four read-only diagnostic scripts are committed in `tools/avatar_diagnostics` with usage examples in their docstrings.
The skin ablation mutates only the Blender process in memory and exits without saving. No repair asset is produced.
No architecture decision changed, so the append-only ADR file is untouched.

Delivery: normal Git push is currently blocked by unavailable GitHub credentials. Native Credential Manager
also reports it cannot prompt when noninteractive. No token was printed or stored. Commit and remote verification
are recorded in the live handoff / final delivery result; do not infer a successful push from a local commit.
