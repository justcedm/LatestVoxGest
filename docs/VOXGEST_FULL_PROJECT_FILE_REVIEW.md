# VoxGest Full Project File Review

Generated on 2026-06-12. Documentation-only review; no runtime, model, dataset, Android, or training behavior was changed.

## Scope

- Physical `.py` files found with ignored/generated paths included: 20,978.
- VoxGest-authored/project `.py` files outside dependency environments: 461.
- Generated dependency/environment `.py` files: 20,517.
- Non-ignored `.py` files visible through `rg --files`: 313.
- Existing unrelated working-tree changes were observed and left untouched.

## 1. Android App And Runtime Files

| File/Group | What it does | Status |
|---|---|---|
| `android_dry_run/settings.gradle`, `build.gradle`, `app/build.gradle` | Gradle project/app config; CameraX, MediaPipe, TFLite, Compose, Filament dependencies. | ACTIVE |
| `android_dry_run/app/src/main/AndroidManifest.xml` | Permissions and launcher/runtime declarations. | ACTIVE |
| `MainActivity.kt`, `SignFragment.kt`, `PhrasesFragment.kt`, `ListenFragment.kt`, `HistoryFragment.kt` | Main user screens for sign recognition, phrases, listen/STT, and history. | ACTIVE |
| `VoxGestCameraRecognitionController.kt` | CameraX analysis loop, MediaPipe extraction, rolling buffer, calibration export, inference, gate logging, callbacks. | ACTIVE |
| `MediaPipeLandmarkExtractor.kt`, `ImageProxyBitmapConverter.kt` | Converts frames and extracts MediaPipe hand/pose landmarks. | ACTIVE |
| `LandmarkExtractor.kt`, `LandmarkSequenceBuffer.kt` | Landmark contracts and current 20-frame buffer. | ACTIVE |
| `OneHand162FeatureBuilder.kt`, `AndroidLandmarkInputPolicy.kt` | Builds normalized onehand162 features and controls mirror/feature policy. | ACTIVE |
| `RecognitionProfile.kt`, `OneHandCalibrationConfig.kt` | Loads runtime manifests and selects original/calibrated onehand profile. Calibrated flag is currently enabled. | ACTIVE |
| `VoxGestTfliteRecognizer.kt`, `TfliteModelLoader.java` | Loads TFLite, labels, validates shape, blocks old 30-frame model under 20-frame profile. | ACTIVE |
| `DynamicWordAcceptanceGate.kt`, `RecognitionResult.java`, `RecognitionFeedback.kt` | Confidence/margin/hand-presence/consecutive-window acceptance and feedback payload. | ACTIVE |
| `OneHandCalibrationRecorder.kt` | Writes Android calibration JSON exports to Downloads/VoxGestCalibration. | ACTIVE |
| `FullSign225FeatureBuilder.kt`, `VoxGestWordRecognizer.kt`, `RecognitionGate.kt` | Fullsign225 branch retained but not the current onehand runtime path. | EXPERIMENTAL/LEGACY |
| `AlphabetClassifier.kt`, `AlphabetAcceptanceGate.kt`, `StaticLetterRecognizer.java` | Static alphabet branch retained; automatic alphabet output is disabled in current controller logs. | LEGACY |
| `ConversationHistoryManager.kt`, `SpeechController.java`, `Avatar*`, `SignVocabulary.kt` | Sentence, history, TTS/STT, avatar mapping/playback and vocabulary output stack. | ACTIVE |
| `android_dry_run/app/src/main/assets/model/*` | Bundled TFLite models, labels, runtime manifests, MediaPipe task assets. | ACTIVE/GENERATED |
| `android_dry_run/app/src/main/assets/avatar/*` | Avatar GLB, sign JSON, and animation assets. | ACTIVE/GENERATED |
| Android `*.bak_*` files | Historical backups from patch experiments. | BACKUP |

## 2. Python ML, Training, Import, And Test Scripts

Canonical source lives in `scripts_ml/`. Recorder packs, `external_datasets` snapshots, and the Claude evaluation package contain duplicate or handoff copies.

