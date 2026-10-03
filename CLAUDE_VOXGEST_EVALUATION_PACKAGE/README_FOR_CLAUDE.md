# VoxGest Gesture Processing Evaluation Package

Purpose:
This package is for evaluating how VoxGest processes recorded sign language gestures from Android-recorded JSON feature files into model predictions and Android app output.

Current active camera-recognition model:
onehand162_android_calibrated_v1

Current stable recognized camera words:
MY
WHAT
YOUR
NAME
NOTHING

Pipeline:
1. Android camera captures the signer.
2. MediaPipe extracts pose and hand landmarks.
3. OneHand162FeatureBuilder converts landmarks into 162 features per frame.
4. LandmarkSequenceBuffer collects 30 frames.
5. TFLite model predicts one label.
6. DynamicWordAcceptanceGate checks confidence, margin, hand presence, duplicate/cooldown, and NOTHING.
7. VoxGestPresentationApp displays accepted words and sentence output.

Main files:
- scripts_ml/70_import_android_onehand_exports.py
- scripts_ml/71_audit_android_onehand_exports.py
- scripts_ml/72_train_android_calibrated_onehand162.py
- model/voxgest_tcn_onehand162_android_calibrated_v1.tflite
- model/class_labels_tcn_onehand162_android_calibrated_v1.json
- model/runtime_manifest_onehand162_android_calibrated_v1.json
- android_runtime/VoxGestCameraRecognitionController.kt
- android_runtime/OneHand162FeatureBuilder.kt
- android_runtime/LandmarkSequenceBuffer.kt
- android_runtime/VoxGestTfliteRecognizer.kt
- android_runtime/DynamicWordAcceptanceGate.kt
- android_ui/VoxGestPresentationApp.kt

Question to evaluate:
How does VoxGest process recorded Android sign gesture samples, convert them into 30x162 landmark sequences, train/load the model, and decide whether to output MY, WHAT, YOUR, NAME, or NOTHING?
