# VoxGest Active Pipeline Map

## Current Active Recognition Path

```text
Android camera frame
  -> MediaPipeLandmarkExtractor.kt
  -> LandmarkFrame / LandmarkPoint contracts
  -> OneHand162FeatureBuilder.kt
  -> LandmarkSequenceBuffer.kt, SEQUENCE_LENGTH=20
  -> VoxGestTfliteRecognizer.kt
  -> DynamicWordAcceptanceGate.kt
  -> VoxGestCameraRecognitionController.kt accepted result callback
  -> Sign/Phrases/History/TTS/avatar UI layers
```

## Dataset To Model Pipeline

```text
Android calibration export JSON
  -> OneHandCalibrationRecorder.kt writes feature_array + sequence_length_at_export
  -> external_datasets/android_phone_exports/VoxGestCalibration/onehand162_phrase_v1/<LABEL>/*.json
  -> scripts_ml/70_import_android_onehand_exports.py
  -> external_datasets/android_onehand162_phrase_v1_features/<LABEL>/*.npy + *.meta.json
  -> scripts_ml/71_audit_android_onehand_exports.py (needs 20-frame update)
  -> scripts_ml/72_train_android_calibrated_onehand162.py (needs 20-frame update)
  -> model/voxgest_tcn_onehand162_android_calibrated_v1.tflite
  -> model/class_labels_tcn_onehand162_android_calibrated_v1.json
  -> model/runtime_manifest_onehand162_android_calibrated_v1.json
  -> android_dry_run/app/src/main/assets/model/* copied/bundled
  -> RecognitionProfile.kt selects active profile
  -> VoxGestTfliteRecognizer.kt loads assets and validates/guards shape
```

## Exact Active Files By Step

| Step | Files |
|---|---|
| Android capture | `VoxGestCameraRecognitionController.kt`, `MediaPipeLandmarkExtractor.kt`, `ImageProxyBitmapConverter.kt`, CameraX config in `app/build.gradle` |
| Landmark contract | `LandmarkExtractor.kt`, `RecognitionAutoRouter.kt` |
| Feature building | `OneHand162FeatureBuilder.kt`, `AndroidLandmarkInputPolicy.kt`, `LandmarkSequenceBuffer.kt` |
| Runtime model config | `RecognitionProfile.kt`, `OneHandCalibrationConfig.kt`, Android onehand runtime manifests |
| Model inference | `VoxGestTfliteRecognizer.kt`, `TfliteModelLoader.java`, onehand `.tflite` assets |
| Acceptance | `DynamicWordAcceptanceGate.kt`, `RecognitionResult.java`, `RecognitionFeedback.kt` |
| Output | `VoxGestCameraRecognitionController.kt`, `SignFragment.kt`, `PhrasesFragment.kt`, `ConversationHistoryManager.kt`, `SpeechController.java`, avatar controllers/assets |
| Export | `OneHandCalibrationRecorder.kt` |
| Import | `scripts_ml/70_import_android_onehand_exports.py` |
| Audit | `scripts_ml/71_audit_android_onehand_exports.py` (30-frame risk), `scripts_ml/check_20f_npy_shapes.py` (experimental) |
| Training | `scripts_ml/72_train_android_calibrated_onehand162.py` (30-frame risk) |
| Artifacts | `model/voxgest_tcn_onehand162_android_calibrated_v1.*`, labels, manifest, Android copied assets |

## File Connection Graph

```text
Phone accepted calibration frames
  -> JSON export from OneHandCalibrationRecorder.kt
  -> 70_import_android_onehand_exports.py
  -> .npy/.meta feature dataset
  -> 71_audit_android_onehand_exports.py
  -> 72_train_android_calibrated_onehand162.py
  -> TFLite + labels + runtime manifest
  -> android assets/model
  -> RecognitionProfile.kt
  -> VoxGestTfliteRecognizer.kt
  -> DynamicWordAcceptanceGate.kt
  -> VoxGestCameraRecognitionController.kt
  -> UI sentence/TTS/history/avatar output
```

## Current Shape Reality

| Location | Shape |
|---|---|
| Android asset onehand manifests | `[1, 20, 162]` |
| `RecognitionProfile.ONEHAND162_INPUT_SHAPE` | `[1, 20, 162]` |
| `LandmarkSequenceBuffer.SEQUENCE_LENGTH` | `20` |
| Root `model/runtime_manifest_onehand162_*.json` | `[1, 30, 162]`, needs sync if treated as source of truth |
| `scripts_ml/70_import_android_onehand_exports.py` | `EXPECTED_SHAPE = (20, 162)` |
| `scripts_ml/71_audit_android_onehand_exports.py` | `EXPECTED_SHAPE = (30, 162)`, needs update |
| `scripts_ml/72_train_android_calibrated_onehand162.py` | `EXPECTED_SHAPE = (30, 162)`, needs update |

## Defense Summary

Python is the data/model factory. It converts recordings into arrays, audits them, trains models, exports TFLite, and writes manifests. Android is the live app. It captures landmarks, mirrors/selects the configured hand, builds feature arrays, runs TFLite, gates the result, and updates UI, TTS, history, and avatar feedback. Legacy files exist because the project tried multiple model families before the current Android-calibrated onehand162 path.
