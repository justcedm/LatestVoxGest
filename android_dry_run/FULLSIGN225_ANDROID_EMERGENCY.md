# VoxGest Android emergency integration

## Safety boundary

The production launcher still uses the existing runtime and does not activate
`STANDARD_FSL_FULLSIGN225`. No fake 105-class model exists. The old demo10,
`*_v1`, and 64-class OneHand162 assets are unchanged.

The two grading identities are separate:

- `STANDARD_FSL_FULLSIGN225`: float32 `[1,20,225]` to `[1,105]`, feature
  `fullsign225_20f_v1`, reserved asset directory
  `app/src/main/assets/model/fsl_fullsign225_20f_105_v1/`.
- `ACCESSIBLE_ONEHAND162`: existing float32 `[1,20,162]` to `[1,64]` debug
  hardware fallback in `FslExperimentalActivity`.

A blocked standard request never automatically loads the OneHand model. The
fallback must be selected and reported by its own profile ID.

## Front-camera and anatomical convention

The grading lens default is front. Three concepts are intentionally separate:

1. CameraX preview may mirror for selfie UX.
2. Standard model analysis input is never mirrored.
3. MediaPipe Hand Landmarker handedness, which assumes mirrored input, is
   inverted exactly once for unmirrored analysis. Reported `Left` maps to
   anatomical RIGHT; reported `Right` maps to anatomical LEFT. Unknown
   handedness is left unassigned and is never guessed from image X.

The canonical Android builder then writes direct fixed slots:

`pose99 | anatomical LEFT63 | anatomical RIGHT63`

It never swaps slots. It implements nose translation, independent hand
wrist-to-middle-MCP scaling, global Z damping by `0.3`, deterministic missing
data, finite checks, and exact 225-float shape.

## Artifact handoff

After the training gate is READY and a winner is exported, copy only these
files into the reserved asset directory:

- `voxgest_fsl_fullsign225_105_float32.tflite`
- `class_labels_fsl105_fullsign225_v1.json`
- `runtime_manifest.json`
- `golden_window_f32.bin`
- `golden_expected.json`

The runtime manifest must use the trainer's canonical nested `artifacts`
schema. The Android artifact gate validates exact shapes, dtypes, class count,
profile and feature IDs, layout, orientation, no-NOTHING/rejection policy,
desktop TensorFlow/TFLite parity status, filenames, label order/count, and the
model SHA-256 before it permits numeric Android parity.

The builder fixture is already generated from
`scripts_ml/fullsign225_feature_builder.py` with five cases (both, left-only,
right-only, no hands, missing pose). Its SHA-256 is
`7256e22ebd3dd278f4d6cc9e90c364ec9cd49d21d3a5c992c88844813fa1f8f4`.

## Build from C, not external SSD D

From PowerShell, copy only the Android project to a local C: work directory.
Do not use `/MIR` and do not copy datasets:

```powershell
New-Item -ItemType Directory -Force C:\VoxGest_Emergency_Android
Copy-Item -Recurse -Force "D:\BSIT 3RD YEAR\New VovGest\android_dry_run\*" C:\VoxGest_Emergency_Android\
Set-Location C:\VoxGest_Emergency_Android
.\gradlew.bat testDebugUnitTest assembleDebug
```

Install over the existing package without uninstalling or clearing data:

```powershell
adb -s R5GYC0M1M4P install -r .\app\build\outputs\apk\debug\app-debug.apk
```

## Samsung parity entry points

Existing 64-class fallback golden + live diagnostic activity:

```powershell
adb -s R5GYC0M1M4P shell am start --user 0 -n com.voxgest.dryrun/com.voxgest.dryrun.fsl.FslExperimentalActivity --ez voxgest.golden_only true
adb -s R5GYC0M1M4P logcat -d -s VoxGestFsl:I AndroidRuntime:E
```

The debug activity has a local system-font theme so it does not inherit the
downloadable Poppins provider that crashed on Android 16.

Standard FullSign225 parity activity:

```powershell
adb -s R5GYC0M1M4P shell am start --user 0 -n com.voxgest.dryrun/com.voxgest.dryrun.fullsign.StandardFslParityActivity
adb -s R5GYC0M1M4P logcat -d -s VoxGestFullSign225:I AndroidRuntime:E
```

Expected before training artifacts exist:

- `ANDROID_FEATURE_PARITY_FULLSIGN225 PASS` with numeric max absolute error.
- `ANDROID_TFLITE_PARITY_FULLSIGN225 BLOCKED` due missing real model/window.
- Overall `NOT_PASS`. Never report standard parity PASS from this state.

After all real artifacts exist, standard activation additionally requires both
Android markers to PASS. TFLite evidence includes top-1 agreement, maximum
probability difference, a maximum allowed threshold of `1e-5`, and latency.

## Release grading UI

Only Sign and Listen appear in bottom navigation. Production hides the demo
token panel, calibration/settings entry point, `162 Feature View`, prototype
footer, Phrases, History, and the extra Delete action. Sign retains front
camera, tracking status, recognized output, Speak, and Clear. Listen retains
microphone/STT and readable text.
