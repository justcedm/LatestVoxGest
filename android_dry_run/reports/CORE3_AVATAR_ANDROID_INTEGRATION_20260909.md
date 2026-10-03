# VoxGest CORE3 Avatar Android Integration — 2026-09-09

## Current release status

`VOXGEST_CORE3_AVATAR_ANDROID_REVIEW_REQUIRED`

The immutable Astra package is verified and imported byte-for-byte. Android implementation and physical Samsung playback gates are still in progress; no Android-ready claim has been made.

## Phase 0 — protected baseline

- Repository: `D:\BSIT 3RD YEAR\New VovGest\android_dry_run`
- Branch: `defense-ui-refresh-20260906`
- Existing dirty and untracked user work was preserved.
- Checkpoint patch: `reports\CORE3_AVATAR_ANDROID_PHASE_0_BASELINE_20260909.patch`
- Baseline command: `gradlew.bat testDebugUnitTest assembleDebug`
- Baseline result: PASS — 61 tests, `BUILD SUCCESSFUL` in 28 seconds.
- Baseline APK: 158,862,375 bytes; SHA-256 `E2EEE5B9A7A67E29B2458A21D64A6E917C0CDA45FB306B22C303D52EF3433910`.
- `DynamicWordAcceptanceGate.kt` retains `(System.nanoTime() / 1_000_000L)`.
- ADB serial previously used by this workspace: `R5GYC0M1M4P` (`SM-A566B`, Android 16). The device disappeared after the Phase 0 ADB daemon restart and must reconnect before any physical result can be recorded.

## Phases 1–2 — architecture and renderer decision

- App stack: Android Gradle Plugin 8.5.2, Kotlin 2.0.21, Java 17, Compose BOM 2024.10.01, minSdk 26, compile/targetSdk 34.
- Existing renderer dependencies: Filament Android, Filament Utils Android, and gltfio Android 1.71.4.
- Renderer selected: Google Filament/gltfio 1.71.4 using `ModelViewer` and the gltfio `Animator` API.
- Why: these maintained Google libraries are already present and baseline-build-compatible, load GLB/glTF 2.0 with embedded resources and skeletal clips, expose named animation clips, and avoid a framework or Compose upgrade.
- Native dependencies added by this sprint: none. The existing Filament native dependencies will be validated from the final APK for 16 KiB ZIP and ELF alignment.
- Compatibility basis: Android's official guidance requires AGP 8.5.1+ for correct 16 KiB packaging of uncompressed native libraries; this project uses AGP 8.5.2. Final `zipalign -c -P 16 -v 4` and ELF LOAD alignment checks remain mandatory.
- The legacy `AvatarHelloPreviewActivity`, MP4/canvas experiments, arbitrary-word routing, and old disabled `SceneAvatarHostView` path are not being reused as the final Guide architecture.

## Phase 3 — immutable package import

- Astra source: `D:\VOXGEST_AVATAR_WORK\20260906\output\android_handoff_v1`
- Android asset directory: `app\src\main\assets\avatar\core3`
- GLB filename: `voxgest_avatar_B32_CORE3_RC2.glb`
- GLB size: 28,123,308 bytes.
- Source GLB SHA-256: `30F13FB65E7557992A8C3109460A790A69161E69F3BA9771A1E64A33F98294F8`.
- Android GLB SHA-256: `30F13FB65E7557992A8C3109460A790A69161E69F3BA9771A1E64A33F98294F8` — MATCH.
- Source animation manifest SHA-256: `F0D8FAB01A36531BD17920D2C3408CD68F35E8D973EE060DFD133F67A2910C2D`.
- Android animation manifest SHA-256: `F0D8FAB01A36531BD17920D2C3408CD68F35E8D973EE060DFD133F67A2910C2D` — MATCH.
- Imported allowlist: HELLO → `FSL_HELLO`, MILK → `FSL_MILK`, RICE → `FSL_RICE`.
- Original Astra files were not modified and no Android optimization copy was created.

