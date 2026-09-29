# Avatar Trial — full-body framing and Samsung retest (2026-09-29)

Scope: isolated `com.voxgest.dryrun.avatartrial` on authorized Samsung SM-A566B, serial `R5GYC0M1M4P`. This is a renderer/device qualification, **not** FSL linguistic approval. Private GLB and animation data were not changed. `listen_ready=false` and all nine supervised actions remain `UNAPPROVED_TEST_CANDIDATE`.

## Preserved start and cause

- Started from Avatar branch `avatar/earle-supervised9-integration-v1` at `fee7a693ac51e4d7c5150a1237429112589bab69`. Existing dirty/untracked Avatar work was preserved without reset. ADB was authorized.
- Previous installed Avatar Trial APK SHA256 `75895291decf9a045418a0a909ffc6b1e5c64e9cdac26eb0fba98a518c83ba20` was pulled to private rollback path `D:\VoxGest\evidence\avatar_private_trial_20260929\avatartrial_before_fullbody_fit.apk` before `adb install -r`. The older pre-trial rollback APK remains privately preserved too.
- Previous camera was intentionally upper-body framing: `UPPER_BODY_CROP_FRACTION=0.43`, asset-bounds `targetY=1.2449045`, `distance=1.8194942` m. Asset AABB center `[0, 0.84976417, -0.009964466]`, half-extents `[0.4917552, 0.84976417, 0.259053]`. Samsung viewport is 912×981 (aspect 0.92966), vertical FOV 31.89079°, near/far 0.05/20 m. Root/world transform unchanged. The old target was ~0.395 m above body center and the distance could fit only the upper body. Cause: **camera too close and target too high**; no evidence of aspect-ratio error, UI overlay crop, or corrupt GLB.

## Trial-only fit

The renderer now selects full-body fitting only when its runtime package opts in; the default Core3 package retains the original camera. The isolated candidate package opts in. The fit uses the loaded GLB AABB, measured Filament viewport/aspect and vertical FOV. It targets the AABB center and sets distance to `depth_half × 1.08 + max(height_half × 1.08 / tan(FOVv/2), reach_half_width × 1.08 / (tan(FOVv/2) × aspect))`, where `reach_half_width=max(rest_half_width, body_half_height)` protects an extended-arm envelope. It re-fits on viewport-size changes, not animation frames. Neither skeleton/mesh scale nor any GLB, curve, clip duration, or camera motion tied to animation was changed.

Device log: `TRIAL_FULL_BODY_CAMERA viewport=912x981 fovVertical=31.890792846679688 aspect=0.9296636085626911 center=[0.0,0.84976417,-0.009964466] distance=3.7349070390008925 reachHalfWidth=0.8497641682624817 margin=1.08`. Camera remained fixed throughout recordings. Neutral screenshot confirms head, feet, left/right sides and hands visible, natural center, no ground clip and reasonable (~10%) margin. Android first frame 953 ms, ready/load 955 ms; asset map 34 ms.

Build `:app:assembleAvatarTrial` PASS; final `:app:testAvatarTrialUnitTest` PASS: 128 tests, 0 failures/errors/skips. New tests cover perspective fit across aspect ratios and the trial opt-in / production-default camera guard. Installed APK and device APK SHA256 `9b0630731d6e834e62757d50d6fecdb63766441a0f04cf7917c701e06ab61245`. GLB SHA256 remains `1f566e98e020b8e46e8a625c3910794be86a2017392093950f467bad68c7689b` (52,887,544 bytes).

## Core3 control gate

Samsung screen recordings were captured for play and replay; sampled frames show full body and active hands throughout, stable camera, no obvious mesh/material regression, and visual neutral return. Logs contain start and completion for each play/replay; no Avatar Trial crash.

| Control | Runtime clip | Duration | Device result |
| --- | --- | --- | --- |
| HELLO | FSL_HELLO | 1.6333 s | PASS: full body, active hand, play, replay, neutral |
| MILK | FSL_MILK | 2.0000 s | PASS: full body, active hand, play, replay, neutral |
| RICE | FSL_RICE | 1.6333 s | PASS: full body, both active hands, play, replay, neutral |

The Core3 pass is technical device/rendering qualification only; it does not revise any linguistic approval claim.

## Survey-priority candidates (unapproved)

