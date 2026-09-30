# Avatar Trial owner-device engineering qualification — 2026-09-30

Scope: authorized Samsung SM-A566B (`R5GYC0M1M4P`), isolated `com.voxgest.dryrun.avatartrial` only. Installed modern VoxGest and Recognition Lab remained present. No APK was reinstalled, no GLB/renderer/recognition code was changed, and production LISTEN remains disabled. The unchanged private GLB is 52,887,544 bytes, SHA256 `1f566e98e020b8e46e8a625c3910794be86a2017392093950f467bad68c7689b`, with exactly 12 runtime actions. This is technical device qualification and owner visual feedback, **not FSL linguistic approval**.

Source context: `docs/avatar/AVATAR_SENTENCE_BILINGUAL_FINGERSPELLING_PLAN_20260930.md` was read from `origin/main` because it is not in this Avatar worktree; the previous isolated-device reports are in `reports/avatar_private_app_test_20260929/`; `reports/ASTRA_LIVE_HANDOFF.md` was read from `origin/avatar/astra-calibration-20260914`. All nine supervised actions remain unapproved test candidates, `listen_ready=false`.

## Repeatable device method and evidence

Each of the 12 labels was selected in the isolated dialog; Play and Replay were triggered separately while screen-recording. ADB logcat shows exact `PLAY_REQUEST`, `ACTION_STARTED`, `PLAY_COMPLETE`, and per-action frame metrics for both runs. Contact sheets sampled each motion and its full-body frame; candidate videos were also checked at the post-completion neutral frame. The screen and logs confirmed the correct asset hash/action enumeration. Private evidence, **not committed**: `D:\VoxGest\evidence\avatar_owner_qualification_20260930\` contains 12 `avatar_owner_<ACTION>.mp4` recordings, `avatar_owner_logcat.txt`, `avatar_owner_meminfo.txt`, `SHA256SUMS.txt`, ready screenshot, contact sheets, and dense THANK YOU onset frames. Log SHA256: `abc93cbf232e15d769ac51b6085907aaac11e716350b313517aa2a7aab4a23`.

Codes: `Y` = observed in log/video; `N` = not observed; `2D-clear` = no obvious defect in sampled front-view frames but 3D/depth not proven; `?depth` = 2D recording cannot resolve penetration. `OWNER_PENDING` means no owner visual decision for that action. Durations are the Android logged clip durations, not inferred from video length.

| Action | Found | Start | Complete | Replay | Neutral return | Full body | Active hand | Duration | Renderer error | Crash |
| --- | --- | --- | --- | --- | --- | --- | --- | ---: | --- | --- |
| FSL_HELLO | Y | Y | Y | Y | Y | Y | Y | 1.6333 s | N | N |
| FSL_MILK | Y | Y | Y | Y | Y | Y | Y | 2.0000 s | N | N |
| FSL_RICE | Y | Y | Y | Y | Y | Y | Y | 1.6333 s | N | N |
| THANK_YOU | Y | Y | Y | Y | Y | Y | Y | 4.0500 s | N | N |
| YES | Y | Y | Y | Y | Y | Y | Y | 4.0500 s | N | N |
| NO | Y | Y | Y | Y | Y | Y | Y | 4.0667 s | N | N |
| IM_FINE | Y | Y | Y | Y | Y | Y | Y | 4.0500 s | N | N |
| HOW_ARE_YOU | Y | Y | Y | Y | Y | Y | Y | 4.0667 s | N | N |
| UNDERSTAND | Y | Y | Y | Y | Y | Y | Y | 4.0833 s | N | N |
| GOOD_EVENING | Y | Y | Y | Y | Y | Y | Y | 4.0667 s | N | N |
| KNOW | Y | Y | Y | Y | Y | Y | Y | 4.0667 s | N | N |
| WRONG | Y | Y | Y | Y | Y | Y | Y | 4.0500 s | N | N |

| Action | Visible snap / onset | Hand-face penetration | Body collision | Mesh deformation | Owner visual review |
| --- | --- | --- | --- | --- | --- |
| FSL_HELLO | N obvious | 2D-clear | 2D-clear | N obvious | Prior control PASS; current technical PASS |
| FSL_MILK | N obvious | 2D-clear | 2D-clear | N obvious | Prior control PASS; current technical PASS |
| FSL_RICE | N obvious | 2D-clear | 2D-clear | N obvious | Prior control PASS; meaning unresolved (kanin/bigas) |
| THANK_YOU | **CONFIRMED neutral-to-clip pose discontinuity** | ?depth near mouth | 2D-clear | N obvious | Owner says otherwise looks good; engineering defect remains |
| YES | Neutral/clip start mismatch; dense review open | 2D-clear | 2D-clear | N obvious | Owner says looks good; source/FSL review pending |
| NO | N obvious in sampled frames | 2D-clear | 2D-clear | N obvious | Owner says looks good; source/FSL review pending |
| IM_FINE | Neutral/clip start mismatch; dense review open | 2D-clear | 2D-clear | N obvious | OWNER_PENDING |
| HOW_ARE_YOU | Neutral/clip start mismatch; dense review open | 2D-clear | 2D-clear | N obvious | OWNER_PENDING |
| UNDERSTAND | Neutral/clip start mismatch; dense review open | ?depth near face | 2D-clear | N obvious | **Owner flags neck-level target; expects forehead-level motion**; source/FSL review required |
| GOOD_EVENING | N obvious in sampled frames | ?depth near face | 2D-clear | N obvious | OWNER_PENDING |
| KNOW | Neutral/clip start mismatch; dense review open | ?depth near face | 2D-clear | N obvious | OWNER_PENDING |
| WRONG | Neutral/clip start mismatch; dense review open | 2D-clear | 2D-clear | N obvious | OWNER_PENDING |

### THANK YOU onset: frame-level finding

The fresh `avatar_owner_THANK_YOU_neutral_transition.png` samples the private video at 0.1-second intervals. At recording 0.8–0.9 s, both hands are in the shared neutral pose in front of the waist. At 1.0 s they shift downward; by 1.1 s the clip's arms-down start pose is reached. The previous day's `ty_start_dense.png` independently showed the same discontinuity around 0.9–1.0 s. The renderer resets via the `FSL_HELLO` clip at time 0, then applies the selected clip at time 0; the observed mismatch is therefore consistent with differing authored start poses, but the exact GLB curve/root cause should be checked by Earle before repair. Do not change this GLB in place. The owner found THANK YOU visually acceptable overall; that does not close the frame-level transition finding. Front-view video cannot certify absence of hand/face penetration.

### UNDERSTAND owner finding

After viewing the isolated app, the owner reported that UNDERSTAND should be performed at the forehead rather than its current neck-level target; the other three requested priority motions looked good to them. This is an owner visual objection, **not** sufficient by itself to rewrite FSL motion. Obtain an authoritative FSL source and qualified review of target, handshape, orientation and trajectory, then submit a separately versioned corrected candidate for Samsung retest. UNDERSTAND remains unapproved.

## Performance and preservation

Current startup log: model map 304 ms, first frame 1,394 ms, ready/load 1,396 ms. The final loaded `dumpsys meminfo` reported total PSS 732,018 KB (~715 MiB), Graphics 453,932 KB (~443 MiB), GL mtrack 382,988 KB, EGL mtrack 70,944 KB, Native Heap PSS 13,212 KB. This is a substantial graphics footprint; no OOM or renderer fatal error appeared during 12 actions. The app's cumulative frame metrics after the final replay reported ~59.8 rendered fps on a 60-Hz display, p95 frame interval ~16.72 ms, 55 janky frames / 27,083 observed (~0.20%) and action switch latencies roughly 0.75–8.75 ms across this battery. These are device log measurements for this run, not a cross-device performance claim.

Installed packages still include `com.voxgest.dryrun`, `com.voxgest.dryrun.recognitionlab`, and `com.voxgest.dryrun.avatartrial`. No installation, data clear, production route change, recognition change, or private binary commit occurred. The current Avatar Trial source is still a dirty local worktree containing pre-existing renderer/dialog/trial changes; this report and offline specifications are safe to checkpoint separately, but do **not** imply a clean standalone APK build from the remote branch.

Offline validation: the new JSON registries parse successfully; all 12 registry actions exactly resolve in the current 12-action manifest; all 26 letter IDs are unique and unavailable; no `listen_ready` entry is true; RICE has no Filipino alias. `:app:testAvatarTrialUnitTest --offline --no-daemon` completed `BUILD SUCCESSFUL`; JUnit XML reports 128 tests across 31 suites, 0 failures, 0 errors and 0 skips. The test task itself was up-to-date; two compile tasks executed. No app was installed for this validation.

Next exact action: Earle and an FSL-qualified reviewer inspect the current UNDERSTAND source/location and the neutral-to-candidate start-pose mismatch (especially THANK YOU); deliver separately versioned candidate corrections for owner Samsung visual retest. Keep all nine candidates unapproved and production LISTEN disabled. In parallel, review the bilingual registry/phrase-plan and source the missing FSL A–Z references before any sentence playback integration.
