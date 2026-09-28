# VoxGest Remote Avatar Status — 2026-09-28

## Latest Earle/Astra checkpoint reported by project owner

Local Earle/Astra checkpoint commit: `54d14a2`.

Push authentication failed on Earle's machine. The last confirmed remote checkpoint before this documentation update was `fcacf3ffdb80bdf8d1bdf3e5e08dd316fa7a9531`.

Reported status:

```text
THANK_YOU_SOURCE=FAIL
Reason: fingertips are below the chin instead of matching the source lip placement.
THANK_YOU_MECHANICAL=PENDING
THANK_YOU_VISUAL=PENDING
THANK_YOU_EXPORT=BLOCKED

YES_SOURCE=PENDING
YES_MECHANICAL=PENDING
YES_VISUAL=PENDING
YES_EXPORT=BLOCKED

NO_SOURCE=PENDING
NO_MECHANICAL=PENDING
NO_VISUAL=PENDING
NO_EXPORT=BLOCKED

UNDERSTAND_SOURCE=PENDING
UNDERSTAND_MECHANICAL=PENDING
UNDERSTAND_VISUAL=PENDING
UNDERSTAND_EXPORT=BLOCKED

CORE3_HELLO_REGRESSION=PASS
CORE3_MILK_REGRESSION=PASS
CORE3_RICE_REGRESSION=PASS

LOCAL_ANDROID_BUILD=PASS
CANDIDATE_GLB=NO_NEW_SURVEY_EXPORT
HANDOFF_ZIP=NOT_CREATED
PRIVATE_FILES_COMMITTED=NO
LISTEN_READY=false
OWNER_DEVICE_TEST=PENDING
```

## Interpretation

This is a useful failure, not a release candidate.

THANK YOU has a source-placement mismatch, so export must remain blocked. The candidate must be corrected against the source before Android packaging.

The existing Core3 HELLO/MILK/RICE action data remain unchanged according to the reported regression check.

No new survey GLB should be promoted until the four survey-priority actions have passed their engineering gates.

## Immediate priority

Survey Avatar order remains:

1. THANK YOU
2. YES
3. NO
4. UNDERSTAND

The other candidate words remain parked.

## THANK YOU correction target

The current discrepancy is explicit:

```text
candidate fingertips: below chin
source target: lip-relative placement
```

Do not compensate by moving the entire avatar or introducing a global transform.

The correction should be local to the sign motion and should preserve:

- shoulder/elbow trajectory;
- wrist orientation;
- palm orientation;
- finger configuration;
- timing;
- neutral entry/exit;
- Core3 regressions.

Review the full preparation/stroke/hold/recovery sequence at normal speed, not only a still pose.

## Source confirmation

The reported next action references:

`clips/7/0.MOV`

This file should be treated as a private/local source until its vocabulary mapping and redistribution rights are confirmed.

Before claiming SOURCE PASS, a human reviewer must confirm that the clip is the intended THANK YOU reference for this calibration lane.

If confirmation is unavailable, status remains:

`THANK_YOU_SOURCE=PENDING_REVIEW`

and linguistic approval must not be inferred from engineering alignment alone.

## Remote workflow

Earle/Astra should continue local calibration and safe reporting.

GitHub may contain:

- scripts;
- reports;
- hashes;
- manifests;
- source-to-candidate measurement summaries;
- screenshots/contact-sheet references if permitted.

Do not commit private source recordings or purchased/private avatar source files.

When a corrected survey candidate is ready, create a private handoff ZIP if licensing requires it and publish only its filename/hash/integration instructions to GitHub.

## Push recovery

On Earle's machine, do not recreate the local checkpoint from memory.

Preserve local commit `54d14a2`.

After authentication is restored:

```text
git status --short
git branch --show-current
git rev-parse HEAD
git log --oneline -3
git fetch origin --prune
git push origin avatar/astra-calibration-20260914
```

If remote history has advanced, do not force-push. Fetch, inspect divergence, and reconcile safely.

## Next exact action

1. Confirm the intended THANK YOU source mapping for `clips/7/0.MOV`.
2. Correct THANK YOU lip-relative hand placement.
3. Re-run source, mechanical, normal-speed visual, collision and Core3 regression checks.
4. If THANK YOU passes, export a test candidate and continue YES, NO and UNDERSTAND with the same gate discipline.
5. Keep `listen_ready=false` until owner-side Android and required FSL validation pass.
