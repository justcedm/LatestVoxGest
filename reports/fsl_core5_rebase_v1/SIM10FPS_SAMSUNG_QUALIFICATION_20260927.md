# Core5 SIM10 Samsung HELLO gate — 2026-09-27

STATUS=HELLO_ONLY_COMPLETE; OTHER_CLASSES_NOT_TESTED; NOT_PRODUCTION_QUALIFIED

## Frozen integration and parity

- Device: authorized Samsung SM-A566B (`R5GYC0M1M4P`), Android 16. Debug APK installed with `adb install -r`, without uninstalling packages or clearing app data. Built APK and installed `base.apk` SHA256 both `924e81190c44ac4e804bb2738e8c6e35b8cde664f435e0f5bf814230150dcec8`.
- Profile: explicit debug-only `FSL_CORE5_SIM10FPS_V1` (`core5_variant=sparse10fps_candidate`); baseline remains the default. Candidate model SHA256 `3702ff77c1c44a60f0dc7f06e19e778b6498df7dbf7e205991dc15158b8e888f`. Control model SHA256 `3cff57f526fd0aab4d531855f838ee8c100a97958be39c7cbbdcbd6d30797def` was verified in the ZIP but not installed or tested.
- ZIP integrity: all seven supplied file hashes and sizes match `sha256_manifest.json`; ZIP CRC check passed. `golden_hello_sim10fps48.bin` SHA256 `06c115da26ff7bea3b85b46050d4791e90957e40e1fe6bf111b3b405c5666343`.
- Contract: `core5_tasks_fullsign225_timestamp48_v1`; float32 `[1,48,225]` to `[1,5]`; labels in order `HELLO, THANK YOU, YES, NO, UNDERSTAND`; pose `[0,99)`, anatomical left `[99,162)`, anatomical right `[162,225)`. Analysis remains unmirrored. On-screen R=true was observed with the physical right hand in the wider front-camera preview; preview is FIT_CENTER and mirrored.
- Supplied golden: desktop SIM10 TFLite top-1 HELLO, max probability difference `1.6370905e-11` from Colab JSON. Samsung startup JSON reports feature parity PASS, temporal parity PASS, TFLite parity PASS, top-1 HELLO, and max difference `1.1920929e-7` (required `<=1e-5`). These checks passed before controlled signing.
- Build: `testDebugUnitTest assembleDebug` PASS; focused Python candidate/contract unit tests 13/13 PASS; `git diff --check` PASS. Existing Core5 V1 model remains SHA256 `3518ddeb68e69afa37428b8c5fc08b9d3b93e396fa1549224493da293f0ea484`. Production gates, UI, Avatar, demo10, and other models were not changed.

## Five timely-ended HELLO captures

Operator used MANUAL boundary, one right-hand HELLO per separately saved event, same camera/framing/lighting, and returned to neutral before END. These are operator-labelled diagnostic trials, not independently video-validated linguistic ground truth. Each event has a saved full probability vector, raw landmarks/timestamps, pre-resample features, final 48x225 tensor and SHA256, gate result, MediaPipe frame latencies, and exact desktop replay JSON. `pose/L/R` are raw-frame presence counts. Duration is first-to-last analyzed frame; rate is effective MediaPipe result rate, not camera sensor FPS.

| # | Event ID | End | Raw top-1 / confidence | Gate | Raw / envelope frames | Duration ms / rate Hz | Pose/L/R | Tensor SHA256 |
|---|---|---|---|---|---:|---:|---:|---|
| 1 | `d77e2c7d-6ef7-431d-b2a9-3ef4e111549e` | MANUAL_END | HELLO / 0.99997485 | ACCEPTED | 61 / 40 | 6217 / 9.65 | 61/0/39 | `dbd8f0184e5dabdb524c17a3ec63914dd11922e8ef69b6851a655506cd5dc485` |
| 2 | `b8416157-59fe-47cb-8025-7ac8abcb055d` | MANUAL_END | HELLO / 0.99994600 | ACCEPTED | 57 / 37 | 5839 / 9.59 | 57/0/36 | `146d3bf352119b7a4c7e1e7d2f9b8a25133661488dbfd890b5ddae5b77cceec9` |
| 3 | `95d3d82d-8822-42c0-983c-a4b54139eea9` | MANUAL_END | HELLO / 0.99966990 | ACCEPTED | 65 / 40 | 6679 / 9.58 | 65/0/39 | `e2cca0440ebdd72672116d64a60dfa6b5b12125757622150b72947194f2ca59d` |
| 4 | `7f354ff6-f5c1-482d-b6a8-41999c715e94` | MANUAL_END | HELLO / 0.99990570 | ACCEPTED | 49 / 31 | 5377 / 8.93 | 49/0/29 | `09bf399addc503cc82f9d9020bf6a2d38471d0297e3b615228712def2b21a0fe` |
| 5 | `e1bb9eed-0ff0-414c-b6c4-2fe037e50acf` | MANUAL_END | HELLO / 0.99947790 | ACCEPTED | 63 / 46 | 6889 / 9.00 | 63/0/40 | `8a7f0c48a7033fea72f40a71cdd2504ab1436a85eec5b236695c0218dbd12d3b` |

All five have Android-versus-desktop TFLite max probability difference `<=1.1920929e-7`, top-1 agreement, and raw-landmark-to-tensor rebuild max difference `4.7683716e-7`: replay PASS. Their end-command-to-result latencies are 38, 65, 45, 55, and 90 ms (median 55 ms); median per-frame MediaPipe latencies are 102.8, 103.2, 106.5, 111.0, and 112.1 ms. TFLite latencies are 6.87, 1.73, 1.58, 3.32, and 1.19 ms. Trajectory motion means are 0.985, 1.032, 1.746, 2.184, and 2.655. These are complete-event diagnostic latencies, not sign-end-to-accepted latency from an automatic boundary detector.

Five of five timely-ended, operator-labelled HELLO events were raw correct and accepted. Four additional HELLO-labelled captures were preserved but ended by `EVENT_TIMEOUT` at about 8 seconds; their raw top-1 was HELLO, yet the unchanged gate correctly rejected them. They are timing failures and are excluded from the five timely-ended result, not silently counted as accepted. No other class or negative/OOD trial was run; no general accuracy or false-accept rate is claimed. No production text/TTS is emitted by this isolated diagnostic activity.

Evidence: `D:/VoxGest/evidence/fsl_core5_rebase_v1/device_files/` contains startup JSON, every raw event JSON and five `.candidate-replay.json` files. Timestamped screenshot/log/collection manifests are in the parent folder. The supplied ZIP remains under the workspace `incoming/core5_20260927/`; no source recording or private event evidence is committed.

Rollback is one launch setting: `adb shell am force-stop com.voxgest.dryrun`, then `adb shell am start -n com.voxgest.dryrun/.Core5DiagnosticActivity --es core5_variant baseline`. The same APK contains the byte-unchanged baseline. Do not alter gates or train on self-labelled Samsung events. Next phase requires an explicitly authorized multi-class and negative battery; stop at HELLO for this request.
