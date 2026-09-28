# VoxGest Remote Avatar Survey Protocol — 2026-09-28

## Purpose

This document coordinates Earle's remote Astra avatar-calibration work with the main VoxGest Android project and the physical Samsung device held by the project owner.

The two machines are in different locations. Therefore calibration, packaging, Android integration, physical-device testing, and final acceptance must be separated explicitly.

## Survey vocabulary priority

The immediate bidirectional survey target is aligned with the current Core5 recognition vocabulary:

1. HELLO
2. THANK YOU
3. YES
4. NO
5. UNDERSTAND

HELLO already exists in the known-good Core3 runtime. For the survey path, Earle/Astra must prioritize the four missing Avatar concepts:

1. THANK YOU
2. YES
3. NO
4. UNDERSTAND

The other current candidates remain useful research/test candidates but are lower priority until the four Core5 Avatar actions pass:

- IM FINE
- HOW ARE YOU
- GOOD EVENING
- KNOW
- WRONG

## Current remote calibration state

Known experimental candidates already exist on Earle's side:

- THANK YOU: FSL_THANK_YOU__PURCHASED_REVIEW_C7
- YES: FSL_YES__PURCHASED_REVIEW_C8
- NO: FSL_NO__PURCHASED_REVIEW_C7
- UNDERSTAND: FSL_UNDERSTAND__SOURCE_FIT_V7
- IM FINE: corrected SOURCE_FIT_V7 candidate
- HOW ARE YOU: corrected SOURCE_FIT_V7 candidate
- remaining package candidates: GOOD EVENING, KNOW, WRONG

These are test candidates, not approved runtime/FSL actions.

Do not set listen_ready=true merely because a clip exists, exports, or passes structural checks.

## Sequencing decision

The previous THANK_YOU-first safety gate remains the acceptance gate.

It is acceptable that the remaining eight candidates have already been prepared as unapproved test candidates, because this does not constitute promotion.

Acceptance order for the survey is:

THANK YOU
-> YES
-> NO
-> UNDERSTAND

Only after each word passes the defined gates should it be promoted to the survey handoff.

Do not spend survey-critical time polishing the five lower-priority words unless the four Core5 words are already packaged for owner-side Android testing.

## Acceptance gates per word

A candidate must pass or be explicitly marked pending for each gate:

1. SOURCE_REFERENCE
   - correct source/reference clip identified
   - frame count/timing preserved
   - no accidental sign substitution

2. MECHANICAL
   - finite transforms
   - no large discontinuities/snaps
   - no impossible scale changes
   - acceptable joint/mesh deformation
   - no obvious self-intersection/contact failure

3. HUMAN_MOTION_VISUAL
   - normal-speed playback beside source
   - preparation/stroke/hold/recovery reviewed
   - wrist/palm/finger motion visually coherent
   - contact and face-relative placement inspected

4. EXPORT
   - candidate GLB loads
   - animation name/duration correct
   - skeleton/skin/material integrity checked
   - source/derivative hashes recorded

5. ANDROID_ENGINEERING
   - local Android test app builds
   - clip can be enumerated and requested
   - Core3 HELLO/MILK/RICE regressions remain intact where included

6. OWNER_DEVICE
   - physical Samsung SM-A566B test by project owner
   - neutral render, framing, playback, replay, return-to-neutral, crash/logcat checked

7. FSL_APPROVAL
   - linguistic correctness must not be claimed from engineering checks alone
   - qualified/reference-based review remains separate where required

Only OWNER_DEVICE + required linguistic validation may justify final user-facing readiness.

## Canonical runtime direction

Core3 remains the canonical Android avatar platform.

Known-good runtime asset:

android_dry_run/app/src/main/assets/avatar/core3/voxgest_avatar_B32_CORE3_RC2.glb

Known-good existing actions:

- HELLO
- MILK
- RICE

The new Earle package is a motion/calibration source. Do not replace the stable Core3 platform without an explicit integration decision.

## Remote responsibility split

### Earle / Astra

Responsible for:

