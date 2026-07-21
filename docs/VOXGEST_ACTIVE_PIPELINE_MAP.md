# VoxGest Active Pipeline Map

Generated on 2026-06-12. Documentation-only review; no runtime, model, dataset, Android, or training behavior was changed.

## Current Active Runtime Path

```text
Android camera frame
  -> ImageProxyBitmapConverter.kt
  -> MediaPipeLandmarkExtractor.kt
  -> LandmarkFrame / LandmarkPoint in LandmarkExtractor.kt
  -> RecognitionAutoRouter.kt
  -> OneHand162FeatureBuilder.kt
  -> LandmarkSequenceBuffer.kt, SEQUENCE_LENGTH = 20
  -> VoxGestTfliteRecognizer.kt
  -> DynamicWordAcceptanceGate.kt
  -> VoxGestCameraRecognitionController.kt accepted-result callback
  -> Sign/Phrases/History/TTS/STT/avatar UI output
```

## Dataset To Android Model Pipeline

```text
Android phone calibration JSON
  -> OneHandCalibrationRecorder.kt writes feature_array + sequence_length_at_export
  -> external_datasets/android_phone_exports/VoxGestCalibration/onehand162_phrase_v1/<LABEL>/*.json
  -> scripts_ml/70_import_android_onehand_exports.py
  -> external_datasets/android_onehand162_phrase_v1_features/<LABEL>/*.npy + *.meta.json
  -> scripts_ml/71_audit_android_onehand_exports.py
  -> scripts_ml/72_train_android_calibrated_onehand162.py
  -> model/voxgest_tcn_onehand162_android_calibrated_v1.tflite
  -> model/class_labels_tcn_onehand162_android_calibrated_v1.json
  -> model/runtime_manifest_onehand162_android_calibrated_v1.json
  -> android_dry_run/app/src/main/assets/model/*
  -> RecognitionProfile.kt selects profile
  -> VoxGestTfliteRecognizer.kt validates labels/shape and runs or blocks inference
```

Important current mismatch: the Android runtime and Android asset manifests use 20 frames, but root `scripts_ml/71_audit_android_onehand_exports.py`, root `scripts_ml/72_train_android_calibrated_onehand162.py`, and root onehand manifests still carry 30-frame assumptions.

## Exact Active Files By Step

| Step | Files |
|---|---|
| Android capture | `VoxGestCameraRecognitionController.kt`, `ImageProxyBitmapConverter.kt`, `MediaPipeLandmarkExtractor.kt`, `AndroidManifest.xml`, `app/build.gradle` |
| Landmark contract | `LandmarkExtractor.kt`, `LandmarkFrameProvider.kt` |
| Feature building | `OneHand162FeatureBuilder.kt`, `AndroidLandmarkInputPolicy.kt`, `LandmarkSequenceBuffer.kt` |
| Runtime config | `RecognitionProfile.kt`, `OneHandCalibrationConfig.kt`, Android runtime manifests |
| Calibration export | `OneHandCalibrationRecorder.kt`, `VoxGestCameraRecognitionController.kt` |
| Import | `scripts_ml/70_import_android_onehand_exports.py` |
| Audit | `scripts_ml/71_audit_android_onehand_exports.py`, `scripts_ml/check_20f_npy_shapes.py` |
| Training/export | `scripts_ml/72_train_android_calibrated_onehand162.py` |
| Artifacts | `model/voxgest_tcn_onehand162_android_calibrated_v1.tflite`, `model/class_labels_tcn_onehand162_android_calibrated_v1.json`, `model/runtime_manifest_onehand162_android_calibrated_v1.json` |
| Android assets | `android_dry_run/app/src/main/assets/model/voxgest_tcn_onehand162_android_calibrated_v1.tflite`, labels, manifest, `hand_landmarker.task`, `pose_landmarker_lite.task` |
| Inference | `VoxGestTfliteRecognizer.kt`, `TfliteModelLoader.java` |
| Acceptance | `DynamicWordAcceptanceGate.kt`, `RecognitionResult.java`, `RecognitionFeedback.kt` |
| UI output | `SignFragment.kt`, `PhrasesFragment.kt`, `HistoryFragment.kt`, `ConversationHistoryManager.kt`, `SpeechController.java`, avatar assets/controllers |

## Shape Truth Table

| Location | Current shape/meaning |
|---|---|
| `LandmarkSequenceBuffer.SEQUENCE_LENGTH` | `20` |
| `RecognitionProfile.ONEHAND162_INPUT_SHAPE` | `[1, 20, 162]` |
| Android asset `runtime_manifest_onehand162_android_calibrated_v1.json` | `[1, 20, 162]` |
| Android asset `runtime_manifest_onehand162_phrase_v1.json` | `[1, 20, 162]` |
| `scripts_ml/70_import_android_onehand_exports.py` | `EXPECTED_SHAPE = (20, 162)` |
| `scripts_ml/71_audit_android_onehand_exports.py` | `EXPECTED_SHAPE = (30, 162)`; needs update for current 20-frame data |
| `scripts_ml/72_train_android_calibrated_onehand162.py` | `EXPECTED_SHAPE = (30, 162)`; needs update before retraining active 20-frame model |
| Root `model/runtime_manifest_onehand162_android_calibrated_v1.json` | `[1, 30, 162]`; not synced with Android asset manifest |
| `VoxGestTfliteRecognizer.kt` | Detects old `[1,30,162]` TFLite under a 20-frame profile and blocks inference |

## File Connection Graph

```text
recorded samples
  -> OneHandCalibrationRecorder.kt
  -> phone JSON export folder
  -> 70_import_android_onehand_exports.py
  -> 20x162 .npy feature dataset
  -> 71_audit_android_onehand_exports.py / check_20f_npy_shapes.py
  -> 72_train_android_calibrated_onehand162.py
  -> TFLite + labels + runtime manifest
  -> android assets/model
  -> RecognitionProfile.kt
  -> VoxGestTfliteRecognizer.kt
  -> DynamicWordAcceptanceGate.kt
  -> VoxGestCameraRecognitionController.kt
  -> Sign/Phrases/History/TTS/avatar UI output
```

## Defense Summary

Python prepares VoxGest models: import recordings, audit shapes/counts, train, export TFLite, and write labels/manifests. Android runs the accessibility app: capture landmarks, build 20-frame onehand162 sequences, run TFLite, gate predictions, and present sentence/TTS/STT/avatar feedback. The active runtime is 20-frame; the root audit/trainer must be updated before claiming a freshly trained 20-frame calibrated model.
