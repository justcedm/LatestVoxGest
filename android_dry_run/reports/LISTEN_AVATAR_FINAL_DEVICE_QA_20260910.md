# VoxGest Listen Avatar Final Device QA — 2026-09-10

## Final release-blocking result

`VOXGEST_LISTEN_AVATAR_FINAL_READY`

| Gate | Result | Evidence |
|---|---:|---|
| LISTEN_EMBEDDED_CORE3 | PASS | Real Filament `TextureView` is embedded in normal Listen directly below transcript/microphone content. |
| UPPER_BODY_FRAMING | PASS | Bounds-derived frontal waist/upper-hip framing; head, shoulders, forearms, hands, and fingers physically visible. |
| REFERENCE_PRESENTATION | PASS | Centered interpreter, small headroom, pale neutral background, soft frontal key/fill lighting, low clutter. |
| HELLO | PASS | Physical motion and neutral return captured. |
| MILK | PASS | Physical motion and neutral return captured. |
| RICE | PASS | Physical motion and neutral return captured. |
| REPLAY | PASS | Three consecutive physical RICE replays completed independently after a neutral reset. |
| CLIP_ISOLATION | PASS | Both required sequences and a rapid-switch sequence used the requested named clip only; stale completions were suppressed. |
| RENDER_FRAME_PACING | PASS | 60 Hz device; measured 60.0–60.1 FPS with approximately 16.63 ms p50 and 16.79 ms p95. |
| LIFECYCLE_10_CYCLES | PASS | Ten real Listen open/leave cycles plus Home/resume and two-way rotation completed without native failure. |
| NO_SIGSEGV | YES | Final and focused lifecycle logcat scans clean. |
| NO_OOM | YES | No process death or `OutOfMemoryError`; decoded graphics memory released on leaving Listen. |
| NO_ANR | YES | Final and focused lifecycle logcat scans clean. |
| AVATAR_FAILURE_CONTAINED | YES | Renderer/controller exceptions map to `ERROR` / `Avatar unavailable`; transcript and STT remain outside the renderer failure surface. |
| PROTECTED_ML_HASHES_UNCHANGED | YES | All 28 protected model, label, manifest, and fixture hashes match Phase 0. |

## Scope and baseline protection

- Repository: `D:\BSIT 3RD YEAR\New VovGest\android_dry_run`
- Device: Samsung SM-A566B, Android 16, ADB serial `R5GYC0M1M4P`
- Physical display cadence exposed to the app: 60 Hz
- Phase 0 checkpoint: `reports/LISTEN_AVATAR_PHASE_0_BASELINE_20260910.patch` (192,815 bytes)
- Existing dirty-worktree changes were preserved; this run did not reset, discard, or overwrite unrelated work.
- No recognition code, FullSign225 behavior, thresholds, MediaPipe behavior, protected ML assets, GLB actions, animation timing, bones, or textures were changed.
- `DynamicWordAcceptanceGate.kt` still contains the exact protected expression `(System.nanoTime() / 1_000_000L)`.
- The supplied `12602.jpg` was used only for camera, framing, lighting, background, scale, and readability. It was not used to redefine avatar identity or FSL motion.

## Implemented Listen behavior

- Normal Listen now owns the real CORE3 viewport; the legacy canvas/cartoon path is not used by normal Listen operation.
- The responsive vertical flow is transcript → microphone/listening status → FSL Avatar, with fixed bottom navigation outside the scrollable content.
- The verified GLB loads once per active Listen renderer session. HELLO/MILK/RICE switch animator clips without reparsing the asset.
- Transcript routing is deliberately strict: case and terminal punctuation are normalized, but only the exact single concepts HELLO, MILK, and RICE resolve. Unsupported or multiword text remains visible and shows `Avatar sign not available yet`; it never guesses another sign.
- Supported transcripts auto-play unless the user has enabled Reduced Motion, in which case explicit Replay/Play Transcript remains available.
- Listen request and selected-tab state are saveable across Activity recreation, verified by retaining RICE through landscape and portrait recreation.
- State surface: `UNLOADED`, `LOADING`, `READY`, `PLAYING`, `ERROR`.
- On renderer failure, the Avatar area reports `Avatar unavailable`; the transcript, language selection, microphone, STT state, and bottom navigation remain functional.

## Renderer and presentation

