# VoxGest Avatar Acceptance Gates

## A — Local asset integrity

PASS only if the required purchased rig, Blender toolchain, frozen solver/configs, approved FSL references/trajectories, QA tools, and CORE3 fallback are located on safe C: storage and important immutable inputs are hashed.

Never use the prohibited old D: workspace.

## B — Blender / rig

PASS only if:
- source `.blend` opens in the intended Blender version
- expected rig/control structure exists
- independent finger articulation is available
- textures/dependencies are accounted for
- neutral/rest state is reproducible
- coordinate/scale/FPS/export conventions are recorded
- purchased source remains preserved; calibration uses a versioned working copy

Historical bug regression:
- upper arm must follow shoulder -> elbow
- forearm must follow elbow -> wrist
- do not reintroduce CLI-only `sys.argv.index("--")` assumptions for scripts that may be run from Blender's Text Editor

## C — Per-sign mechanical QA

PASS only if:
- Action exists with canonical/versioned name
- finite transforms; no NaN/Inf
- valid frame range/FPS
- correct anatomical hands
- no unexplained orientation flip
- quaternion continuity acceptable
- palms/wrists/fingers coherent
- short-gap handling is defensible
- no severe spikes/hyperextension
- neutral entry/return
- inactive limbs deterministic
- no gross collision
- no clip contamination

## D — Per-sign visual QA

Compare source reference against Avatar and inspect:
- hand(s)
- signing location
- trajectory
- handshape
- palm orientation
- fingers
- wrist
- elbow/shoulder
- timing
- neutral transition
- readability / collision

The master Action must preserve validated source timing and natural preparation, stroke, hold, and recovery where present. Inspect at normal speed and, optionally, slower for diagnosis. Slower tutorial playback must not replace or modify the validated master Action.

Require continuous shoulder/elbow/wrist/finger motion, physically coherent arm movement, readable handshape holds, natural body/shoulder contribution where sourced, smooth neutral return, rig-appropriate rotation continuity, and F-curve/keyframe plus wrist/finger velocity inspection.

Fail or review with explicit flags:

- `POSE_POP`
- `WRIST_SNAP`
- `FINGER_JITTER`
- `HAND_INTERSECTION`
- `ARM_CHAIN_DISTORTION`
- `UNNATURAL_SPEED`
- `TIMING_MISMATCH`
- `LEFT_RIGHT_ERROR`
- `BODY_DRIFT`
- `CAMERA_CROP`
- `SOURCE_MISMATCH`

Do not shorten clips for UI responsiveness, force identical sign timing, rush handshape transitions, allow robotic snapping, or over-smooth meaningful articulation.

Mechanical PASS is not visual PASS.
Engineering visual PASS is not linguistic certification by an FSL expert.

## E — Runtime export

PASS only if:
- GLB/runtime package loads independently
- expected clip name exists
- duration/timing correct
- hand/finger deformation survives export
- neutral entry/return survives export
- inactive limbs remain deterministic
- clips do not contaminate one another

Regression mix should include:
`HELLO -> NEW_A -> MILK -> NEW_B -> RICE -> NEW_C -> neutral`

## F — Android runtime

PASS only if:
- current unit tests/build pass
- Avatar loads lazily
- visible render succeeds
- expected Action plays
- repeat playback succeeds
- Back closes safely
- reopen succeeds
- at least 10 open/play/back/reopen cycles show no Java crash, native SIGSEGV, ANR, or OOM
- memory behavior is recorded and not obviously unbounded
- recognition behavior remains untouched

Historical Android regressions to guard:
- blank Avatar because SurfaceView was below Compose Dialog
- double/invalid Filament native teardown causing SIGSEGV

## G — Physical device

No `DEVICE_PASS` without actual hardware evidence.

Record:
- timestamp
- branch + HEAD
- build/APK variant
- device model + Android version
- action(s) tested
- repetitions
- crash/ANR/OOM/native status
- screenshot/video/log evidence location
- tester notes

## Product status

`listen_ready=true` only after `SOURCE_PASS + MECHANICAL_PASS + HUMAN_MOTION VISUAL_PASS + EXPORT_PASS` for the same versioned Action.

`android_ready=true` only after `ANDROID_BUILD_PASS` plus Android runtime gates and physical `DEVICE_PASS`.

`LINGUISTICALLY_VALIDATED` remains a separate gate and requires actual qualified FSL review evidence. Engineering/source/mechanical/visual/export success does not imply it.

## Reliability fallback

If live 3D remains materially unstable after targeted fixes and CORE3 cannot pass the Android stability gate, do not endlessly patch it.

Create a documented fallback experiment:
`verified Blender Action -> fixed interpreter camera -> H.264 MP4 -> local Android playback`

Preserve Blender Actions as the source master. Compare reliability and document the decision before changing product architecture.
