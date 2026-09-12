# FSL-105 recognition event/freshness checkpoint — 2026-09-10

## Scope and repository

- Authoritative repository: `C:\VOXGEST_RECOVERY_20260910\New_VovGest_GIT`
- Authoritative Android workspace: `android_dry_run`
- D: was not accessed or modified.
- Listen, Avatar, and presentation UI were not changed by this checkpoint.

## Previously verified baseline

- Normal Sign routes to `STANDARD_FSL_FULLSIGN225`; legacy Demo remains separate.
- Runtime contract: `[1,20,225] -> [1,105]`, 105 exact indexed labels.
- Golden FullSign225 feature parity: PASS, maximum absolute difference `0.0`.
- Golden Android TFLite parity: PASS; fixture top-1 `BROWN` (index 9), probability difference approximately `5.86e-14`.
- Last completed unit-test run before this checkpoint: 78 tests, 0 failures, 0 errors.
- Previously installed Samsung performance evidence: CameraX RGBA analyzer approximately 9.7–11.1 FPS; landmark total approximately 81–91 ms median and 106–111 ms p95; TFLite approximately 1.16 ms. TFLite is not the live bottleneck.

## Source hardening added in this checkpoint

- Explicit event states: `IDLE -> PRIMING -> SIGN_ACTIVE -> CANDIDATE -> ACCEPTED -> WAIT_FOR_RELEASE`.
- A merely visible/held hand cannot start inference. Two consecutive deliberate-motion frames or a real post-release hand entry are required.
- Static/held signs remain supported after deliberate entry; active held frames continue filling the 20-frame window.
- An acceptance immediately disarms collection/inference. The same held sign cannot emit another event after a timer alone.
- Re-arm requires either three unusable landmark frames or three sustained changed-motion frames after the minimum wait.
- The rolling window is flushed on entry/re-arm/release, preventing cross-event frame contamination.
- Source timestamps now travel with each canonical FullSign225 frame.
- Runtime rejection now fails closed on unavailable, non-chronological, stale, overlong, or excessively gapped windows.
- Live logs now include event state/reason, wrist/fingertip/joint activity, activity variance, recent valid ratio, and exact freshness values:
  - `WINDOW_DURATION_MS`
  - `OLDEST_FRAME_AGE_MS`
  - `MEDIAN_FRAME_GAP_MS`
  - `MAX_FRAME_GAP_MS`
- Existing top-5 probability and exact gate-rejection logging remains in the Standard runtime.

## Tests added (not yet executed)

- Held visible hand remains `IDLE` until deliberate movement.
- A held accepted sign cannot re-arm on elapsed time alone.
- Three missing frames release and flush the event.
- Sustained changed motion re-arms while one motion frame does not.
- Rolling-window timing metrics are exact for a synthetic 20-frame sequence.
- Stale and excessive-gap windows reject before prediction acceptance.
- Developer diagnostics include event and freshness fields.

The source changes above require a fresh Gradle test/build run. Existing 78/78 results predate these changes and are not presented as verification of them.

## Immutable rollback evidence

- FullSign225 model SHA-256: `42D040EC2269D437546D327DECAACA32839ABDD6BB63B2D400063C90630E5D13`
- FSL-105 labels SHA-256: `BFA76D96ED10BF97F43CA80BCFCC5BADD3E96DF7EBE4C0654FC078552DA55FB6`
- CORE3 GLB SHA-256: `30F13FB65E7557992A8C3109460A790A69161E69F3BA9771A1E64A33F98294F8`
- CORE3 GLB bytes: `28,123,308`
- Protected clock expression remains: `(System.nanoTime() / 1_000_000L)`.

No model or label artifact was replaced, so the audited current model remains the rollback model.

## Required next verification

1. Run the Android unit suite and a clean-enough debug assembly without altering unrelated dirty-tree work.
2. Re-run both existing parity fixtures in the built APK.
3. Install the new APK on Samsung `R5GYC0M1M4P`.
4. Capture idle/open-hand and random-motion negative attempts; acceptance count must remain zero.
5. Capture deliberate FSL attempts with top-5, exact rejection reason, event transition, and freshness metrics.
6. Repeat one accepted sign without release; it must emit one token only. Release/re-arm, then repeat; it may emit one new token.
7. Report offline model metrics separately from live-system attempt metrics.
8. Retrain only if top-1 remains genuinely wrong after all parity, event, freshness, and performance gates pass.

## Current verdict

Physical Samsung evidence for this checkpoint is not yet available. Therefore this checkpoint is **review required**, not device ready.
