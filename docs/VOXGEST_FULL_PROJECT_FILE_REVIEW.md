# VoxGest Full Project File Review

Generated on 2026-06-03. Documentation-only review; no runtime, model, dataset, or training behavior was changed.

## Scope Notes

- Total `.py` files physically present: 20971.
- VoxGest-authored/project `.py` files reviewed individually: 454.
- Generated dependency files are represented by directory buckets in this project map and listed individually in the Python/status appendices: 20517 total (`voxgest_env` has 9732; recorder/site-packages environments have 10785).

## 1. Android App And Runtime Files

| File/Group | What It Does | Status |
|---|---|---|
| `android_dry_run/settings.gradle` | Gradle project include for app module. | ACTIVE |
| `android_dry_run/build.gradle` | Top-level Android Gradle plugin versions. | ACTIVE |
| `android_dry_run/app/build.gradle` | Android app config and dependencies: CameraX, MediaPipe, TFLite, Compose, Filament. | ACTIVE |
| `android_dry_run/app/src/main/AndroidManifest.xml` | Permissions and launcher activity. | ACTIVE |
| `android_dry_run/app/src/main/java/com/voxgest/app/MainActivity.kt` | Main app shell for Sign, Phrases, Listen, History. | ACTIVE |
| `android_dry_run/app/src/main/java/com/voxgest/dryrun/VoxGestCameraRecognitionController.kt` | Camera analysis, MediaPipe extraction, rolling buffer, inference, calibration export, callbacks. | ACTIVE |
| `android_dry_run/app/src/main/java/com/voxgest/dryrun/MediaPipeLandmarkExtractor.kt` | MediaPipe task wrapper producing LandmarkFrame. | ACTIVE |
| `android_dry_run/app/src/main/java/com/voxgest/dryrun/LandmarkExtractor.kt` | LandmarkPoint, HandObservation, LandmarkFrame contracts. | ACTIVE |
| `android_dry_run/app/src/main/java/com/voxgest/dryrun/OneHand162FeatureBuilder.kt` | Normalized onehand162 feature builder and dormant delta method. | ACTIVE |
| `android_dry_run/app/src/main/java/com/voxgest/dryrun/LandmarkSequenceBuffer.kt` | 20-frame rolling sequence buffer. | ACTIVE |
| `android_dry_run/app/src/main/java/com/voxgest/dryrun/RecognitionProfile.kt` | Runtime manifest/profile loader. | ACTIVE |
| `android_dry_run/app/src/main/java/com/voxgest/dryrun/VoxGestTfliteRecognizer.kt` | TFLite loader, validator, old-model shape guard. | ACTIVE |
| `android_dry_run/app/src/main/java/com/voxgest/dryrun/DynamicWordAcceptanceGate.kt` | Confidence, margin, hand-presence, consecutive-window, cooldown gate. | ACTIVE |
| `android_dry_run/app/src/main/java/com/voxgest/dryrun/AndroidLandmarkInputPolicy.kt` | Mirror policy and velocity-delta flag. | ACTIVE |
| `android_dry_run/app/src/main/java/com/voxgest/dryrun/OneHandCalibrationRecorder.kt` | Writes Android calibration JSON exports. | ACTIVE |
| `android_dry_run/app/src/main/java/com/voxgest/dryrun/RecognitionAutoRouter.kt` | Landmark-based route/state decision. | ACTIVE |
| `android_dry_run/app/src/main/java/com/voxgest/dryrun/FullSign225FeatureBuilder.kt` | Fullsign225 feature builder, bundled but disabled for live output. | EXPERIMENTAL |
| `android_dry_run/app/src/main/java/com/voxgest/dryrun/AlphabetClassifier.kt` | Static alphabet classifier wrapper. | LEGACY |
| `android_dry_run/app/src/main/java/com/voxgest/dryrun/AlphabetAcceptanceGate.kt` | Static alphabet gate; inactive in current camera output. | LEGACY |
| `android_dry_run/app/src/main/java/com/voxgest/dryrun/ConversationHistoryManager.kt` | Conversation history persistence. | ACTIVE |
| `android_dry_run/app/src/main/java/com/voxgest/dryrun/SpeechController.java` | TTS/speech controller. | ACTIVE |
| `android_dry_run/app/src/main/java/com/voxgest/dryrun/ui/VoxGestPresentationApp.kt` | Compose presentation/demo UI. | ACTIVE |
| `android_dry_run/app/src/main/java/com/voxgest/app/avatar/*` | Avatar rendering, loading, state, and motion stack. | ACTIVE |
| `android_dry_run/app/src/main/assets/model/*` | Bundled TFLite, labels, manifests, MediaPipe task files. | ACTIVE/EXPERIMENTAL |
| `android_dry_run/app/src/main/assets/avatar/*` | Avatar GLB, sign JSON, animation assets. | ACTIVE |
| `android_dry_run/app/src/main/java/**/*.bak_*` | Historical backup copies. | BACKUP |

