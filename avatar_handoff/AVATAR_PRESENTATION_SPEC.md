# VoxGest Avatar Presentation Target

Date: 2026-09-14

The purchased Avatar is retained. Astra calibrates its presentation; Astra does not replace or redesign the character.

## Required look

The Avatar should feel **lighter, brighter, calm, comforting, and accessibility-first**.

Target composition:

- frontal, centered character
- small margin above the head
- bottom crop approximately at the waist / upper hip
- head-to-waist framing by default
- both shoulders visible
- both elbows visible where practical
- both forearms visible
- both hands completely visible
- fingers large enough to inspect
- enough side space for extended two-hand signing
- no camera orbit during signing
- no dramatic perspective distortion

## Background and lighting

Prefer:

- clean light neutral or soft pale-blue background
- bright soft frontal/key light
- gentle fill
- low-shadow presentation
- clear separation between hands and torso/background
- readable eyes, palms and fingers

Avoid:

- dark/cinematic lighting
- harsh face or hand shadows
- colored lighting that reduces skin/hand readability
- busy environment
- strong rim lighting
- camera shake

## Readability priority

1. Hands / fingers
2. Wrists / forearms
3. Elbows
4. Face
5. Torso
6. Lower body

Never crop meaningful hand/finger motion just to enlarge the face.

A specific sign may widen the framing slightly if its signing space requires it, but keep scale consistent across clips where practical.

## Motion

Playback must be smooth, stable, deliberate, human-like, and readable. A technically valid retarget that looks robotic is not a visual pass.

Do:

- preserve validated source timing as the master timing
- preserve natural preparation, stroke, hold, and recovery phases where the source contains them
- maintain continuous shoulder, elbow, wrist, finger, and rig-appropriate quaternion/Euler motion
- inspect F-curves/keyframe transitions and wrist/finger velocity changes for discontinuity
- preserve readable handshape holds and physically coherent arm movement
- allow natural body/shoulder contribution when present in the source
- return to neutral smoothly
- compare source and Avatar at normal speed, with optional slower diagnostic playback
- preserve the original validated master Action unchanged if tutorial slowdown is later offered

Do not:

- speed up FSL just to reduce duration or improve perceived UI responsiveness
- rush transitions between handshapes or globally shorten signs
- force identical timing across different signs
- introduce robotic pose snapping, finger jitter, wrist teleporting, elbow popping, or arm-chain distortion
- over-smooth meaningful hand articulation

Record applicable visual flags:

`POSE_POP`, `WRIST_SNAP`, `FINGER_JITTER`, `HAND_INTERSECTION`, `ARM_CHAIN_DISTORTION`, `UNNATURAL_SPEED`, `TIMING_MISMATCH`, `LEFT_RIGHT_ERROR`, `BODY_DRIFT`, `CAMERA_CROP`, `SOURCE_MISMATCH`.

## Face / non-manual limitation

Keep the face natural and neutral unless validated source evidence supports something more specific.

Do not fabricate FSL facial grammar or non-manual markers. Current FullSign225/raw225 Avatar trajectory data does not provide validated facial/NMM grammar for generation.

## Design intent

The Avatar should resemble a professional accessibility interpreter display, not a game-character showcase.
