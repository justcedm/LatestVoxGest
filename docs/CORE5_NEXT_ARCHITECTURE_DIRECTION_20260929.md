# VoxGest Core5 Next Architecture Direction — 2026-09-29

## Decision

The streaming semantic-geometry feasibility experiment is promising but not safe for word emission.

Confirmed findings from commit `7249ca6259f46b7090120196211fd4a32bfa0b68`:

- rolling windows can surface intended Core5 classes before legacy EVENT_TIMEOUT in many saved events;
- stability alone produces unacceptable non-sign false peaks;
- exploratory geometry reduces but does not eliminate false positives;
- current 225 normalized tensor preserves hand direction/shape but loses valid hand-to-face distance because hands are independently scaled;
- raw landmarks preserve the relational geometry needed for hand-to-face/body reasoning;
- Face Landmarker is not required for the current Core5 vocabulary.

## Recommended architecture

Keep the existing 48x225 sequence stream unchanged for temporal handshape/motion recognition.

Add a second compact semantic-geometry stream derived BEFORE independent hand scaling from raw pose+hand landmarks:

- shoulder-width-normalized wrist/palm-to-face vectors;
- hand height relative to eyes/mouth/shoulders;
- fingertip-to-mouth/nose/eye distances;
- elbow/forearm angles;
- palm orientation;
- finger articulation descriptors;
- inter-hand geometry;
- timestamped motion features.

Fuse only after offline evidence supports it.

Conceptual path:

raw landmarks
  -> stream A: existing 48x225 classifier
  -> stream B: semantic geometry
  -> OOD/in-vocabulary rejector
  -> temporal stability / duplicate suppression
  -> accepted word

## Rejection priority

The next major blocker is not EVENT_TIMEOUT alone. It is false acceptance of unsupported motion.

Use FSL-105 only for training/evaluation development:

Positive:
- Core5 classes

Negative:
- non-Core5 FSL-105 classes
- validated neutral pre/post regions

Keep Samsung random-wave/partial events sealed as diagnostic tests.

Do not train the rejector on operator-labelled Samsung diagnostics.

## Boundary annotations

Streaming research still needs independently marked:
- sign start;
- sign end;
- holds;
- return-neutral;
- safe-rearm.

Use these labels only for event/stream timing research, not as classifier ground truth.

## Face policy

Do not add full Face Landmarker now.

Existing pose face anchors are sufficient for current Core5 feasibility. Revisit selected facial landmarks only when vocabulary requires precise cheek/chin/lip/eyebrow or non-manual distinctions.

## Android policy

Do not implement streaming acceptance in the production app yet.

Continue in the separate recognition-lab package only.

The modern VoxGest UI remains frozen and protected.

## Next exact work order

1. Build leakage-safe FSL-105 Core5-vs-Other OOD dataset using the same extraction contract.
2. Train/evaluate a lightweight OOD rejector offline.
3. Build compact raw-landmark semantic geometry features normalized by body scale.
4. Test fusion of classifier confidence + OOD score + geometry plausibility on held-out FSL-105 development data.
5. Evaluate sealed Samsung waves/partials and saved positives.
6. Collect annotated sign-end/rearm data only after the rejection experiment is ready to consume it.
7. Authorize Android streaming only if false-accept behavior is materially improved without sacrificing Core5 recall.