## 2. Python ML, Training, Import, And Test Scripts

Root `scripts_ml/` is the canonical source tree. `tools/`, `external_datasets/`, and the evaluation package contain duplicate snapshots for recording and provenance.

| Filename | Role | Stage | Status |
|---|---|---|---|
| `13_download_target_words.py` | Downloads WLASL/YouTube target-word videos. | recording/import | LEGACY |
| `16_record_manual_words.py` | Records desktop webcam 30x162 manual word samples. | recording | LEGACY |
| `17_test_word_accuracy.py` | Sanity-checks trained desktop word model accuracy. | evaluation | LEGACY |
| `18_extract_lstm.py` | Extracts 30x162 word features from videos. | preprocessing | LEGACY |
| `19_train_lstm.py` | Trains old LSTM temporal word classifier. | training | LEGACY |
| `20_webcam_dual.py` | Desktop live recognizer for static alphabet plus dynamic words. | demo | LEGACY |
| `21_diagnostic.py` | Small live model diagnostic. | diagnostic | LEGACY |
| `22_diagnostic_lstm.py` | Live diagnostic for 30x162 motion model. | diagnostic | LEGACY |
| `23_archive_manual_samples.py` | Archives manual samples without deleting them. | utility | LEGACY |
| `24_train_tcn.py` | Trains older basic TCN model families. | training | LEGACY |
| `25_sprint_status.py` | Reports recording/training readiness. | audit | LEGACY |
| `26_record_phrase_intents.py` | Records endpoint phrase-intent gestures. | recording | EXPERIMENTAL |
| `27_train_phrase_tcn.py` | Trains endpoint phrase-intent TCN. | training | EXPERIMENTAL |
| `28_extract_phrase_videos.py` | Extracts phrase-intent features from videos. | preprocessing | EXPERIMENTAL |
| `30_audit_recognition_dataset.py` | Audits recognition dataset shapes/readiness. | audit | LEGACY |
| `30_eval_static_alphabet_live.py` | Evaluates static alphabet live logs. | evaluation | LEGACY |
| `31_record_static_calibration.py` | Records static alphabet calibration frames. | recording | LEGACY |
| `32_train_static_landmark_v4.py` | Trains static landmark alphabet classifier. | training | LEGACY |
| `33_live_word_test_logger.py` | Logs desktop live word tests. | evaluation | LEGACY |
| `33_live_word_test_logger_BACKUP_BEFORE_FULLSIGN_FIX.py` | Backup of live word logger. | backup | BACKUP |
| `34_compare_live_logs.py` | Compares live test logs. | evaluation | LEGACY |
| `34_record_motion_letters.py` | Records J/Z motion-letter samples. | recording | LEGACY |
| `35_discover_expansion_dataset.py` | Discovers expansion video/dataset candidates. | utility | LEGACY |
| `35_train_motion_letter_tcn.py` | Trains dynamic motion-letter TCN. | training | LEGACY |
| `36_bootstrap_fullsign225_negatives.py` | Bootstraps fullsign225 negative samples. | preprocessing | LEGACY |
| `37_cleanup_fullsign225_video_dataset.py` | Plans fullsign225 cleanup. | audit | LEGACY |
| `38_clean_fullsign225_video_dataset.py` | Cleans fullsign225 video dataset. | preprocessing | LEGACY |
| `39_audit_fullsign225_team16_extraction.py` | Audits fullsign225 team16 extraction. | audit | LEGACY |
| `40_extract_fullsign225_team16_features.py` | Extracts fullsign225 team16 features. | preprocessing | LEGACY |
| `41_record_manual_fullsign225_words.py` | Records fullsign225 manual samples. | recording | LEGACY |
| `42_audit_fullsign225_manual15_dataset.py` | Audits manual15 fullsign225 dataset. | audit | LEGACY |
| `42_audit_fullsign225_manual16_dataset.py` | Audits manual16 fullsign225 dataset. | audit | LEGACY |
| `42_audit_fullsign225_manual5_dataset.py` | Audits manual5 fullsign225 dataset. | audit | LEGACY |
| `47_merge_fullsign225_team_recorded_features.py` | Merges teammate fullsign225 samples. | import | LEGACY |
| `48_audit_fullsign225_manual5_team_features.py` | Audits merged fullsign225 team features. | audit | LEGACY |
| `49_prepare_fullsign225_anas_seed_features.py` | Prepares Anastacia-only fullsign225 seed dataset. | preprocessing | LEGACY |
| `50_audit_fullsign225_manual5_anas_seed_features.py` | Audits Anastacia seed fullsign225 dataset. | audit | LEGACY |
| `51_train_residual_dilated_tcn.py` | Experimental residual dilated TCN trainer. | training | EXPERIMENTAL |
| `52_merge_fullsign225_manual5_team_v2.py` | Merges fullsign225 team v2 outputs. | import | LEGACY |
| `53_audit_fullsign225_manual5_team_v2.py` | Audits fullsign225 team v2 features. | audit | LEGACY |
| `60_merge_fullsign225_phrase_v1.py` | Wrapper to merge fullsign225 phrase-v1 samples. | import | EXPERIMENTAL |
| `61_audit_fullsign225_phrase_v1.py` | Wrapper to audit fullsign225 phrase-v1 samples. | audit | EXPERIMENTAL |
| `62_merge_onehand162_phrase_v1.py` | Wrapper to merge onehand162 phrase-v1 samples. | import | EXPERIMENTAL |
| `63_audit_onehand162_phrase_v1.py` | Wrapper to audit onehand162 phrase-v1 samples. | audit | EXPERIMENTAL |
| `64_process_latest_phrase_recordings.py` | Processes latest phrase-v1 recording packs. | import | EXPERIMENTAL |
| `65_train_phrase_v1_tcn.py` | Profile-explicit phrase-v1 TCN trainer. | training | EXPERIMENTAL |
| `66_live_demo_runtime_plan.py` | Builds demo-safe runtime plan from live logs. | evaluation | EXPERIMENTAL |
| `70_import_android_onehand_exports.py` | Imports Android calibration JSON exports to .npy. | import | ACTIVE |
| `71_audit_android_onehand_exports.py` | Audits imported Android onehand samples. | audit | EXPERIMENTAL |
| `72_train_android_calibrated_onehand162.py` | Trains Android-calibrated onehand162 TCN. | training | EXPERIMENTAL |
| `check_20f_npy_shapes.py` | Checks 20-frame Android .npy shapes. | audit | EXPERIMENTAL |
| `clean_backcam_patch.py` | One-off Android back-camera patch helper. | utility | BACKUP |
| `export_tflite_fixed.py` | Utility to export/verify Keras models to TFLite. | export | LEGACY |
| `fix_backcam_log.py` | One-off back-camera log patch helper. | utility | BACKUP |
| `frame_quality_gate.py` | Desktop landmark quality gate. | utility | LEGACY |
| `gesture_segmenter.py` | Endpoint gesture segmenter. | preprocessing | EXPERIMENTAL |
| `lstm_features.py` | Shared Python feature builder. | preprocessing | LEGACY |
| `motion_letter_config.py` | Motion-letter config. | configuration | LEGACY |
| `patch_162_skeleton_overlay.py` | One-off skeleton overlay patch helper. | utility | BACKUP |
| `patch_avp_demo.py` | One-off avatar/presentation patch helper. | utility | BACKUP |
| `patch_back_camera.py` | One-off back-camera patch helper. | utility | BACKUP |
| `patch_camera_switch.py` | One-off camera switch patch helper. | utility | BACKUP |
| `patch_camera_switch_safe.py` | Safer camera switch patch helper. | utility | BACKUP |
| `phrase_builder.py` | Token-based phrase builder. | utility | ACTIVE |
| `phrase_config.py` | Phrase-intent label config. | configuration | EXPERIMENTAL |
| `phrase_v1_dataset_tools.py` | Merge/audit helpers for phrase-v1 teammate datasets. | import/audit | EXPERIMENTAL |
| `repair_camera_switch.py` | One-off camera switch repair helper. | utility | BACKUP |
| `test_phrase_builder.py` | Unit tests for phrase_builder. | testing | ACTIVE |
| `token_composer.py` | Token composer for accepted predictions. | utility | LEGACY |
| `voxgest_blender_avatar.py` | Blender avatar generation script. | utility | EXPERIMENTAL |
| `word_config.py` | Shared desktop word vocabulary/config. | configuration | LEGACY |

