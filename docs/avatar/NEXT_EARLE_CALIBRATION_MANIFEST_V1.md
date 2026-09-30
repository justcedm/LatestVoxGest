# Next Earle calibration handoff — v1

Status: proposed, **not** authorization to promote current candidate clips. The existing 52,887,544-byte private GLB (SHA256 `1f566e98e020b8e46e8a625c3910794be86a2017392093950f467bad68c7689b`) remains unchanged. All new clips must be separate versioned candidates and `listen_ready=false` until source, FSL, owner-device, and runtime gates pass.

## First resolve current review findings

- `UNDERSTAND`: owner reports the current hand target is around the neck, whereas their expected target is the forehead. Obtain an authoritative FSL reference and qualified reviewer decision on location, orientation, handshape and motion. Do not mechanically move the hand to the forehead based on this note alone. Recalibrate only after the source is settled; retest on Samsung.
- `THANK_YOU`: current Samsung recording shows a shared-neutral hands-forward to clip-start arms-down discontinuity around recording 0.9–1.1 s. Inspect the clip frame-0 pose versus the current shared neutral pose and the renderer transition policy. The issue may affect other candidate starts. Preserve the current GLB and provide a candidate correction only after controlled comparison.

## Small sentence-enabling tranche

| Priority | Concept requested | Proposed runtime action | Source/reference gate | Timing and device gate |
| --- | --- | --- | --- | --- |
| 1 | self-introduction/name | `SELF_INTRO_NAME` **or** `NAME`, selected by FSL reviewer; not both by default | FSL-qualified review of the whole phrase and required semantic scope; retain source ID, signer, rights and reviewer decision | Full observed preparation, stroke, hold and return; manifest duration; Samsung play/replay/neutral/framing and sentence transition test |
| Conditional | I/me | `I_ME` only if FSL-reviewed phrase plan requires it | Explicit grammar decision that a separate action is needed | Same gates; exclude from handoff count until decision |
| 2 | you | `YOU` | Validated FSL reference, source/license and expert review | Same gates; check pointing direction and body-relative target |
| 3 | please | `PLEASE` | Validated FSL reference, source/license and expert review | Same gates; check hand–body clearance and repeated motion if applicable |
| 4 | help | `HELP` | Validated FSL reference, source/license and expert review | Same gates; check both-hand relation if applicable |
| 5 | sorry | `SORRY` | Validated FSL reference, source/license and expert review | Same gates; check location/orientation and collision |

Five concepts are mandatory proposals; `I_ME` is one conditional sixth. Do not add `MY` or `IS` merely to mimic English grammar. Before rig work, the FSL reviewer must approve a `GREETING_SELF_INTRO(name)` phrase plan, its actual FSL action order, and whether `SELF_INTRO_NAME`, `NAME`, or `I_ME` is needed.

For each approved candidate Earle should deliver a separate named clip and manifest row with reference/version, provenance and license, reviewer decision, motion type, 60-fps source timeline or documented native rate, authored start/active/hold/end phases, measured duration, shared-neutral in/out pose and transition notes, collision/penetration audit, one-skin/mesh integrity check, independent asset hash, and explicit `UNAPPROVED_TEST_CANDIDATE` status. Do not pad or shorten a sign to hit a guessed duration. Device acceptance requires exact action enumeration, start/complete/replay, neutral return, full-body/active-hand visibility, no obvious snap/clipping/deformation/crash, memory/frame-pacing observation, owner visual review and FSL-qualified linguistic review. These gates are separate: technical playback never implies linguistic approval.

## Separate A–Z workload

The 26 `FSL_FS_A`–`FSL_FS_Z` clips are a separate package, **not included in the five-concept count**. Each needs its own validated FSL source, static/dynamic determination, authored hold/motion and transition, repeated-letter readability, calibrated timing and the same Samsung/linguistic gates. No clip currently exists in the private 12-action manifest. Historical ASL assets must not be repurposed.