| Script/Group | Role | Status |
|---|---|---|
| `scripts_ml/70_import_android_onehand_exports.py` | Imports Android onehand calibration JSON into `(20,162)` `.npy` plus metadata. | ACTIVE |
| `scripts_ml/71_audit_android_onehand_exports.py` | Intended audit gate for imported Android samples. | EXPERIMENTAL/NEEDS_UPDATE; still expects `(30,162)` |
| `scripts_ml/72_train_android_calibrated_onehand162.py` | Intended trainer/exporter for calibrated Android onehand TCN. | EXPERIMENTAL/NEEDS_UPDATE; still expects `(30,162)` |
| `scripts_ml/check_20f_npy_shapes.py` | Quick 20-frame shape checker. | EXPERIMENTAL; path differs from importer output |
| `scripts_ml/73_setup_fsl_dataset_dirs.py` to `scripts_ml/78_train_fsl_rdtcn.py`, `scripts_ml/fsl_config.py` | Future FSL 20x162 dataset/import/audit/extraction/recording/training branch. | EXPERIMENTAL |
| `scripts_ml/60_*` to `scripts_ml/66_*`, `phrase_v1_dataset_tools.py` | Phrase-v1 merge/audit/training/live-plan tools. | EXPERIMENTAL |
| `scripts_ml/13_*` to `scripts_ml/53_*` older scripts | Desktop recording, WLASL import, LSTM/TCN training, static alphabet, fullsign225, motion-letter, and log analysis branches. | LEGACY/BACKUP |
| `phrase_builder.py`, `tests/test_phrase_builder.py` | Python phrase-builder utility and unit tests. | ACTIVE |
| `android_dry_run/*.py` | One-off Android patch helpers with hard-coded paths. | BACKUP |
| `voxgest_blender_avatar.py` | Blender avatar/animation generator. | EXPERIMENTAL |

The complete Python file-by-file inventory is in `docs/VOXGEST_ALL_PYTHON_FILES_REVIEW.md`.

## 3. Dataset, Import, And Audit Areas

| Path | Purpose | Status |
|---|---|---|
| `external_datasets/android_phone_exports/VoxGestCalibration/onehand162_phrase_v1/` | Phone JSON export landing area. | ACTIVE INPUT |
| `external_datasets/android_onehand162_phrase_v1_features/` | Imported Android onehand feature arrays and `.meta.json`. | ACTIVE/GENERATED |
| `reports/android_onehand162_import_report.json` | Import report from script `70`. | GENERATED |
| `reports/android_onehand162_audit_report.json` | Audit report; interpret carefully because root auditor still expects 30 frames. | GENERATED/RISK |
| `external_datasets/fsl_phone_exports/`, `external_datasets/fsl_features/`, `reports/fsl/` | Future FSL branch. | EXPERIMENTAL/GENERATED |
| `external_datasets/team_incoming_recorded_features/` | Teammate recorder-pack snapshots and extracted features. | GENERATED/BACKUP |
| `dataset_words_lstm/`, `dataset_words_lstm_fullsign225/` | Old 30-frame desktop word datasets. | LEGACY/GENERATED |
| `dataset_phrase_intents/`, `dataset_motion_letters/` | Phrase-intent and motion-letter research datasets. | EXPERIMENTAL/LEGACY |

## 4. Model Files And Manifests

| File/Group | Purpose | Status |
|---|---|---|
| Android asset `runtime_manifest_onehand162_android_calibrated_v1.json` | Current Android calibrated profile manifest; declares `[1,20,162]`. | ACTIVE |
| Android asset `voxgest_tcn_onehand162_android_calibrated_v1.tflite` | Bundled calibrated model artifact. Verify internal input shape before defense. | ACTIVE/GENERATED |
| Android asset `class_labels_tcn_onehand162_android_calibrated_v1.json` | Labels `WHAT`, `YOUR`, `NAME`, `MY`, `NOTHING`. | ACTIVE |
| Root `model/runtime_manifest_onehand162_android_calibrated_v1.json` | Root manifest still declares `[1,30,162]`; not synced with Android asset copy. | GENERATED/NEEDS_SYNC |
| Root `model/voxgest_tcn_onehand162_android_calibrated_v1.tflite` | Root calibrated TFLite artifact. | GENERATED/EXPERIMENTAL |
| `model/runtime_manifest_fsl_rdtcn_v1.json` | Future FSL manifest, pending training. | EXPERIMENTAL |
| `model/voxgest_tcn_fullsign225_*`, `model/runtime_manifest_fullsign225_*` | Fullsign225 model families retained for experiments/history. | LEGACY/EXPERIMENTAL |
| `model/voxgest_lstm_*`, `model/voxgest_tcn_v1.tflite`, `model/voxgest_v3.tflite` | Older LSTM/TCN/static alphabet artifacts. | LEGACY/GENERATED |
| `model/backups/*` | Historical backup snapshots. | BACKUP |

## 5. Reports And Evaluation Files

| File/Group | Purpose | Status |
|---|---|---|
| `reports/*.md` | Human-readable training/audit/evaluation summaries. | GENERATED/DOCUMENTATION |
| `reports/*.json`, `reports/*.csv` | Generated machine-readable audit, import, training, live-log outputs. | GENERATED |
| `reports/live_word_test_log_*` | Historical desktop/live recognition tests. | LEGACY/GENERATED |
| `reports/fsl/*` | Future FSL import/audit/diversity/extraction reports. | EXPERIMENTAL/GENERATED |

## 6. Documentation Files

