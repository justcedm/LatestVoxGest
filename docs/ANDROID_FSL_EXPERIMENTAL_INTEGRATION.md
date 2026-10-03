# Android experimental FSL integration

Status: locally integrated, debug-only, and not deployment eligible. No Android device was visible to ADB at the evidence cutoff, so installation, Android TFLite parity, Android-generated feature parity, and live Samsung inference have not been run.

The final current tree passes `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:lintDebug`. All four unit tests pass. The debug APK is 127,963,228 bytes with SHA-256 `9534e13c623646f9fe01d7468da71fcdff71949681e5ecf1a622accc312991cb`. Android lint completed with 0 errors and 325 existing project-wide warnings.

## Isolation and active profiles

- Stable launcher: `com.voxgest.app.MainActivity` remains the only `MAIN`/`LAUNCHER` activity.
- Stable recognition default: `onehand162_android_calibrated_v1` remains selected by `USE_ANDROID_CALIBRATED_ONEHAND_MODEL = true` when its original assets exist.
- Experimental profile: `fsl_onehand162_20f_rdtcn_v2` is exposed only by the debug-source-set activity `com.voxgest.dryrun.fsl.FslExperimentalActivity`.
- The experimental activity must receive `--es voxgest.profile fsl_onehand162_20f_rdtcn_v2`. It is not registered in the stable `RecognitionProfile` loader and cannot become the default accidentally.
- The stable model, labels, manifests, feature builders, gates, cooldowns, launcher, and alphabet paths were not modified by this integration.

Experimental assets are isolated under:

`android_dry_run/app/src/main/assets/model/fsl_onehand162_20f_rdtcn_v2/`

| Asset | Contract / SHA-256 |
|---|---|
| `voxgest_fsl_rdtcn_v2_float16.tflite` | `[1,20,162]` float32 to `[1,64]` float32; `8673eac3f7bf4da7b98570afe09e9cb6aeafd97f29927eee89eda9f809f3372d` |
| `class_labels_fsl_v2.json` | exact 64 Android-safe tokens mapped to indices `0..63`; `f9ff2d9acae609130878eebe58edbbebb8b905ad2eec63fc0df25b56b5911642` |
| `runtime_manifest.json` | validation-only gates and exact class order; `b3982e344c3ba3a792055929fe749aa457fad733e9b3f69b3aa47d0b4b5f9030` |
| `golden_window_f32.bin` | held-out AUNTIE test window; `a3302f5cc8f18023caa02fcf4c23bb2d1a4acd77b1554e29244c8e4a1f2b6695` |
| `golden_feature_fixture_f32.bin` | three canonical builder cases; `3d54832f32bde281f3d965f35e9ae6bc434f87babffce9a172814fb01b32ac2e` |

## Canonical feature contract

Feature version: `onehand162_20f_nose_mcp_z03_v2`.

- `[0:99]`: MediaPipe pose landmarks `0..32`, each in XYZ order.
- `[99:162]`: fixed selected hand, MediaPipe hand landmarks `0..20`, each in XYZ order.
- Pose and hand coordinates are nose-relative using pose landmark `0`.
- Hand coordinates are divided by the Euclidean wrist `0` to middle-finger MCP `9` distance only when the distance is greater than `0.001`.
- Every Z coordinate is multiplied by `0.3`.
- Missing hand produces 63 zeros without moving slots.
- Missing pose produces an entirely zero 162-value frame.
- Each frame is float32; inference requires exactly 20 frames.

The Android builder consumes `LandmarkFrame.rightHandLandmarks`. In the debug activity the front-camera bitmap is mirrored before MediaPipe Tasks runs, and the implementation treats the resulting `right` slot as anatomical right. This equivalence is still a device-validation blocker: confirm it on Samsung using raw exported landmarks and visible right/left-hand trials before interpreting live accuracy.

## What local verification proves

- `FslCanonicalFeatureBuilderTest` passed its full, missing-hand, and missing-pose golden cases at an absolute tolerance of `1e-6`.
- The Python TFLite golden replay passed with maximum absolute probability error `0.0`. It predicted AUNTIE at index `0`, probability `0.9996241331`, with margin `0.9994725585`.
- These checks do **not** establish Android runtime parity. The debug activity must emit both `ANDROID_FEATURE_PARITY PASS` and `ANDROID_TFLITE_PARITY PASS` on the target device before it binds the camera. A failure blocks live mode.
- Android-generated feature parity requires exporting a real probe window and rebuilding its raw landmarks with `scripts_ml/88_compare_android_fsl_export.py`.

## Build and install without clearing data

From PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
Set-Location 'D:\BSIT 3RD YEAR\New VovGest\android_dry_run'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --stacktrace

$adb = 'C:\Users\loldk\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb devices -l
& $adb install -r '.\app\build\outputs\apk\debug\app-debug.apk'
```

`install -r` updates the APK while preserving application data. Do not run `pm clear`, uninstall the package, or use any equivalent data-clearing command.

At the last device check, `adb devices -l` returned only `List of devices attached`; therefore the install command has not been run. Resolve USB debugging/authorization until exactly one intended Samsung serial appears before continuing.

## Mandatory golden-only Android run

```powershell
$component = 'com.voxgest.dryrun/com.voxgest.dryrun.fsl.FslExperimentalActivity'
& $adb shell am start -W -n $component `
  --es voxgest.profile fsl_onehand162_20f_rdtcn_v2 `
  --ez voxgest.golden_only true