## 3. Dataset, Import, And Audit Areas

| Path | Purpose | Status |
|---|---|---|
| `external_datasets/android_phone_exports/` | Landing area for phone JSON calibration exports. | ACTIVE input area |
| `external_datasets/android_onehand162_phrase_v1_features/` | Import output for Android onehand162 `.npy` and `.meta.json`. | ACTIVE/NEEDS_NAMING_REVIEW |
| `external_datasets/android_onehand162_20f_features/` | Referenced by `check_20f_npy_shapes.py`. | EXPERIMENTAL/UNCERTAIN |
| `external_datasets/onehand162_phrase_v1_features/` | Older teammate merged onehand162 phrase dataset, 30x162. | LEGACY |
| `external_datasets/fullsign225_phrase_v1_features/` | Older teammate merged fullsign225 phrase dataset, 30x225. | EXPERIMENTAL/LEGACY |
| `external_datasets/team_incoming_recorded_features/` | Incoming teammate recorder packs and extracted snapshots. | GENERATED/BACKUP |
| `dataset_words_lstm/` | Desktop word LSTM/TCN 30x162 dataset. | LEGACY |
| `dataset_words_lstm_fullsign225/` | Desktop fullsign225 word dataset. | LEGACY |
| `dataset_phrase_intents/` | Endpoint phrase-intent dataset. | EXPERIMENTAL |
| `dataset_motion_letters/` | Dynamic alphabet letter dataset. | LEGACY |