## Gate matrix

| Gate | Result |
|---|---|
| Immutable source/Android checksums | PASS |
| Baseline unit tests/build | PASS |
| HELLO Samsung playback | PASS |
| MILK Samsung playback | PASS |
| RICE Samsung playback | PASS |
| Materials and visible hands/fingers | PENDING |
| Clip isolation and neutral reset | PENDING |
| Lazy load and failure isolation | PENDING |
| Lifecycle and memory profile | PENDING |
| Guide Watch Sign | PENDING |
| Final crash/logcat sweep | PENDING |
| Protected recognition/model assets unchanged | PENDING final comparison |

## Exact next task

Rebuild the HELLO-only camera fix (`ModelViewer` with no default orbit manipulator), install it on Samsung, and repeat the HELLO visual/material/hand gate before enabling MILK or RICE.

## Phase 4–7 checkpoint — first HELLO attempt

- Integration build: PASS — 69 JVM tests, zero failures/errors, `assembleDebug` PASS.
- Integration APK before camera fix: 186,985,954 bytes; SHA-256 `3A2A8BF04FCF62C8A30E605410B99C6F46AE324DFFAA8E853482CE33FBD2CB2D`.
- APK growth from protected baseline: 28,123,579 bytes. No new native dependency was added; growth is the 28,123,308-byte verified GLB plus packaging metadata.
- Android 16 KiB ZIP-alignment check: PASS using Build Tools 37.0.0 `zipalign -c -P 16 -v 4`.
- Connected device: `R5GYC0M1M4P`, Samsung `SM-A566B`.
- Existing installed normal app cold launch: PASS, 880 ms, no crash signature.
- New integration normal app cold launch: PASS; Guide HELLO detail shows `AVATAR AVAILABLE` and `Watch Sign`.
- Lazy-load evidence: no CORE3 renderer logs before Watch Sign; `RENDERER_CREATED` occurs only after the explicit action.
- Runtime asset verification on Samsung: PASS — 28,123,308 bytes and expected SHA-256.
- Filament reported the exact named clips `FSL_HELLO`, `FSL_MILK`, and `FSL_RICE` and an unchanged asset root.
- First-frame time: 565 ms; ready/load time: 567 ms; memory-map/checksum time: 51 ms.
- HELLO command path: `NEUTRAL_RESET` → `PLAY_REQUEST FSL_HELLO` → `PLAY_COMPLETE` after the non-looping 1.6333333-second duration.
- Crash scan: no FATAL EXCEPTION, ANR, SIGSEGV, or OutOfMemoryError.
- Visual result: FAIL — screenshot showed the viewport clear color but no character.
- Root cause: Filament 1.71.4 `ModelViewer(SurfaceView)` creates a default orbit manipulator targeting `(0, 0, -4)`. `render()` reapplied that manipulator and overwrote the explicit asset-native camera, placing the untransformed avatar outside view. The fix supplies `manipulator = null`; the asset root remains untouched.
- Memory after the blank HELLO load: TOTAL PSS 967,776 KB; TOTAL RSS 1,065,880 KB; Java Heap 13,944 KB; Native Heap 339,192 KB; Graphics 503,016 KB. The process survived, but retention after closing must be measured before approval.
- Teardown result: FAIL — the first Back test produced native SIGSEGV at 11:54:18.294. `ModelViewer`'s private SurfaceView detach listener destroyed the native engine before the host's second cleanup call. Fix in progress: explicit idempotent model release before dialog removal; detach only drops managed references and never invokes the invalidated viewer.
- Evidence: `reports\evidence\core3_20260909\core3_guide_watch_sign.png` and `core3_hello_avatar_blank_camera_issue.png`.

### HELLO camera-fix retest

