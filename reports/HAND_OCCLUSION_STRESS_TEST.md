# Hand Occlusion Stress Test

Status: implementation/unit tests pass; physical result is pending and is not
claimed yet.

Profile under test: `MAPUA14_LIVE_SEGMENT_V1`.

## Required sequence

1. Both hands visible and separate.
2. Hands touch.
3. Hands cross without changing anatomical identity.
4. One hand occludes the other.
5. Hands separate and are reacquired.

For every phase, record:

| Phase | Left present | Right present | Reported handedness | Left wrist | Right wrist | Hand distance | Slot changes | Dropout duration | Result |
|---|---|---|---|---|---|---:|---:|---:|---|
| Separate | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED |
| Touch | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED |
| Cross | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED |
| Occlude | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED |
| Separate/reacquire | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED | NOT_YET_TESTED |

Acceptance rule: anatomical identity must follow handedness, pose-wrist anchors,
and temporal wrist continuity—not image-X ordering. Ambiguous anatomy fails
closed. Missing landmark coordinates are never fabricated.

## Automated evidence

The JVM suite verifies:

- transient handedness-category errors are overridden only by stronger wrist
  continuity and pose-anchor evidence;
- a gradual anatomical hand crossing preserves LEFT/RIGHT even after their
  image-X positions reverse;
- an exact unresolved two-hand collision fails closed;
- a short dropout can reacquire the prior anatomical track without inserting
  a fabricated hand in the missing frame; and
- a non-monotonic timestamp fails closed without advancing track state.

Physical separate/touch/cross/occlude/separate evidence remains required on the
Samsung and will be recorded in the table above.