- Renderer: Filament 1.71.4 through `ModelViewer`
- Android presentation surface: `TextureView` (the proven Samsung/Compose layering baseline); no regression to `SurfaceView`
- GLB animation names physically enumerated at runtime: `FSL_HELLO`, `FSL_MILK`, `FSL_RICE`
- Actual asset bounds: center `[0.0, 0.84976417, -0.009964466]`, half extent `[0.4917552, 0.84976417, 0.259053]`
- Root transform: unchanged
- Bounds-derived camera: waist Y `0.7307972`, target Y `1.2449045`, distance `2.0054433`, 42 mm focal length
- Background: `#D6E9F0` pale blue/neutral clear color
- Lighting: soft frontal key at 82,000 lux plus gentle frontal fill at 42,000 lux; hard dramatic shadows disabled
- Layout: portrait Avatar aspect ratio 0.78; compact/landscape aspect ratio 2.10. Both orientations keep the complete signing silhouette within the viewport.
- Render loop: one `Choreographer` callback chain; paused outside visible lifecycle; fixed-size primitive frame history; no per-frame metrics allocation, asset parsing, animator lookup, texture upload, or scene-light creation.
- Teardown: native `ModelViewer` access stops before `TextureView` detach; explicit unload is generation-guarded and idempotent.

## Physical animation and routing QA

The immutable source durations were preserved:

| Sign | Runtime clip | Duration | Physical result |
|---|---|---:|---|
| HELLO | `FSL_HELLO` | 1.6333333 s | PASS — active hand/fingers visible; returned neutral. |
| MILK | `FSL_MILK` | 2.0 s | PASS — signing hand and inactive arm readable; returned neutral. |
| RICE | `FSL_RICE` | 1.6333333 s | PASS — both-hand relationship and fingers visible; returned neutral. |

Completed physical sequences:

1. HELLO → MILK → RICE → HELLO
2. RICE → HELLO → MILK
3. Rapid RICE → HELLO → MILK request switching

Every accepted request logged `NEUTRAL_RESET` before `PLAY_REQUEST`. The two full sequences completed in order. In the rapid sequence, only the final MILK request completed; stale callbacks from superseded requests did not mutate UI state. No inherited pose, inactive-arm contamination, stale completion, or reload between clip switches was observed.

Replay torture used RICE three consecutive times. Completion serials 4, 6, and 8 each logged one neutral reset, one request, and one completion. Each completion retained 60.1 FPS, 16.63 ms p50, 16.79 ms p95, one cumulative janky frame, and one cumulative estimated dropped frame.

## Frame pacing

Primary renderer measurements are derived from `Choreographer` timestamps and actual rendered callbacks, not duplicated animation frames.

| Physical workload | Display | Average FPS | p50 | p95 | Janky | Estimated dropped | Notes |
|---|---:|---:|---:|---:|---:|---:|---|
| Normal Listen HELLO session | 60 Hz | 60.0 | 16.64 ms | 16.76 ms | 1 | 2 | 2,135 callbacks; 2,121 rendered; 14 skipped while surface/state unavailable. |
| Required action sequences | 60 Hz | 60.1 | 16.64 ms | 16.74–16.77 ms | 1 | 1 | Same loaded GLB across clip changes. |
| Three RICE replays | 60 Hz | 60.1 | 16.63 ms | 16.79 ms | 1 | 1 | Cumulative session counters; all three completions independent. |
| Rotation recreation RICE | 60 Hz | 60.1 | 16.63 ms | 16.78–16.79 ms | 0 | 0 | Separate landscape and restored-portrait renderer sessions. |

A separate Android `dumpsys gfxinfo` UI-thread sample recorded 2,825 frames, 22 janky frames (0.78%), p50 12 ms, p90 16 ms, p95 17 ms, and p99 19 ms. One surface/reset outlier occurred outside the steady render distribution; it did not cause an ANR or renderer failure.

Observed GLB readiness was approximately 288–588 ms depending on process/cache state and concurrent profiling. Rotation recreation measured 374 ms load / 272 ms first frame and 332 ms load / 244 ms first frame. Warm ten-cycle loads were typically about 288–307 ms. A deliberate concurrent `dumpsys meminfo` cold sample measured 588 ms / 571 ms and is treated as instrumented worst case, not steady state.

## Memory and GPU safety