## 4. Model Files And Manifests

### Android Asset Manifests

| Manifest | Profile | Feature | Input Shape | Sequence | Feature Size |
|---|---|---|---|---|---|
| android_dry_run/app/src/main/assets/model/runtime_manifest_fullsign225_manual5_team_v2.json | fullsign225_manual5_team_v2 | fullsign225 | [1, 30, 225] | 30 | 225 |
| android_dry_run/app/src/main/assets/model/runtime_manifest_fullsign225_phrase_v1.json | fullsign225_phrase_v1 | fullsign225 | [1, 30, 225] | 30 | 225 |
| android_dry_run/app/src/main/assets/model/runtime_manifest_onehand162_android_calibrated_v1.json | onehand162_android_calibrated_v1 | onehand162 | [1, 20, 162] | 20 | 162 |
| android_dry_run/app/src/main/assets/model/runtime_manifest_onehand162_phrase_v1.json | onehand162_phrase_v1 | onehand162 | [1, 20, 162] | 20 | 162 |

### Root Model Manifests

| Manifest | Profile | Feature | Input Shape | Sequence | Feature Size |
|---|---|---|---|---|---|
| model/runtime_manifest_fullsign225_manual15.json | fullsign225_manual15 | fullsign225 | [1, 30, 225] | 30 | 225 |
| model/runtime_manifest_fullsign225_manual5.json | fullsign225_manual5 | fullsign225 | [1, 30, 225] | 30 | 225 |
| model/runtime_manifest_fullsign225_manual5_anas_seed.json | fullsign225_manual5_anas_seed | fullsign225 | [1, 30, 225] | 30 | 225 |
| model/runtime_manifest_fullsign225_manual5_team.json | fullsign225_manual5_team | fullsign225 | [1, 30, 225] | 30 | 225 |
| model/runtime_manifest_fullsign225_manual5_team_v2.json | fullsign225_manual5_team_v2 | fullsign225 | [1, 30, 225] | 30 | 225 |
| model/runtime_manifest_fullsign225_phrase_v1.json | fullsign225_phrase_v1 | fullsign225 | [1, 30, 225] | 30 | 225 |
| model/runtime_manifest_fullsign225_team16.json | fullsign225_team16 | fullsign225 | [1, 30, 225] | 30 | 225 |
| model/runtime_manifest_onehand162_android_calibrated_v1.json | onehand162_android_calibrated_v1 | onehand162 | [1, 30, 162] | 30 | 162 |
| model/runtime_manifest_onehand162_phrase_v1.json | onehand162_phrase_v1 | onehand162 | [1, 30, 162] | 30 | 162 |
| model/runtime_manifest_rd_tcn_fullsign225_manual5_team.json | fullsign225_manual5_team | fullsign225 | None | None | None |
| model/runtime_manifest_sprint30.json | sprint30 | None | None | None | None |
| model/runtime_manifest_sprint30_fullsign225.json | sprint30_fullsign225 | fullsign225 | None | None | None |
| model/runtime_manifest_v1.json | None | None | None | None | None |

