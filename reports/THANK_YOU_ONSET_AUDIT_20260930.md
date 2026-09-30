# THANK YOU onset investigation — 2026-09-30

Inspected exact shipped GLB SHA256 1f566e98e020b8e46e8a625c3910794be86a2017392093950f467bad68c7689b. V4 and all Core3/source assets remain unchanged. No new derivative or GLB was created: the reported 0.9–1.0s snap has not been confirmed at the corresponding animation time.

## Timing result

User clarified the time is measured from pressing Play, not from an animator time readout. GLB time 0.9–1.0 corresponds to frames54–60; the wider frames48–66 (0.8–1.1s) are entirely static. All corrected world matrices in that interval are exactly equal, and exported translation/scale channels are unchanged. Quaternion angular calculations can show approximately 0.000003 degrees from floating point arithmetic, not actual changed keys. Wrist translation, elbow translation, palm normal and finger poses do not change there. LINEAR interpolation joins identical keys.

The local Core3FilamentHostView starts its clock on the first rendered playback frame, applies elapsedSeconds without a speed multiplier, then updates bone matrices. This is a local source inspection, not proof that the owner's APK uses identical code. Input-to-start delay alone would not explain a later 2.12s event appearing at 0.9s; a different time origin, seeking/rate, timing estimate, playback state or different owner code must be checked, not assumed.

## Nearby transition and face audit

A separate right distal ring-finger change remains 34.917646 degrees at frames127–128 (2.116667–2.133333s). It is a candidate for review, not established as the reported Samsung event. Sampled source/candidate frames54,60,126–129 were inspected. Stills do not establish full-speed visual acceptance. Aggregate wrist/palm/elbow steps for preparation frames102–135 and face-adjacent frames135–180 are in the JSON report. Local translations use rig units; world trajectory distances are metres.

Existing V4 collision evidence covers all243 frames and27 surface-pair tests with zero flags. Face clearance frames129–180 has zero signed-normal penetration flags. These scoped diagnostics do not prove every possible collision or intentional FSL contact. No new correction means no new post-correction export/clearance pass is claimed.

## Gates

SOURCE=PENDING_REVIEW (source mapping approval not supplied).
MECHANICAL=REPORTED_INTERVAL_STATIC; SEPARATE_TRANSITION_FLAG_REQUIRES_REVIEW.
FULL_SPEED_VISUAL=OWNER_SUSPECTED_SNAP; TIMING_CORRELATION_PENDING.
COLLISION=EXISTING_V4_SCOPED_CHECKS_PASS, unchanged.
EXPORT=EXISTING_V4_UNCHANGED; NO_NEW_DERIVATIVE.
CORE3_OWNER_RUNTIME=PASS, per user report.
NINE_ACTION_OWNER_TECHNICAL_PLAYBACK=PASS, per user report.
YES/NO/UNDERSTAND_OWNER_VISUAL=REVIEW_PENDING.
LISTEN_READY=false for all new actions.

## Exact owner evidence needed

A screen recording beginning before Play and continuing through the snap; the tested APK SHA/commit; and a short playback trace containing selected animation name/index, request/start timestamp and the exact seconds passed to applyAnimation for frames surrounding the snap. This is diagnostic evidence, not a request to change production UI. If an existing trace provides these fields, supply it rather than rebuilding. No global smoothing or Core3 changes are justified by the current evidence.