| File/Group | Purpose | Status |
|---|---|---|
| `README.md`, `README_CURRENT.md`, `README_ANDROID_DRY_RUN.md` | Project and Android dry-run overview. | ACTIVE DOCS |
| `docs/ML_ENGINEER_KNOWLEDGE_BASE.md`, sprint/fullsign/phrase docs | Research, sprint, and training context. | DOCUMENTATION |
| `docs/VOXGEST_FULL_PROJECT_FILE_REVIEW.md` | This project map and important-file review. | ACTIVE DOCS |
| `docs/VOXGEST_ALL_PYTHON_FILES_REVIEW.md` | Complete Python inventory and review. | ACTIVE DOCS |
| `docs/VOXGEST_ACTIVE_PIPELINE_MAP.md` | Active dataset-to-Android pipeline map. | ACTIVE DOCS |
| `docs/VOXGEST_FILE_STATUS_CLASSIFICATION.md` | Status classification table. | ACTIVE DOCS |
| PDF/DOCX/PPTX thesis and presentation files | Defense/thesis artifacts. | DOCUMENTATION/GENERATED |

## 7. Experimental, Legacy, Backup Files

| File/Group | Purpose | Status |
|---|---|---|
| `tools/VoxGest_Recorder_*` | Portable recorder packs and duplicate script trees. | BACKUP |
| `CLAUDE_VOXGEST_EVALUATION_PACKAGE/` | Untracked evaluation/handoff package with Android snippets, sample exports, and asset copies. | BACKUP/UNTRACKED |
| `android_dry_run/*.py`, Android `*.bak_*` | One-off patch helpers and source backups. | BACKUP |
| `external_datasets/team_incoming_recorded_features/` | Teammate data snapshots and copied recorder files. | GENERATED/BACKUP |
| `voxgest_env/`, `**/recorder_env/` | Dependency environments; 20,517 generated `.py` files. | GENERATED |

## 8. Untracked Or Generated Files

Observed before documentation edits:

```text
 M android_dry_run/app/src/main/java/com/voxgest/dryrun/NamePhraseDetector.kt
?? CLAUDE_VOXGEST_EVALUATION_PACKAGE/
?? RUN_VOXGEST_20F_RESEARCH.ps1
?? android_dry_run/app/src/main/java/com/voxgest/dryrun/*.bak_*
?? android_dry_run/app/src/main/java/com/voxgest/dryrun/ui/*.bak_*
?? android_dry_run/*.py
?? model/class_labels_tcn_onehand162_android_calibrated_v1.json
?? model/runtime_manifest_onehand162_android_calibrated_v1.json
?? model/tcn_training_report_onehand162_android_calibrated_v1.json
?? model/voxgest_tcn_onehand162_android_calibrated_v1.tflite
?? reports/fsl/
?? scripts_ml/check_20f_npy_shapes.py
```

## Risks And Cleanup Suggestions

- Duplicate scripts exist across `scripts_ml/`, `tools/VoxGest_Recorder_*`, `external_datasets/team_incoming_recorded_features/`, and `CLAUDE_VOXGEST_EVALUATION_PACKAGE/`.
- Root `scripts_ml/71_audit_android_onehand_exports.py` and `scripts_ml/72_train_android_calibrated_onehand162.py` still assume `(30,162)` and need update for the 20-frame Android path.
- Root `model/runtime_manifest_onehand162_*.json` files still say 30 frames, while Android asset manifests say 20 frames.
- `VoxGestTfliteRecognizer.kt` has a useful old-model guard, but a guarded old model means runtime inference can be blocked until retraining/export is synced.
- `scripts_ml/check_20f_npy_shapes.py` checks `external_datasets/android_onehand162_20f_features`, while importer `70` writes `external_datasets/android_onehand162_phrase_v1_features`.
- `android_dry_run/*.py` patch helpers and Android `.bak_*` files are safe archive candidates later, but should not be run or deleted without approval.
- Dependency environments, datasets, model backups, and generated reports should remain ignored/uncommitted unless intentionally packaged.

## Defense-Ready Explanation

Python does the dataset and model work in VoxGest: it imports Android/exported recordings, audits shape and class readiness, preprocesses landmark arrays, trains temporal classifiers, exports TFLite, and writes labels/manifests. Android does the live accessibility work: it captures camera frames, extracts MediaPipe landmarks, builds 20-frame onehand162 sequences, runs the TFLite recognizer, gates predictions, and updates sentence output, TTS/STT, history, avatar, and visual feedback.

Recorded gesture data becomes a model by moving from Android JSON export to `.npy` features, then audit, training, TFLite export, labels, manifest, and Android asset sync. Some files are experimental or legacy because VoxGest explored older 30-frame LSTM/TCN, static alphabet, fullsign225, phrase-intent, motion-letter, RD-TCN, and future FSL paths before focusing on Android-calibrated onehand162 recognition.