Finding: Android asset onehand manifests are 20-frame, while root `model/runtime_manifest_onehand162_*` files still report 30-frame. Treat root copies as generated/needs-sync until retraining updates them.

## 5. Reports And Evaluation Files

| Pattern | Purpose | Status |
|---|---|---|
| `reports/android_onehand162_import_report.json` | Android import output. | ACTIVE/GENERATED |
| `reports/android_onehand162_audit_report.json` | Android audit output from current audit script. | GENERATED/NEEDS_20F_UPDATE |
| `reports/onehand162_phrase_v1_*` | Older onehand162 merge/audit reports. | LEGACY/GENERATED |
| `reports/fullsign225_*` | Fullsign experiments, merges, audits, training logs. | LEGACY/GENERATED |
| `reports/live_word_test_log_*` | Desktop live testing logs. | LEGACY/GENERATED |
| `reports/live_phrase_demo_runtime_plan.*` | Demo runtime planning output. | EXPERIMENTAL/GENERATED |
| `reports/static_alphabet_*` | Static alphabet evaluation. | LEGACY/GENERATED |

## 6. Documentation Files

- `README.md`, `README_CURRENT.md`, `README_ANDROID_DRY_RUN.md`: orientation docs.
- `ANDROID_DEVELOPER_HANDOFF.md`: Android handoff context.
- `VOXGEST_*`, `SESSION_HANDOFF_*`, `SINGLE_HAND_*`: progress and historical decisions.
- Thesis PDFs/DOCX/PPTX: defense/reference assets.
- This report set under `docs/`: active documentation.

## 7. Experimental, Legacy, Backup, And Generated Files

- Android `.bak_*` files and `android_dry_run/*.py` patch helpers are BACKUP.
- `tools/VoxGest_Recorder_*` folders are BACKUP recorder packs.
- `CLAUDE_VOXGEST_EVALUATION_PACKAGE/` is an untracked evaluation snapshot with outdated 30-frame copies.
- `voxgest_env/` and `recorder_env/` are GENERATED dependency environments.

## 8. Defense-Ready Explanation

Python is VoxGest's offline ML factory. It records or imports gestures, converts them into landmark feature arrays, audits shape and class readiness, trains temporal models, exports TFLite, writes labels and manifests, and creates reports. Android is the live assistive app. It captures camera frames, extracts MediaPipe landmarks, builds feature arrays in Kotlin, runs TFLite, applies acceptance gates, and emits accepted labels to sentence output, TTS/history, and avatar feedback.

Recorded gesture data becomes a trained model through JSON or `.npy` samples, importer/audit scripts, a trainer, generated `.h5`/`.tflite`, class-label JSON, and runtime manifest JSON. The Android app bundles the `.tflite`, labels, and manifest under assets, loads them through `RecognitionProfile.kt` and `VoxGestTfliteRecognizer.kt`, and only accepts results after `DynamicWordAcceptanceGate.kt` passes confidence, margin, hand-presence, consecutive-match, and cooldown rules.

Some files are experimental or legacy because VoxGest evolved through static alphabet, LSTM, TCN sprint30, fullsign225, phrase-intent, onehand162 phrase-v1, and Android-calibrated onehand paths. The current Android runtime is 20-frame onehand162 with a guard against old 30-frame models.
