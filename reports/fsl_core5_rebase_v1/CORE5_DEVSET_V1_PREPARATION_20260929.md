# CORE5_DEVSET_V1 — independent development dataset preparation

Date: 2026-09-29. Base: `b07c20e3104a29be07c6680d3c7669a939898c1d`, branch `recognition/fsl-core5-rebase-v1`.
The requested direction document is not in this branch; it was read completely from `origin/main` without checking out or copying unrelated changes. This checkpoint is tooling only: **zero new captures, zero training, zero threshold fitting, zero Android source changes, zero installs**.

## Capture and replay audit

- Existing separate `recognitionLab` build type uses application ID `com.voxgest.dryrun.recognitionlab`. `Core5DiagnosticActivity` initializes MediaPipe Tasks (`MediaPipeLandmarkExtractor`), the front camera, and `Core5Recorder`. The extractor's anatomical slots and `StandardFullSign225FeatureBuilder` construct pose `[0,99)`, physical-left `[99,162)`, and physical-right `[162,225)`. The debug-only `Core5Contract` provides timestamp resample48 and the unchanged diagnostic gate; `Core5Runtime` verifies asset hashes, `[1,48,225]` input, `[1,5]` output, label order, and TFLite parity.
- `Core5Recorder` already saves raw landmark arrays, per-frame timestamps/presence/rotation and canonical vectors, envelope inclusion, final tensor/hash, diagnostic probabilities, quality metrics, and termination/gate reason under app-private `files/core5_diagnostics/<UUID>.json`. The final tensor is reproduced from the raw included observations during import. No Android implementation change was needed.
- `scripts_ml/core5_device.py` addresses the old `com.voxgest.dryrun` package and historical evidence root; it is **not** an authorized CORE5_DEVSET_V1 collector. `scripts_ml/core5_candidate_replay.py` writes beside existing evidence; it is reused conceptually, not invoked on historical events here. The new host companion addresses **only** the lab package and writes only to the new private root.

## Schema and isolation

Versioned contract: `CORE5_DEVSET_V1_TRIAL_SCHEMA.json`; code: `core5_devset_v1.py` and `core5_devset_capture.py`. A lane is a named signer/device pair, initially `signerA__samsungA56`. The physical serial is hashed and bound on first future capture, not stored in Git. This alias must be confirmed with the actual signer before use; if the signer/device differs, add a separate lane instead of relabeling captures.

Each lane has 90 preplanned immutable slots: HELLO 10, THANK YOU 10, YES 15, NO 15, UNDERSTAND 10, RANDOM_NON_FSL 10, PARTIAL_ABORTED 10, NEUTRAL 10. The deterministic name is `core5dev_v1__<signer>__<device>__<LABEL>__trialNNN`, e.g. `core5dev_v1__signerA__samsungA56__YES__trial001`. The fixed within-lane allocation is 54 `DEV_TUNE`, 18 `DEV_HOLDOUT`, 18 `SEALED_FINAL`; whole-lane role policies also exist for a future held-out signer/device. Roles are assigned before capture. Duplicate event IDs, raw hashes, and final tensor hashes across all roles/lanes are rejected. A consumed slot is never overwritten. Captures remain `supervised_training_allowed=false` and `linguistic_ground_truth_validated=false` pending independent review.

The 69-event authoritative historical matrix SHA-256 is `9ea9cfcf577cc031d98042afe8ab305aaa59b32ffe6f23940bce3176cbeabc85`. The private `SEALED_HISTORICAL_DIAGNOSTIC` registry contains all 69 matrix IDs plus hashes of **all 74** raw event files currently in the old device folder. Five extra historical raw files are excluded too. Every import/validation rechecks the matrix and all source raw file hashes; import refuses historical source paths, IDs, or hashes. Historical events may only be used for later diagnostic evaluation, never training or threshold fitting.

New private root: `D:\VoxGest\evidence\core5_devset_v1`. Current validation: one lane, 90 **planned**, **zero captured**, 69 matrix events sealed, 74 historical raw files excluded. Per-trial output template:

The private `sealed_historical.json` SHA-256 is `35762d7de0bff9234acd02d1a57af6e1a5cd4a4f6763742b2bf1c3159beff766`. The raw registry and its private paths remain out of Git.

