# VoxGest Core5 Streaming Semantic-Geometry Proposal — 2026-09-29

## Motivation

The current EVENT_TIMEOUT problem is primarily an event-boundary problem. A different recognition strategy can reduce dependence on explicit linguistic end detection: continuously evaluate recent landmark windows and recognize a sign when the same class becomes sufficiently strong and geometrically plausible.

This is a research/engineering proposal, not a production change.

## Core idea

Instead of:

camera -> wait for event start -> wait for event end -> classify

evaluate:

camera -> timestamped landmark stream -> rolling temporal windows -> classifier -> semantic geometry verifier -> temporal stability/peak logic -> accept/reject

This is conceptually a streaming sign-spotting path.

## Why it fits VoxGest

The existing 225 features already contain:

- 33 pose landmarks x 3 = 99
- left hand 21 x 3 = 63
- right hand 21 x 3 = 63

Pose landmarks already include useful coarse face/body anchors such as nose, eyes, ears, mouth corners, shoulders, elbows and wrists.

Therefore the first experiment does not require adding every face landmark.

## Proposed semantic geometry stream

Derive compact explicit per-frame features from the existing landmarks, such as:

- wrist/palm-to-nose vector
- wrist/palm-to-eye-center vector
- wrist/palm-to-mouth-center vector
- fingertip-to-mouth / fingertip-to-nose distance
- wrist/palm-to-shoulder midpoint vector
- hand height relative to eyes / mouth / shoulders
- elbow angle
- forearm direction
- palm orientation / palm normal proxy
- hand opening / finger flexion descriptors
- inter-hand distance and relative orientation for two-hand signs
- selected motion velocities using real timestamps

Normalize body-relative geometry by shoulder width or another stable body scale. Normalize handshape internally by hand scale.

Do not use full FaceMesh by default.

## Optional face upgrade

If later classes require precise cheek/chin/lip/eyebrow placement or non-manual markers, evaluate MediaPipe Face Landmarker or Holistic Landmarker and select a small stable subset of face anchors rather than concatenating every face point.

Do not add a heavy face stream until on-device latency is measured on Samsung SM-A566B.

## Streaming recognition / sign spotting

Maintain a rolling timestamp buffer, for example approximately 2 seconds.

Evaluate overlapping candidate windows periodically. Candidate durations should be learned from FSL-105 source envelopes and may include multiple temporal scales.

Each candidate window:
1. resample to the current 48-step temporal contract;
2. run the existing Core5 classifier;
3. run semantic-geometry plausibility checks or a learned geometry branch;
4. run OOD/rejection logic;
5. accumulate class stability across overlapping windows.

A sign may be emitted when:
- the same class forms a stable local peak across consecutive windows;
- semantic geometry is plausible for that class;
- input quality is sufficient;
- OOD/rejection permits it;
- rearm/cooldown prevents duplicate output.

This path does not require the hand to disappear and may therefore avoid the legacy EVENT_TIMEOUT dependency.

## Example: HELLO

A HELLO hypothesis should not be based on one static rule, but the learned/diagnostic geometry can encode evidence such as:

- dominant hand reaches the forehead/upper-face region;
- palm/hand orientation is consistent with source examples;
- handshape is consistent;
- the temporal path matches the trained source distribution;
- motion proceeds outward/away as represented in the source trajectories.

The classifier remains sequence-based; semantic geometry provides explicit relational evidence.

## Recommended experiment order

1. Audit the current 225 normalization to confirm that hand-to-body relational geometry is numerically preserved and comparable across source and Samsung.
2. Derive semantic features from existing FSL-105 Core5 trajectories.
3. Measure class separability using those features without modifying Android.
4. Build an offline sliding-window replay using existing Core5 classifier.
5. Compare against legacy event-gated recognition on saved Samsung data.
6. Add class-conditioned geometry verifier or compact fused model only if offline evidence supports it.
7. Evaluate OOD/non-sign rejection.
8. Only then implement debug streaming mode in the separate recognition-lab package.

## Important separation

This does not eliminate the need for rejection.

A closed-set five-class model can still assign a supported label to random motion.

Streaming acceptance should therefore combine:
- classifier evidence
- semantic geometry
- temporal stability
- input quality
- OOD rejection
- duplicate/rearm logic

## Face/upper-body policy

Do not blindly add all possible landmarks.

Prefer:
- existing 33-pose anchors first;
- explicit relative geometry;
- selected face anchors only when the vocabulary needs finer face-relative placement;
- face expressions/blendshapes only for signs whose linguistic distinction requires non-manual features.

## Success criteria

The experiment is promising if it:
- recognizes deliberate Core5 signs without waiting for hand disappearance;
- reduces or removes EVENT_TIMEOUT dependency;
- does not increase false accepts;
- avoids duplicate outputs;
- preserves on-device responsiveness;
- remains explainable and reproducible from FSL-105 source data.