All values are physical Samsung `dumpsys meminfo` values in KiB. Android's native/graphics accounting for this textured 28 MB GLB is variable, so lifecycle release and absence of monotonic growth/process death were the controlling gates.

| Point | PSS | RSS | Java heap | Native heap | Graphics | Swap PSS |
|---|---:|---:|---:|---:|---:|---:|
| A. Normal VoxGest, cold Sign | 156,775 | 248,500 | 17,332 | 26,160 | 47,952 | 633 |
| B. Avatar loading window | 162,425 | 256,772 | 13,756 | 28,472 | 50,908 | 142 |
| C. Avatar idle/ready | 1,032,473 | 1,129,636 | 13,496 | 470,712 | 433,188 | 145 |
| D. HELLO active | 1,005,369 | 1,103,152 | 15,096 | 468,620 | 433,572 | 145 |
| E. Rapid action switching | 1,022,391 | 1,034,664 | 12,560 | 464,488 | 429,988 | 71,251 |
| F. Ten seconds after leaving Listen | 228,715 | 326,628 | 14,512 | 82,524 | 50,780 | 145 |

After the ten-cycle lifecycle run, the corresponding post-leave sample was PSS 231,564 KiB, RSS 280,492 KiB, native 89,116 KiB, and graphics 51,804 KiB. Graphics returned from roughly 430 MiB loaded to roughly 50 MiB after leaving Listen. There was no OOM, process death, or escalating per-cycle residue that justified modifying the immutable asset.

## Lifecycle torture results

- Ten real Listen open → leave → reopen cycles: PASS. Each session created, parsed, became ready, stopped callbacks, unloaded, and detached without native double-destruction.
- Typical per-cycle unload snapshots: 69–76 callbacks, p50 16.64 ms, p95 16.70–16.76 ms, normally 60.1 FPS (one short sample at 58.5 FPS).
- Listen → Home → resume during MILK: PASS. Rendering paused in the background, resumed safely, and completed the requested clip.
- Listen → landscape → portrait while RICE was selected: PASS. Both recreation paths logged `UNLOADED`, a new renderer creation/readiness sequence, RICE replay, and stable pacing. `user_rotation` was restored to its original value `0`.
- Repeated Replay: PASS, three consecutive RICE completions.
- Repeated HELLO/MILK/RICE switching: PASS, including both specified sequences and rapid supersession.
- Focused and final full logcat scans: no VoxGest `FATAL EXCEPTION`, `AndroidRuntime` process crash, `SIGSEGV`, `OutOfMemoryError`, or `ANR` match.

## Build, tests, and immutable artifacts

- Command: `gradlew.bat testDebugUnitTest assembleDebug --console=plain`
- Result: `BUILD SUCCESSFUL`
- Unit tests: 74 executed, 0 failures, 0 errors, 0 skipped across 23 suites
- New coverage includes exact Listen transcript routing, unsupported/no-guess behavior, frame-pacing calculations, controller clip isolation, neutral reset, stale callback suppression, idempotent unload, and synthetic renderer failure containment.
- Final debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- APK size: 186,985,954 bytes
- APK SHA-256: `CDFA60BFE70B22DAD92787BD86D5FF343DDF8EF7BD2D2B9EF215DD79F0E4A327`
- Android SDK 37.0.0 `zipalign -c -P 16 4`: PASS
- CORE3 GLB: `app/src/main/assets/avatar/core3/voxgest_avatar_B32_CORE3_RC2.glb`
- GLB size: 28,123,308 bytes
- GLB SHA-256: `30F13FB65E7557992A8C3109460A790A69161E69F3BA9771A1E64A33F98294F8`

## Protected ML hash verification

All values below matched their Phase 0 values after the final build. No protected file was missing or modified.

