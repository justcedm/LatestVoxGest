# VoxGest Core5 Boundary V2 Result + Annotation Protocol — 2026-09-28

## Latest confirmed result

The latest Core5 boundary investigation is preserved in commit:

`fa36f8a522e5f3d5ea94143a2dc5873d0616b6d3`

Key conclusion:

Boundary V2 is **not qualified** for Android deployment yet.

The saved-event analysis confirms the legacy root cause:

- automatic completion depends on hand landmarks disappearing;
- when hands remain visible, the event can remain open until timeout.

However, replacing that rule with simple motion-settle logic is not yet safe.

A 432-configuration offline search using timestamp-aware XY hand translation, articulation and arm motion found:

- safest explored settings resolve at most 1/9 prior timeout events;
- a more aggressive setting resolves 5/9 timeouts but creates premature-cut risk on real positives and candidate triggers on non-sign events;
- causal filtering does not establish a safe operating point;
- capture-quality metrics alone do not separate false-accepted non-signs.

Therefore no Android Boundary V2 implementation should be promoted from this search.

## What is missing

The saved events do not contain independently marked ground-truth timestamps for:

- actual sign start;
- actual linguistic sign end;
- intentional hold;
- return-to-neutral;
- safe rearm point.

Without these labels, the system can measure landmark motion but cannot know whether a low-motion region is:
- a legitimate hold inside the sign;
- the true end of the sign;
- post-sign idle;
- or tracking jitter.

## Next data collection: small annotated boundary sample

This is diagnostic calibration data for the event detector, not linguistic classifier training.

Recommended sample:

- HELLO ×3
- THANK YOU ×3
- YES ×3
- NO ×3
- UNDERSTAND ×3

Total: 15 deliberate sign trials.

Also:
- neutral/no-sign ×3
- arbitrary non-FSL motion ×3

Total diagnostic session: 21 trials.

### Required timing labels per deliberate sign

For each trial record or annotate:

- PREP_START
- SIGN_START
- HOLD_START if applicable
- HOLD_END if applicable
- SIGN_END
- RETURN_NEUTRAL
- SAFE_REARM

The most important labels are SIGN_END and SAFE_REARM.

### Practical collection method

Preferred:
- save the raw diagnostic event and synchronized preview/video or frame-index reference;
- after each short batch, human-review the clip and mark timestamps.

If synchronized video cannot be saved safely, add diagnostic buttons/markers that allow the operator to tap:
- START SIGN
- END SIGN
- NEUTRAL

These markers must not alter classifier output or automatic boundary behavior; they are annotation-only.

### Hold-point rule

A hold must be labelled explicitly when the sign contains a valid low-motion pose before the sign is linguistically complete.

This is essential because a motion detector must not terminate a sign simply because velocity drops temporarily.

## Evaluation protocol

Split the 15 sign trials into:
- development/tuning subset;
- held-out evaluation subset.

Do not tune and score on the exact same events.

Boundary candidate must demonstrate:
- zero held-out premature cuts;
- substantially fewer timeout failures;
- no increase in ghost events;
- no increase in non-sign candidate events;
- stable behavior across all five Core5 classes.

## Recognition and rejection remain separate

YES still has a separate classifier/domain issue.

Non-sign false accepts remain a rejection problem.

Boundary V2 should not be judged by classifier correctness, and the classifier should not be retrained merely to fix timeout.

## OOD research next

A future binary Core5-like vs Other/Neutral rejector can be evaluated using FSL-105 only:

Positive:
- Core5 classes

Negative:
- non-Core5 FSL-105 classes
- validated neutral pre/post regions

Samsung waves/partials remain sealed diagnostic tests and must not enter training.

## Modern UI protection

Keep the installed modern VoxGest app frozen.

Use the separate recognition-lab package for all boundary experiments.

Do not replace the modern app with an older diagnostic APK.

## Next exact action

Collect the 15 deliberate Core5 trials plus six negative/neutral trials with independently marked sign-end/rearm points, then rerun the boundary search using held-out validation before implementing Android Boundary V2.
