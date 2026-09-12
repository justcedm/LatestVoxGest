# VoxGest Live Handoff

This is the canonical operational checkpoint. Every meaningful future task
must update all fields below before handoff. Record failed checks explicitly;
do not replace missing evidence with assumptions.

TIMESTAMP=2026-09-12T13:12:12+08:00

BRANCH=recognition/recovery-20260912

COMMIT=735686fa6bc6015bf11c8963fca6bc4298de4071 (session baseline; the recovery/handoff commit follows this record)

CURRENT_GOAL=Establish justcedm/LatestVoxGest as the shared GitHub source of truth and publish the safe recovered state without training or large/local artifacts.

WORK_COMPLETED=

- Proved repository identity by matching recovered SHAs against all three
  candidate GitHub repositories. LatestVoxGest alone contains exact commits
  b6cf9eea25d100f52dfdb46b2713b695e0d39b3a and
  552689a5e329a50be9f52229127d721a55aa6e57.
- Created recovery branch recognition/recovery-20260912 without rewriting
  history.
- Audited and hardened .gitignore for datasets, archives, Android/Gradle
  outputs, Python environments/caches, local signing/secrets, device evidence,
  recovery snapshots, and intermediate training checkpoints.
- Scanned the safe tracked/untracked set for secret signatures; zero hits.
- Verified no staged or safe-untracked file exceeds GitHub's 100 MB per-file
  limit. The 159,736,124-byte debug APK is ignored and must not be pushed.
- Ran the complete Android debug unit-test task and assembled the debug APK.
- Added this handoff and the authoritative architecture-decision log.

FILES_CHANGED=

- Recovered Android recognition, camera, output/composer, localization,
  presentation, CORE3 Avatar runtime, resources, and unit-test sources under
  android_dry_run/app/src.
- Final deployable FullSign225 FSL-105 model bundle under
  android_dry_run/app/src/main/assets/model/fsl_fullsign225_20f_105_v1.
- Required CORE3 runtime asset and manifest under
  android_dry_run/app/src/main/assets/avatar/core3.
- Concise Android QA/freeze Markdown reports and UI/design references.
- .gitignore
- docs/ARCHITECTURE_DECISIONS.md
- reports/CODEX_LIVE_HANDOFF.md

COMMANDS/TESTS=

- git remote -v
- git branch --show-current
- git status --short
- git log -5 --oneline --decorate
- git ls-remote --heads --tags for justcedm/LatestVoxGest,
  justcedm/VOXGEST, and justcedm/ProjectVoxGest
- git switch -c recognition/recovery-20260912
- secret-signature filename/content scans: PASS, 0 hits
- gitignore policy checks for APK, Gradle build, local.properties, keystores,
  datasets, archives, evidence captures, backups, and checkpoints: PASS
- gradlew.bat testDebugUnitTest assembleDebug --no-daemon using process-local
  JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
- Android tests: 89 run, 0 failures, 0 errors, 0 skipped
- Android build: BUILD SUCCESSFUL
- Debug APK SHA-256:
  DFFC898FED15E2902924E16EA8615CCE641B54083D6D63C93A219EBD31E3651A
  (local ignored build output; never stage)

DATASET_STATUS=

- Existing FSL-105: metadata only in the stable C: repository; 1,704 train
  plus 426 test references (2,130 total), 105 labels; raw MOV clips absent.
- Transactional FSL v1: external-only workspace
  C:\VOXGEST_DATASETS\TRANSACTIONAL_FSL_V1; 1,107 MP4 clips, 26 classes,
  source/copy SHA-256
  51333B36E8CCA082BC5ECB1B53B0242E1C91D9D00393C2A75E18DCE27CEB48DE.
- The raw MediaPipe audit checkpoint reached 970/1,107 videos before the task
  switched. It is outside Git and must not be staged. No dataset/archive was
  copied into this repository.

TRAINING_STATUS=NOT_STARTED. New dataset training is prohibited until explicit approval after routing/parity/performance and physical-device qualification.

SAMSUNG_STATUS=NOT_CONNECTED at this checkpoint. ADB started successfully but listed no devices. Last known target serial is R5GYC0M1M4P; do not claim current physical validation.

METRICS=

- Standard runtime contract: float32 [1,20,225] to [1,105], RD-TCN,
  FullSign225 105 labels, canonical unmirrored input.
- Held-out test accuracy: 0.9364375461936437
- Held-out macro precision: 0.9410390743481
- Held-out macro recall: 0.9409048770197285
- Held-out macro F1: 0.9385262654349393
- TensorFlow/TFLite top-1 agreement: 1.0 over 210 samples
- Maximum probability difference: 1.430511474609375e-06
- Android unit tests/build: 89/89 pass; assembleDebug pass.
- Live Samsung FSL acceptance/negative/duplicate metrics: NOT QUALIFIED.

FAILURES=

- Initial Gradle invocation failed before build because JAVA_HOME/java was not
  available in the shell. Rerun with the existing Android Studio JBR passed.
- Samsung was not connected during this handoff, so no new physical-device
  recognition evidence was collected.
- Physical negative, seven-sign, duplicate-suppression, composer, and Listen
  smoke qualification remain incomplete.
- The FullSign225 artifact manifest retains lineage fields
  status=experimental_not_android_default and android_default_changed=false.
  Source routing names Standard as the normal profile and tests pass, but the
  installed APK must still prove the executed profile on Samsung.
- Transactional raw-video audit stopped at a 970/1,107 checkpoint when task
  focus changed; this is an audit interruption, not training.

CURRENT_HYPOTHESIS=The recovered source now routes normal Sign to the gated Standard FullSign225 profile and separates legacy Demo behavior. Remaining user-facing risk is physical runtime activation plus rejection/freshness/duplicate calibration and camera-domain performance, not the proven offline TFLite shape/parity contract.

NEXT_ACTION=After the safe branch is published, reconnect Samsung R5GYC0M1M4P, install the branch APK, prove the runtime profile/model/shape in logs, then execute no-hands, idle, open-palm, wave, HELLO, NO, MILK, RICE, KNOW, GOOD MORNING, MOTHER, and KNOW-neutral-KNOW trials one at a time. Capture raw top-1/confidence and exact rejection reason before any retraining decision.

DO_NOT_MODIFY=

- Do not access or write the retired D: workspace.
- Do not force-push or rewrite GitHub history.
- Do not stage raw datasets, archives, APK/AAB/build output, environments,
  caches, secrets, device evidence, recovery patches, or checkpoints.
- Do not train until explicitly approved.
- Do not redesign the frozen white/teal UI or CORE3 Avatar during recognition
  qualification.
- Do not replace the current FSL-105 model; preserve it as rollback.
- Do not mirror canonical ML input or swap anatomical hand slots.
- Preserve (System.nanoTime() / 1_000_000L).

## Future update contract

Every meaningful task must refresh: TIMESTAMP, BRANCH, COMMIT, CURRENT_GOAL,
WORK_COMPLETED, FILES_CHANGED, COMMANDS/TESTS, DATASET_STATUS,
TRAINING_STATUS, SAMSUNG_STATUS, METRICS, FAILURES, CURRENT_HYPOTHESIS,
NEXT_ACTION, and DO_NOT_MODIFY.