| Protected asset | SHA-256 |
|---|---|
| `class_labels_lstm_v1.json` | `B90242F379D6107C442DAEBEDB95385C7F0A5656AFE5A809DE90FC9977D149D6` |
| `class_labels_tcn_v1.json` | `B90242F379D6107C442DAEBEDB95385C7F0A5656AFE5A809DE90FC9977D149D6` |
| `model/class_labels_tcn_fullsign225_manual5_team_v2.json` | `DB1CC2312ECDAE72FFD5C15FF3D11D192180A7D1EE182419FEF8E241DA67FCB9` |
| `model/class_labels_tcn_fullsign225_phrase_v1.json` | `61F2A5D1EE7CE3E4A7A857EA9143F2744FCC6D7F4696431E79FDB567B11B0E52` |
| `model/class_labels_tcn_onehand162_android_calibrated_v1.json` | `7A054A1C3936CE9EF92B4D8824B7ACADDFA01D9ABD3FBE7DE3DA64D3F4780EBD` |
| `model/class_labels_tcn_onehand162_phrase_v1.json` | `7A054A1C3936CE9EF92B4D8824B7ACADDFA01D9ABD3FBE7DE3DA64D3F4780EBD` |
| `model/hand_landmarker.task` | `FBC2A30080C3C557093B5DDFC334698132EB341044CCEE322CCF8BCF3607CDE1` |
| `model/pose_landmarker_lite.task` | `59929E1D1EE95287735DDD833B19CF4AC46D29BC7AFDDBBF6753C459690D574A` |
| `model/runtime_manifest_fullsign225_manual5_team_v2.json` | `EDE07FFD9407C58045E174E172C091B6A832799E85786360334E305E992DC119` |
| `model/runtime_manifest_fullsign225_phrase_v1.json` | `EC30B8D96929DD023246E81241284DBAB836568002AA9301E1B4B274D1743668` |
| `model/runtime_manifest_onehand162_android_calibrated_v1.json` | `2F742C3A1CD0584780B91787E69C19DF65B836A2A1F26657068F13EAFD7DEC27` |
| `model/runtime_manifest_onehand162_phrase_v1.json` | `0AD5819F5D9396736CE4150206DFF92B00B594E45B153CCF63F294ED9D0BB2CB` |
| `model/voxgest_tcn_fullsign225_manual5_team_v2.tflite` | `97EE230505E5D6CA82CAA4C5FFC604DC431C7E0B2BC95C54CEC4BF59545C7633` |
| `model/voxgest_tcn_fullsign225_phrase_v1.tflite` | `3E888953CB7C429C4393F870F4DA7E2E473245812497071C3BC30C09CA5F8196` |
| `model/voxgest_tcn_onehand162_android_calibrated_v1.tflite` | `874D93380301D3DBFBCDDCB7A8EB3E019D59E03D56884C1F8A7777C3EA3A4C7E` |
| `model/voxgest_tcn_onehand162_phrase_v1.tflite` | `7B64570DC02617947E53D2A8BD7B8C3474811FA7B8D99419C58CC0729B2FA636` |
| `model/fsl_fullsign225_20f_105_v1/class_labels_fsl105_fullsign225_v1.json` | `BFA76D96ED10BF97F43CA80BCFCC5BADD3E96DF7EBE4C0654FC078552DA55FB6` |
| `model/fsl_fullsign225_20f_105_v1/golden_expected.json` | `D44966E887FDA019A85E8C6D182F427291A36DDD3D52576C0AF361FF2934AA56` |
| `model/fsl_fullsign225_20f_105_v1/golden_fullsign225_feature_fixture.json` | `59B7C1BF8291BFF2515D75C4379F941CB4D55B4CAC0C46EB258E296EBA602652` |
| `model/fsl_fullsign225_20f_105_v1/runtime_manifest.json` | `11E646A55307648DF91BFD2818555A64182DA9DF5E07B5C1163DD2F0C0FE4F97` |
| `model/fsl_fullsign225_20f_105_v1/voxgest_fsl_fullsign225_105_float32.tflite` | `42D040EC2269D437546D327DECAACA32839ABDD6BB63B2D400063C90630E5D13` |
| `model/fsl_onehand162_20f_rdtcn_v2/class_labels_fsl_v2.json` | `F9FF2D9ACAE609130878EEBE58EDBBEBB8B905AD2EEC63FC0DF25B56B5911642` |
| `model/fsl_onehand162_20f_rdtcn_v2/golden_expected.json` | `929F00E25868710E7A66D58DCE5A24C62B1B70CF3D0306D17F5CDE76C3AFE72F` |
| `model/fsl_onehand162_20f_rdtcn_v2/golden_feature_fixture.json` | `D1875FDBC998642EF2646D9147B43A151C848A9EEC59AF1BA847AAE4B21DEB6B` |
| `model/fsl_onehand162_20f_rdtcn_v2/runtime_manifest.json` | `B3982E344C3BA3A792055929FE749AA457FAD733E9B3F69B3AA47D0B4B5F9030` |
| `model/fsl_onehand162_20f_rdtcn_v2/voxgest_fsl_rdtcn_v2_float16.tflite` | `8673EAC3F7BF4DA7B98570AFE09E9CB6AEAFD97F29927EEE89EDA9F809F3372D` |
| `models/asl_alphabet.tflite` | `B6AB5751BD7F692DC6870929E160F08DEC7DCBB343A529F7A2A6DC9426F3C8B8` |
| `models/asl_alphabet_labels.json` | `86A6658260D84CDA996DCE2ACDB9C44DEDB4462F7C00017D0A6156A3BFBB49FE` |

