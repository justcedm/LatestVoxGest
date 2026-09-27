# VoxGest Survey Readiness Plan — 2026-09-27

## Immediate survey target

Survey date: Tuesday.

Primary goal before survey: produce one stable Android build with the same five FSL concepts working end-to-end as far as each subsystem is qualified:

1. HELLO
2. THANK YOU
3. YES
4. NO
5. UNDERSTAND

Do not broaden to 10–20 live-qualified words until the Core5 battery passes. Expansion beyond five is conditional on the five-word recognition and rejection gates.

---

## Priority 1 — Gesture recognition

### Current evidence

HELLO is not currently a retraining problem.

Nine saved Samsung HELLO tensors were replayed through Baseline, Native48 and SIM10. All 27 raw predictions were HELLO.

Five timely manually-ended Samsung HELLO events were accepted as HELLO. Four additional HELLO events reached EVENT_TIMEOUT but also had raw top-1 HELLO.

Therefore the current open problems are separated into:

- remaining four-class live qualification;
- automatic event completion / segmentation;
- unsupported/non-sign rejection;
- final app integration.

### Next required device battery

Using the validated diagnostic profile and manual event boundaries first:

- THANK YOU × 5
- YES × 5
- NO × 5
- UNDERSTAND × 5

Preserve raw prediction separately from acceptance result.

Then run negative/OOD trials:

- neutral / no intentional sign × 5
- arbitrary non-FSL hand movement × 5
- incomplete/aborted supported gesture × 5 where cleanly collectable

Only after those results are preserved should automatic event-boundary behavior be modified.

### 60 FPS skeleton UX concept

The recognition pipeline and the display overlay must remain separate.

Current Samsung evidence shows MediaPipe landmark results near 9–10 Hz. It is therefore incorrect to claim 60 FPS landmark inference.

A future Sign-page UX may render the skeleton overlay at display/vsync cadence (target 60 Hz where device capability allows) by interpolating the latest visual landmark states. This interpolation is presentation-only.

Do NOT feed UI-interpolated landmarks into the classifier unless a separate experiment validates that change.

Preferred architecture:

camera preview -> native camera cadence
MediaPipe -> timestamped landmark results at measured processing cadence
recognition -> raw timestamped feature contract
UI skeleton -> independently smoothed/interpolated render loop

Recognition accuracy and event-boundary correctness take priority over the 60 FPS visual overlay.

---

## Priority 2 — Hearing/speaker to Avatar feedback

### Canonical known-good runtime

Existing Core3 Android runtime has device-qualified playback for:

- HELLO
- MILK
- RICE

Canonical Android asset:

android_dry_run/app/src/main/assets/avatar/core3/voxgest_avatar_B32_CORE3_RC2.glb

### Earle/Astra package status

The Earle package is still useful as a motion/reference source, but the current experimental Actions are not yet final-runtime qualified.

On branch:

avatar/astra-calibration-20260914

the live handoff reports:

- THANK YOU transferred to the purchased 525-bone hierarchy as experimental review candidate C7.
- YES transferred as experimental review candidate C8.
- NO transferred as experimental review candidate C7.
- UNDERSTAND has a corrected SOURCE_FIT_V7 experimental candidate.
- IM FINE and HOW ARE YOU also have V7 corrected experimental candidates.
- none of these new words is currently listen_ready.
- Android/export/linguistic acceptance remains open.

For survey readiness, STOP expanding unrelated Avatar vocabulary and focus only on the four missing Core5 Avatar Actions:

- THANK YOU
- YES
- NO
- UNDERSTAND

HELLO already exists in Core3.

Each action must pass, in order:

1. source/reference comparison;
2. mechanical rig/collision review;
3. normal-speed human-motion visual review;
4. export validation;
5. Android neutral-render gate;
6. Android action playback;
7. Core3 regression;
8. FSL linguistic validation where required.

Do not label an experimental candidate as verified FSL merely because it animates.

### Avatar 60 FPS UX target

Earle calibration assets are authored/reviewed at 60 FPS, but Android smoothness must be measured independently.

Preferred Android runtime:

- Filament render loop synchronized to display vsync;
- animation time driven by elapsed timestamps rather than frame-index assumptions;
- target 60 Hz on capable devices;
- measure actual frame time and jank;
- lazy-load the Avatar;
- avoid Compose recomposition for every animation tick;
- reuse renderer where safe;
- preserve clean unload/lifecycle handling.

Do not claim 60 FPS unless device measurements support it.

---

## Vocabulary expansion after Core5

Do not promise 20 live-qualified signs by schedule alone.

Expansion gate:

Core5 manual device battery PASS
-> negative/rejection battery acceptable
-> automatic boundary reliable
-> production Sign route stable

Then expand in controlled batches:

Batch 2: +5 words
Batch 3: +5 words
Batch 4: +5 words

Every batch requires model/device evidence and must not silently become production-qualified from dataset availability alone.

---

## Documentation / GitHub policy

Every major engineering change must include:

- branch/worktree;
- HEAD/commit;
- exact files changed;
- model/asset SHA256 where applicable;
- build/test result;
- physical device result where applicable;
- evidence paths;
- rollback path;
- known limitations;
- next exact action.

Recognition, UI, and Avatar work must stay separated by branch/worktree until qualified.

Current Avatar reference on main:

docs/avatar/VOXGEST_AVATAR_REBUILD_REFERENCE_20260927.md

Current Avatar progress source:

reports/ASTRA_LIVE_HANDOFF.md on branch avatar/astra-calibration-20260914

---

## Survey readiness definition

Minimum useful survey build:

- Sign page can reliably capture and accept the five target FSL concepts under the approved runtime conditions;
- unsupported input does not trivially become a supported word;
- recognized text is visible and TTS path works where enabled;
- Listen page transcribes speech;
- HELLO plus any newly qualified Avatar Actions play smoothly and safely;
- unqualified Avatar concepts explicitly show unavailable rather than a wrong sign;
- Board fallback remains available;
- build and device evidence are committed/documented.

No claim of unrestricted FSL translation is permitted.