- Blender/source inspection
- motion fitting/calibration
- normal-speed source comparison
- mechanical/collision checks
- candidate Action naming
- export validation
- candidate manifest
- deterministic hashes
- local Android test-app build
- regression checks that do not require the owner's Samsung
- documentation and resumable checkpoints

### Project owner / main machine

Responsible for:

- preserving the modern production UI
- final integration branch
- physical Samsung installation
- Filament device playback
- frame pacing/jank measurement
- final LISTEN resolver integration
- final survey APK
- physical evidence and rollback

Earle's side must not make production LISTEN ready by assumption.

## Remote handoff package

For each survey candidate package, Earle/Astra should create one local handoff directory/ZIP with:

- candidate GLB intended for Android testing
- animation manifest JSON
- action-name mapping JSON
- SHA256SUMS.txt
- calibration decision report
- Android integration/readme instructions
- build/test summary
- exact source branch and commit
- explicit status matrix for THANK YOU / YES / NO / UNDERSTAND
- rollback instructions

Suggested naming:

VOXGEST_CORE5_AVATAR_CANDIDATES_20260928_v1.zip

The ZIP must not silently contain purchased/private source material that is prohibited from redistribution.

Private source .blend files, purchased originals, source videos, and fitted private geometry remain outside Git unless licensing and project policy explicitly permit otherwise.

If the derived candidate GLB is also restricted by the purchased asset license, transfer it privately to the project owner rather than committing it.

## GitHub content

GitHub should contain reproducible/public-safe material:

- reports/ASTRA_LIVE_HANDOFF.md
- calibration audit/results JSON where safe
- scripts used for calibration/validation
- action/manifest schemas
- hashes
- README/integration instructions
- status tables
- exact local handoff ZIP filename and SHA256

Do not commit private source recordings or purchased source assets.

## Owner-side information Earle should rely on from GitHub

Read these before packaging:

- docs/avatar/VOXGEST_AVATAR_REBUILD_REFERENCE_20260927.md
- docs/VOXGEST_SURVEY_READINESS_20260927.md
- docs/VOXGEST_MODERN_UI_RECOVERY_AND_NEXT_RECOGNITION_20260928.md
- reports/ASTRA_LIVE_HANDOFF.md
- docs/AVATAR_ARCHITECTURE_DECISIONS.md
- avatar_handoff/GITHUB_PROTOCOL.md

## Android survey integration target

Final product target is one APK with:

MODERN FIVE-TAB UI
+
CORE5 RECOGNITION
+
LISTEN / AVATAR
+
BOARD FALLBACK

Avatar survey concepts should align with recognition where possible:

HELLO
THANK YOU
YES
NO
UNDERSTAND

Unsupported/unapproved speech concepts must show unavailable/fallback rather than play the wrong animation.

## Performance target

Source review/calibration may use 60 FPS.

Android runtime should target display-vsync-synchronized smooth playback on capable devices, but do not claim 60 FPS without device measurement.

Animation time should be elapsed-time based, not tied to arbitrary Compose recomposition counts.

## Required Earle/Astra final status

THANK_YOU_SOURCE=
THANK_YOU_MECHANICAL=
THANK_YOU_VISUAL=
THANK_YOU_EXPORT=

YES_SOURCE=
YES_MECHANICAL=
YES_VISUAL=
YES_EXPORT=

NO_SOURCE=
NO_MECHANICAL=
NO_VISUAL=
NO_EXPORT=

UNDERSTAND_SOURCE=
UNDERSTAND_MECHANICAL=
UNDERSTAND_VISUAL=
UNDERSTAND_EXPORT=

CORE3_REGRESSION=
ANDROID_LOCAL_BUILD=
CANDIDATE_GLB=
CANDIDATE_GLB_SHA256=
HANDOFF_ZIP=
HANDOFF_ZIP_SHA256=
PRIVATE_FILES_COMMITTED=NO
LISTEN_READY=false until owner-side/device and required FSL gates pass
NEXT_EXACT_ACTION=