`D:\VoxGest\evidence\core5_devset_v1\trials\<signer>__<device>\core5dev_v1__<signer>__<device>__<LABEL>__trialNNN\`

The future per-trial directory contains `raw_capture.json`, `raw_landmarks.npz`, optional `tensor.bin`, `annotations.json`, `model_outputs.json`, `metadata.json`, and hashes. `pending/<trial-id>/` retains the pulled source/clock attestation even when validation fails; no silent replacement event is allowed. The raw device event itself remains app-private. `manifest_snapshots/` stores earlier manifests by SHA-256.

Human observer markers are `SIGN_START`, `SIGN_END`, `RETURN_NEUTRAL`, `SAFE_REARM`; optional paired `HOLD_START`/`HOLD_END`. For NEUTRAL, the first two bracket stillness and have no linguistic meaning. Markers are metadata only; they never alter the model tensor, model output, automatic collector, or gate. Host monotonic time is mapped to device elapsed time using `/proc/uptime`; an estimated uncertainty above 100 ms makes import fail while pending evidence remains. **Clock mapping and marker usability still need a non-sign physical dry run before relying on annotation timing.**

Every saved tensor is replayed on the desktop through SHA-locked Baseline `3518ddeb…`, Native48 `3cff57f5…`, and SIM10 `3702ff77…`, with all five probabilities stored separately. No winner is selected at collection time. If there is no final tensor, outputs are null. Offline cadence export can select actual observations at 60/30/20/15/12/10/8 fps-equivalent where the measured result rate permits; it does not invent missing high-rate motion and does not train a model.

## Future collection invocation — **do not run until owner is ready**

From the recognition repository, with a second observer at the keyboard and the signer framed in the recognition-lab camera, use the isolated package only. The script checks one authorized device, candidate startup profile/model parity, one new event, event/tensor reconstruction, marker order, historical seal, and frozen model hashes before import. It does not install an APK or touch the modern app.

```powershell
& 'C:\Users\loldk\miniconda3\envs\voxgest\python.exe' -B scripts_ml\core5_devset_capture.py `
  --root 'D:\VoxGest\evidence\core5_devset_v1' `
  --trial-id 'core5dev_v1__signerA__samsungA56__HELLO__trial001' `
  --adb 'C:\Users\loldk\AppData\Local\Android\Sdk\platform-tools\adb.exe' `
  --baseline 'android_dry_run\app\src\debug\assets\model\fsl_core5_rebase_v1\core5_float32.tflite' `
  --native-zip 'D:\BSIT 3RD YEAR\New VovGest\incoming\core5_20260927\VOXGEST_CORE5_ANDROID_CANDIDATES_20260927.zip' `
  --sim 'D:\BSIT 3RD YEAR\New VovGest\incoming\core5_20260927\VOXGEST_CORE5_ANDROID_CANDIDATES_20260927.zip'
```

First append `--preflight-only` to the example command. This starts **no capture event**; the observer taps marker keys to check clock uncertainty. Remove that flag only after signer/lane identity, framing, and clock checks pass. The observer then presses SPACE once for each requested marker, including safe re-arm, while the signer performs exactly one sign or one negative action. The existing manual diagnostic event still has an 8-second timeout; a timeout is retained and marked invalid, not silently replaced. Option `--hold` adds hold markers. After the future trial, run `python -B scripts_ml/core5_devset_v1.py validate --root 'D:\VoxGest\evidence\core5_devset_v1'`. Later offline `cadence-export` writes to a separate new directory only.

## Verification and limits

- Python offline safeguard tests: 6/6 pass in both base Python and the VoxGest environment (`python -B tests/test_core5_devset_v1.py`). Broader Core5 suite: **37/37 pass** with the project VoxGest Python environment. The base Miniconda interpreter has NumPy 2 and produces three unrelated pre-existing `np.cross` 2-D semantic-geometry test errors; no geometry code was changed here. Synthetic import checks deterministic 90-slot roles, event/tensor hash validation, historical path/ID rejection, duplicate rejection, immutable lane binding, marker timing, cadence availability, and lab-only command construction. Private root seal validation passes with 90 planned/0 captured.
- Three frozen TFLite models: actual SHA-locked desktop interpreter smoke on a synthetic `[48,225]` zero tensor passed; this is plumbing validation, **not recognition evidence**. No official sealed test example or historical Samsung tensor was opened for this smoke.
- Android `:app:testRecognitionLabUnitTest :app:assembleRecognitionLab --offline`: **BUILD SUCCESSFUL** (44 tasks, cached/up-to-date). No installation or Samsung interaction.
- Modern UI, Avatar, deployed models, production gate, and default route were not edited. Pre-existing dirty Android/debug files and unrelated untracked artifacts were preserved and excluded from this checkpoint commit.

Next exact action: when the owner is ready, confirm signer/device lane aliases, verify that the separate recognition-lab APK is present and camera startup passes, perform one **non-sign** marker/clock dry run, then start the 90-slot controlled collection. Do not train or tune on the historical 69.
