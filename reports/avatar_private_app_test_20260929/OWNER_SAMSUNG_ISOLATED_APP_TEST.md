# Avatar Trial — owner Samsung isolated test (2026-09-29)

Status: **stopped at the Core3 control gate**. The private 12-animation package passed integrity checks and the isolated `com.voxgest.dryrun.avatartrial` APK runs on Samsung SM-A566B. HELLO playback/replay complete without an observed app crash, but the viewport crops the lower body. The required full-body criterion therefore fails. MILK, RICE, and all nine supervised candidates were **not tested**. No linguistic approval or LISTEN promotion is implied.

## Package and isolation

- Read completely `ANDROID_INTEGRATION_README.md`, `CALIBRATION_STATUS.md`, `BUILD_TEST_REPORT.md`, `action_mapping.json`, `animation_manifest.json`, `runtime_audit.json`, and `SHA256SUMS.txt` from the owner-provided private package. All nine SHA256SUMS entries matched.
- GLB `voxgest_avatar_CORE3_PLUS_SUPERVISED9_TYV4_TEST.glb`: SHA256 `1f566e98e020b8e46e8a625c3910794be86a2017392093950f467bad68c7689b`, 52,887,544 bytes, valid GLB header/length, 12 animations. Exact mapping: HELLO/FSL_HELLO, MILK/FSL_MILK, RICE/FSL_RICE, THANK YOU/THANK_YOU, YES/YES, NO/NO, IM FINE/IM_FINE, HOW ARE YOU/HOW_ARE_YOU, UNDERSTAND/UNDERSTAND, GOOD EVENING/GOOD_EVENING, KNOW/KNOW, WRONG/WRONG.
- Nine non-Core3 actions remain `UNAPPROVED_TEST_CANDIDATE`; `listen_ready=false`. Private GLB/mapping/manifest remain local-only, not for Git. Canonical Core3 GLB was not overwritten.
- New local source is confined to the Avatar worktree's `avatarTrial` variant, manual activity/catalog/test, and variant declaration in `app/build.gradle`. Modern package, recognition lab, models, recognition evidence/gates, and production LISTEN route were not edited in this task. Pre-existing dirty Avatar source is still uncommitted and untouched.

## Build and device evidence

- `:app:assembleAvatarTrial` PASS; `:app:testAvatarTrialUnitTest` PASS (126 tests, 0 failures). Intermittent D: `device not ready` errors required single-worker/in-process Gradle retries; final build/tests succeeded.
- APK: `android_dry_run/app/build/outputs/apk/avatarTrial/app-avatarTrial.apk`; SHA256 `75895291decf9a045418a0a909ffc6b1e5c64e9cdac26eb0fba98a518c83ba20`; 212,458,852 bytes. Manifest has package `com.voxgest.dryrun.avatartrial` and sole launcher `AvatarTrialActivity`. APK enumerates the exact three private assets; embedded GLB hash/size and 12-action metadata match.
- ADB authorized Samsung serial `R5GYC0M1M4P`, model `SM-A566B`. Prior Avatar Trial APK hash `f87c8a0935bfdd9c0cc83e332e63d3cb60a5ed4cf82c7531b98b39fc6b289c88` and app data were backed up before install. `adb install -r` failed `INSTALL_FAILED_UPDATE_INCOMPATIBLE` due to different signer certificates. Only the old **Avatar Trial** package was uninstalled, then the new APK installed. Old app data was cleared by uninstall and remains in a private backup tar; it was not restored.
- Installed modern package SHA256 before/after: `6354700532f8e49eb7e16ec2e8efc7edd884dd24db1f90c1c1444c8cdd08e912`. Recognition lab before/after: `1a3d2770634c79ce4e6b3ce00a3dacab289773ee389599d573a05f7d4957cd07`. Both remain installed unchanged. Installed Avatar Trial hash matches the new APK.
- Runtime log: `CANDIDATE_READY` with correct SHA/bytes/12 names and `listen_ready=false`; Filament 1.71.4; `MODEL_PARSED`, `FIRST_FRAME` 1022 ms, `READY` 1024 ms. HELLO/FSL_HELLO started/completed in 1.6333333 s on play and replay, ~60 fps. No Avatar Trial fatal exception or Filament error seen in filtered device log.
- Private evidence, not for Git: `D:\VoxGest\evidence\avatar_private_trial_20260929\` contains old APK/data backup, `core3_hello_neutral_before.png`, `core3_hello_post_replay.png`, `avatar_trial_hello_control.mp4`, `avatartrial_device_log.txt`.

## Core3 stop gate

Neutral and post-replay screenshots show face/hair/clothing/hands without obvious mesh or material corruption, and a neutral-looking post-replay pose. But the skirt/lower legs/feet extend below the viewport. Log reports `PRESENTATION_CAMERA source=ASSET_BOUNDS waistY=0.7307972 targetY=1.2449045 distance=1.8194942`, consistent with existing `UPPER_BODY_CROP_FRACTION=0.43`. This is a framing/configuration failure, not evidence of a corrupt private mesh. The UI says “Neutral pose” after playback; the callback does not explicitly call `resetNeutral()` at completion, so an explicit runtime neutral reset remains unverified.

| Control | Evidence | Result |
| --- | --- | --- |
| HELLO | Loads, plays, completes, replays, visually neutral afterward; body cropped | **FAIL — full-body framing** |
| MILK | Not run after HELLO failure | NOT TESTED |
| RICE | Not run after HELLO failure | NOT TESTED |
| THANK YOU, YES, NO, UNDERSTAND | Not run | NOT TESTED / UNAPPROVED |
| IM FINE, HOW ARE YOU, GOOD EVENING, KNOW, WRONG | Not run | NOT TESTED / UNAPPROVED |

Do not infer linguistic correctness, absence of clipping/collision during untested actions, or production readiness from metadata or this HELLO control.

## Rollback and next action

Private rollback APK: `D:\VoxGest\evidence\avatar_private_trial_20260929\avatartrial_before_20260929.apk` (SHA above). Its signer differs from the current debug signer; rollback requires backing up current Avatar Trial data if needed, uninstalling **only** `com.voxgest.dryrun.avatartrial`, installing the backup APK, and optionally restoring the separately preserved pre-install app-data backup. Do not uninstall modern or recognition-lab packages.

Next: make a strictly Avatar Trial-only full-body presentation option without changing modern VoxGest behavior or compensating for an animation defect; verify explicit neutral return at playback completion. Rebuild/install only Avatar Trial, then rerun HELLO, MILK, RICE controls. Evaluate survey-priority candidates only if all Core3 controls pass. This checkpoint stops before that work under the requested stop rule.