- Build/test: PASS — 69 tests; assembly PASS.
- Load: PASS — 562 ms ready, 560 ms first frame, 41 ms memory map/checksum.
- Animation command path: PASS — neutral reset, named HELLO request, 1.6333333-second completion.
- Crash scan during playback: PASS.
- Visual result: still blank.
- Corrected diagnosis: the camera manipulator was a valid risk and remains disabled, but was not the blank-screen cause. Samsung SurfaceFlinger evidence shows the `SurfaceView` at a negative layer beneath the Activity while Compose's Avatar player is hosted in a separate Dialog window. The surface rendered behind the opaque dialog. The next retest uses Filament's supported `TextureView` ModelViewer constructor so the viewport is composited inside the dialog hierarchy.
- HELLO loaded memory: TOTAL PSS 946,961 KB idle / 947,388 KB playing; TOTAL RSS 1,044,888 KB idle / 1,045,256 KB playing; Java Heap 14,652 KB idle / 14,712 KB playing; Native Heap 336,064 KB idle / 336,060 KB playing; Graphics 503,016 KB idle and playing.
- First TextureView attempt correctly entered isolated `Avatar unavailable` ERROR state instead of crashing because Android rejects background drawables on TextureView. Removed the unsupported `setBackgroundColor` call; Filament's renderer clear color and the parent viewport provide the background.

### HELLO final staged gate

- TextureView composition: PASS. The Avatar is visible within the Compose Dialog on Samsung.
- Character framing: PASS — head, shoulders, elbows, both hands, fingers, and full body are visible without cropping.
- Materials: PASS by captured device evidence — skin, eyes, hair, blue shirt, white skirt, and shoes render without pink/missing textures.
- HELLO pose: PASS — raised signing hand is visible near the face; inactive arm remains coherent.
- Neutral entry/return: PASS — explicit neutral reset precedes playback and the 1.6333333-second endpoint is visibly neutral.
- Replay: PASS — two additional complete Replay cycles.
- Reset: PASS.
- Final HELLO load/first-frame time: 516/515 ms; package memory-map/checksum time: 55 ms.
- Loaded/playing memory is consistent with the prior profile (~947 MB TOTAL PSS, ~1,045 MB TOTAL RSS, ~336 MB Native Heap, 503 MB Graphics).
- After closing the player: TOTAL PSS 211,701 KB; TOTAL RSS 246,728 KB; Java Heap 8,996 KB; Native Heap 58,572 KB; Graphics 50,512 KB.
- Teardown: PASS — process remained alive, `UNLOADED` logged, and no FATAL, ANR, SIGSEGV, or OOM occurred.
- Evidence: `core3_hello_mid_textureview_fix.png` and `core3_hello_neutral_textureview_fix.png`.
- HELLO passed, so MILK and RICE are now enabled for the next physical gate.

## Phase 9 — MILK and RICE Samsung gate

- MILK: PASS. `FSL_MILK` played for the exact non-looping 2.0-second manifest duration. Captured Samsung frames show active-hand motion near the upper torso, visible fingers, coherent elbow deformation, and a stable inactive arm. Neutral endpoint and Replay pass.
- RICE: PASS. `FSL_RICE` played for the exact non-looping 1.6333333-second manifest duration. Captured Samsung frames retain the near-mouth active hand and supporting palm; fingers remain visible and both hands stay inside frame. Neutral endpoint and multiple Replay cycles pass.
- Guide integration: PASS for individual HELLO, MILK, and RICE entries; each shows `Avatar Available` and `Watch Sign`.
- Materials/framing remained stable across all three clips.
- No FATAL, ANR, SIGSEGV, or OOM occurred during either sign gate.
- Evidence: `core3_milk_f1.png`, `core3_milk_neutral.png`, `core3_rice_f1.png`, `core3_rice_f3.png`, and `core3_rice_neutral.png`.
- Exact next task: run both mandatory clip-isolation orders in one retained renderer via the compact HELLO/MILK/RICE selector, then test lifecycle and final memory/crash gates.