& $adb logcat -d -v threadtime -s VoxGestFsl:I VoxGestLandmarks:W '*:S'
```

Required evidence before live testing:

```text
ANDROID_FEATURE_PARITY PASS ... max_abs_error=... flags_match=true
ANDROID_TFLITE_PARITY PASS ... top1_match=true max_abs_error=... mean_abs_error=...
FSL_PROFILE_READY profile=fsl_onehand162_20f_rdtcn_v2 ... deployment_eligible=false
```

The Android probability thresholds are maximum absolute error `1e-4` and maximum mean absolute error `1e-5`, with an exact top-1 index match. If either parity marker is missing or reports `FAIL`, stop before live inference.

## Live diagnostic launch

```powershell
& $adb shell am start -W -n $component `
  --es voxgest.profile fsl_onehand162_20f_rdtcn_v2 `
  --ez voxgest.golden_only false

& $adb logcat -v threadtime -s VoxGestFsl:I VoxGestLandmarks:W '*:S'
```

`FSL_LIVE_DIAGNOSTIC` logs the profile, class, top 1, top 2, margin, current hand/pose presence, window hand/pose ratios, readiness `0..20`, inference latency, accepted/rejected state, reason, activity state, and selected-hand policy. This is diagnostic output only.

The confidence `0.5`, margin `0.1`, and cooldown `10` values came from validation-window calibration only. Cooldown was simulated from stored windows at extraction stride 5, not a continuous camera stream. The single-frame activity detector achieved validation accuracy `0.8646`, below the required `>0.95`, and is not used to claim reliable NSAC/background rejection in this Android path. The 64-class model has no NSAC output class.

## Samsung probe: five attempts per class

Use one signer ID and one unique session ID for a complete 64-class pass. Expected values may be the ASCII tokens in `class_labels_fsl_v2.json`; this avoids punctuation/space quoting issues.

```powershell
$package = 'com.voxgest.dryrun'
$component = "$package/com.voxgest.dryrun.fsl.FslExperimentalActivity"
$signer = 'SIGNER_01'
$session = 'SAMSUNG_SIGNER_01_20260828_RUN01'
$expected = 'AUNTIE' # replace with the next token in exact index order

& $adb shell am start -W -n $component `
  --es voxgest.profile fsl_onehand162_20f_rdtcn_v2 `
  --es voxgest.expected_label $expected `
  --es voxgest.signer_id $signer `
  --es voxgest.session_id $session `
  --ez voxgest.export_windows true
```

For that class:

1. Hold the neutral setup position; do not sign while the activity opens and mandatory golden parity runs.
2. Tap **Capture probe attempt 1/5**, perform one complete sign, and wait for the saved confirmation.
3. Return to neutral, then tap Capture again. Repeat until the UI says **Probe complete 5/5**.
4. Close the experimental activity, replace `$expected` with the next token, and rerun the launch command with the same signer/session values.
5. Complete all 64 tokens in exact index order. The recorder rejects a sixth attempt for the same expected class/session and rejects overlapping or out-of-order windows.

To print the exact token order from the integrated labels file:

```powershell
$labelsPath = 'D:\BSIT 3RD YEAR\New VovGest\android_dry_run\app\src\main\assets\model\fsl_onehand162_20f_rdtcn_v2\class_labels_fsl_v2.json'
$labelMap = Get-Content -LiteralPath $labelsPath -Raw | ConvertFrom-Json
$tokens = $labelMap.PSObject.Properties | Sort-Object { [int]$_.Value } | ForEach-Object Name
$tokens | ForEach-Object { "index=$($labelMap.$_) token=$_" }
```

Each summary row contains expected/predicted label, top-1/top-2 labels and scores, margin, inference latency, acceptance state, UTC timestamp, signer ID, session ID, pseudonymous device ID, model/profile ID, pose/right-hand frame counts, and optional detailed-window path. Detailed JSON includes raw pose/right-hand landmarks and canonical `(20,162)` float32 values plus feature-version and contract metadata.

## Export and Python feature comparison

Probe data stays in app-internal storage under `files/fsl_probe_f4719270/`. Copy it to the app's external files directory, then pull it; these commands do not clear app data:

```powershell
$exportTag = "fsl_probe_export_$session"
$remote = "/sdcard/Android/data/$package/files/$exportTag"
$local = "D:\BSIT 3RD YEAR\New VovGest\reports\device_exports\$exportTag"

& $adb shell run-as $package mkdir -p $remote
& $adb shell run-as $package cp -R files/fsl_probe_f4719270/. "$remote/"
New-Item -ItemType Directory -Force -Path $local | Out-Null
& $adb pull $remote $local
```

Then compare every detailed Android window to the Python canonical builder:

```powershell
Set-Location 'D:\BSIT 3RD YEAR\New VovGest'
Get-ChildItem -LiteralPath $local -Filter 'attempt_*.json' -Recurse | ForEach-Object {
  python scripts_ml/88_compare_android_fsl_export.py $_.FullName --output "$($_.FullName).parity.json"
}
```

Every window must print `ANDROID_FEATURE_PARITY PASS` with shape `[20,162]`, dtype float32, the exact feature version, strictly increasing timestamps, and maximum absolute error at or below `1e-6`. Preserve the raw export and generated parity JSON; do not import a failed or legacy-version window into training.

## Stop conditions and remaining blockers

Stop the Samsung probe and do not claim Android integration success if golden Android parity fails, an export fails Python reconstruction, the selected-hand slot is not anatomical right, shape/dtype/version differs, or model/label hashes differ.

Even after parity passes, deployment remains blocked by the failed activity-detector target, absence of a trained NSAC/background class in the 64-way classifier, validation-only thresholds/cooldown, unverified Samsung handedness equivalence, and missing multi-device continuous-live validation. This task does not add alphabet A-Z training/recording, any of the excluded 41 two-handed FSL classes, Transformer, USB/RTSP/smart-glasses, flashlight, or profanity-filter functionality.