## Files changed by this Listen hardening run

Source changes:

- `app/src/main/java/com/voxgest/dryrun/ui/VoxGestPresentationApp.kt`
- `app/src/main/java/com/voxgest/app/avatar/Core3FilamentHostView.kt`
- `app/src/main/java/com/voxgest/app/avatar/Core3ListenTranscriptResolver.kt` (new)
- `app/src/main/java/com/voxgest/app/avatar/Core3FramePacingTracker.kt` (new)
- `app/src/test/java/com/voxgest/app/avatar/Core3ListenTranscriptResolverTest.kt` (new)
- `app/src/test/java/com/voxgest/app/avatar/Core3FramePacingTrackerTest.kt` (new)

QA artifacts:

- `reports/LISTEN_AVATAR_PHASE_0_BASELINE_20260910.patch`
- `reports/LISTEN_AVATAR_FINAL_DEVICE_QA_20260910.md`
- `reports/evidence/listen_avatar_20260910/` screenshots and UI hierarchies

The CORE3 catalog/controller/Filament integration and copied CORE3 assets already existed from the verified CORE3 Android handoff baseline; the source list above identifies this run's Listen-specific additions and edits. The repository contained many unrelated pre-existing modified and untracked files, all left intact.

## Screenshot and device evidence index

All evidence is under `reports/evidence/listen_avatar_20260910/`.

| Evidence | Purpose |
|---|---|
| `listen_core3_normal_final.png` / `.xml` | Final normal product Listen: transcript and microphone above embedded real CORE3; diagnostic selectors absent. |
| `listen_core3_framing_final.png` / `.xml` | Final portrait reference-style waist-up framing. |
| `listen_core3_hello_mid.png` | HELLO active motion with signing hand/fingers visible. |
| `listen_core3_hello_sequence.png` | HELLO sequence state. |
| `listen_core3_milk_sequence.png` | MILK physical motion state. |
| `listen_core3_rice_sequence.png` | RICE physical motion state. |
| `listen_core3_rapid_final.png` / `listen_core3_rapid.xml` | Rapid supersession ends in requested MILK neutral state without inherited pose. |
| `listen_core3_home_resume.png` | Home/background/resume recovery. |
| `listen_core3_rotation_landscape.png` / `.xml` | Responsive landscape CORE3 viewport with RICE state retained. |
| `listen_core3_rotation_restored.png` / `.xml` | Portrait restoration after rotation with RICE state retained. |
| `listen_core3_debug.xml`, `listen_core3_debug2.xml` | Debug-only physical selector hierarchy used to drive exact resolver QA. |

`listen_core3_initial.*` and `listen_core3_controls.*` document the superseded first framing pass and are retained for auditability; the files with `final`, per-sign, lifecycle, and rotation names are authoritative.

## Known limitations

- Only the three purchased/verified single-concept actions HELLO, MILK, and RICE are enabled. Multiword or unsupported transcripts intentionally do not animate.
- Reduced Motion intentionally disables automatic playback; the user can explicitly replay the verified sign.
- The immutable 28.1 MB compressed GLB expands to a large device-native/graphics footprint (roughly 430 MiB graphics in these samples). It releases when leaving Listen and remained stable across lifecycle torture, so no lossy asset mutation was made.
- This run validates Android rendering, routing, lifecycle safety, and the supplied verified clips. It is not a linguistic re-evaluation of those frozen FSL motions.
- The artifact is a debug APK. Production signing/Play release validation remains a separate release-engineering step.

## Stop boundary

The final Listen Avatar integration and performance-hardening scope is complete. No gesture-recognition work was begun in this run.