All four were enumerated by exact runtime name, played, replayed, completed and visibly returned to neutral without a crash. Full body and active hands stayed in frame. Owner visual review and expert FSL review are **PENDING**. A 2D front-view recording cannot conclusively rule out 3D hand/face penetration or fine mesh collisions.

| Action | Duration | Full body / hands | Obvious snap | Hand-face / body collision | Mesh deformation | Play / replay / neutral | Crash |
| --- | ---: | --- | --- | --- | --- | --- | --- |
| THANK YOU | 4.0500 s | YES / YES | **ONSET SNAP SUSPECTED**: hands-forward neutral shifts to arms-down at recording ~0.9–1.0 s (clip onset); see dense frames | Hand approaches mouth at ~3.5 s; penetration **not determinable from 2D**; no obvious body collision | None obvious in sampled frames | YES / YES / YES | NO |
| YES | 4.0500 s | YES / YES | None obvious in sampled/dense onset frames | None obvious; depth unverified | None obvious | YES / YES / YES | NO |
| NO | 4.0667 s | YES / YES | None obvious in sampled/dense onset frames | None obvious; depth unverified | None obvious | YES / YES / YES | NO |
| UNDERSTAND | 4.0833 s | YES / YES | None obvious in sampled/dense onset frames | Hand approaches face; penetration **not determinable from 2D**; no obvious body collision | None obvious | YES / YES / YES | NO |

No animation was modified to address THANK YOU. Earle/Astra should compare the initial THANK_YOU clip pose against the shared neutral reference at clip frame 0 and inspect the private recording around 0.9–1.0 s. The optional smoke recordings below were captured before this denser onset review revealed the suspected snap; they do **not** imply the four candidates were approved or calibration-clean.

## Optional smoke pass and performance

IM FINE, HOW ARE YOU, GOOD EVENING, KNOW and WRONG were each selected in that order and played once. Logs show `PLAY_COMPLETE` for all five (~4.05–4.07 s each), no crash; sampled frames show full body in view and neutral-looking end pose. Replay, fine collisions, snap-free motion and linguistic correctness were **not** qualified for these optional actions.

No obvious device jank was observed. Filament frame-pacing logs during Core3 play/replay report approximately 60.0 rendered fps, animation update rates ~59.5–60.1 fps, p95 frame intervals ~16.7 ms; these are measured device log values, not a guarantee across other devices. During a loaded Avatar Trial, `dumpsys meminfo` reported total PSS ~754 MB and Graphics ~454 MB; this is substantial and warrants later memory profiling, although no OOM/crash or Filament fatal error appeared. Logcat did contain generic Android back-dispatcher/vendor-property warnings, not renderer failures.

Modern installed package SHA256 before/after `6354700532f8e49eb7e16ec2e8efc7edd884dd24db1f90c1c1444c8cdd08e912`; Recognition Lab `1a3d2770634c79ce4e6b3ce00a3dacab289773ee389599d573a05f7d4957cd07`. Both remain installed and unchanged. Production LISTEN, recognition, UI, model assets and Avatar GLB were not changed.

## Evidence and checkpoint limits

Private local evidence (not for Git): `D:\VoxGest\evidence\avatar_private_trial_20260929\fullbody_fit_neutral.png`, `fullbody_fit_{hello,milk,rice}_play_replay.mp4` and contact sheets, `candidate_{thank_you,yes,no,understand}_play_replay.mp4` and contact sheets, `priority_start_dense.png`, `ty_start_dense.png`, five `smoke_*.mp4`, and `fullbody_fit_device_log.txt`. The report intentionally does not embed private video or GLB data.

The isolated trial code is tested and installed locally, but source is **not yet a clean standalone remote build checkpoint**: the Avatar worktree already had uncommitted shared renderer/dialog code on arrival, and the trial build depends on it. Only this safe report should be pushed until those pre-existing shared Avatar changes are reviewed and reconciled without changing modern VoxGest behavior. Never commit the private GLB or private recordings.

Next exact action: owner/Earle review the four priority recordings, especially THANK YOU onset at ~0.9–1.0 s and face-adjacent frames; keep all nine unapproved and LISTEN disabled. Separately profile loaded Avatar Trial graphics memory and reconcile the local isolated source into a reproducible Avatar-only branch without modifying the modern package.
