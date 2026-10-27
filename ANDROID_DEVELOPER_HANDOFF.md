# VoxGest Android Developer Handoff — Current Survey Track

**Updated:** 2026-10-28  
**Authoritative full context:** [Gemini Master Handoff](docs/GEMINI_MASTER_HANDOFF_20261028.md)

> The original 10-word / 30-frame / 162-feature handoff has been preserved at [docs/ANDROID_DEVELOPER_HANDOFF_LEGACY_20261028.md](docs/ANDROID_DEVELOPER_HANDOFF_LEGACY_20261028.md). Do not apply its legacy model contract to Core20 without checking the active runtime manifest.

## Current target

- Android main app package reported: `com.voxgest.dryrun`
- Separate Recognition Lab package: `com.voxgest.dryrun.recognitionlab`
- Physical target: Samsung Galaxy A56 5G, SM-A566B, Android 16, ADB serial reported `R5GYC0M1M4P`
- Current survey profile: Core20 (inherited name), 19 output labels
- Standard model input/output: `[1,20,225] -> [1,19]`
- Feature layout per frame: 99 pose + 63 anatomical-left hand + 63 anatomical-right hand
- Keep the historical Core5 five-class profile separate. Do not make experimental `sprint30_fullsign225` the default.

## Open P0: front-camera preview appears mirrored

The user has physically reported that the main survey SIGN screen still looks mirrored, even though an earlier code/build check claimed the preview was unmirrored. Treat this as unresolved.

Relevant files reported in the recent local work:
- `android_dry_run/app/src/main/java/com/voxgest/dryrun/survey/SurveySignController.kt`
- `android_dry_run/app/src/main/java/com/voxgest/dryrun/ui/SurveySignScreen.kt`
- `android_dry_run/app/src/main/java/com/voxgest/dryrun/PreviewOverlayMapper.kt` (verify it exists in the active worktree)

Inspect CameraX `Preview.Builder` / `MirrorMode`, PreviewView transforms/scale type, any Compose or AndroidView `scaleX`, and overlay mapping. Preview pixels, analysis frames, MediaPipe anatomical hand slots, and overlay coordinates are separate contracts. Do not blindly flip the analysis/model input to make the preview look right.

### Required verification
1. Confirm actual repository path, branch, dirty diff, installed package, APK hash, and active screen.
2. Reproduce with an asymmetric text/marker in the scene and move physical left/right hands independently.
3. Trace every transform and change only the layer proved wrong.
4. Run `testDebugUnitTest` and `assembleDebug`.
5. Install the exact APK with `adb install -r`, verify hash, launch the main survey SIGN screen, and physically check orientation and overlay alignment.
6. Do not report PASS until the user confirms the preview is unmirrored on the device.

## Core20 runtime contract

The approved manuscript defines the standard FSL-105 sequence as 20 frames × 225 features. Pose is nose-relative; each hand is independently scaled from wrist landmark 0 to middle-finger MCP landmark 9 when valid; z is multiplied by 0.3; extraction stride is 5. A valid 20-frame window requires pose and at least one hand in at least 13/20 frames. Preserve this contract across Python and Android. The separate Mapúa experiment uses 48×225 and must remain separate.

## 19 survey labels

HELLO, UNDERSTAND, NO, YES, THANK YOU, GOOD MORNING, GOOD EVENING, GOOD AFTERNOON, HOW ARE YOU, IM FINE, NICE TO MEET YOU, YOURE WELCOME, SEE YOU TOMORROW, DON'T UNDERSTAND, KNOW, DON'T KNOW, CORRECT, SLOW, FAST.

## Protected behavior and scope

- Do not change UI, model, thresholds, avatar, sentence builders, feature extraction, labels, or manuscript as part of the camera-only fix unless the root cause proves one is directly responsible.
- Do not overwrite Core5 or promote an experimental profile by accident.
- Do not use FSL TUTOR/YouTube data in the standard FSL-105 track.
- Keep performed/ground-truth label distinct from model prediction and operator acceptance.
- Rejected/low-confidence predictions must not be silently inserted into a sentence.
- Confirm the speech recognition service's offline behavior before promising the entire app is offline.
- A build or numerical parity check does not prove real-world recognition quality.

## Build commands

From the verified project root:
```powershell
cd .\android_dry_run
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Read [Gemini Master Handoff](docs/GEMINI_MASTER_HANDOFF_20261028.md) for the complete research context, dataset rules, survey protocol, limitations, current device state, and agent instructions.
