# Hand Occlusion Stress Test

Status: implementation test pending; no physical result is claimed yet.

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

