# VOXGEST - CURRENT SYSTEM ARCHITECTURE

**Audit date:** 2026-08-24  
**Scope:** read-only repository audit. Code and machine-readable artifacts take precedence over older handoffs.  
**Status vocabulary:** IMPLEMENTED, EXPERIMENTAL, PLANNED, NOT IMPLEMENTED, UNVERIFIED.

## Executive finding

VoxGest is a landmark-based temporal sign-recognition project with several retained generations. The newest FSL branch has completed extraction of **16,146 validated `(20, 162)` windows from 1,038 FSL-105 training videos for 64 confirmed one-handed labels**, but it has **not trained or exported an FSL model**. Its RD-TCN manifest explicitly says `pending_training`.

The Android source has a real CameraX/MediaPipe/TFLite one-hand path and a 20-frame buffer, but the bundled onehand TFLite artifacts were verified as **`[1, 30, 162]`**, while their Android asset manifests declare **`[1, 20, 162]`**. `VoxGestTfliteRecognizer` detects this old-model shape and blocks inference. Therefore, a 20-frame Android UI/pipeline exists, but a verified working 20-frame model deployment does not.

## 1. Project engineering evolution

Only repository-supported evolution is listed below.

1. **ASL/static prototype - IMPLEMENTED historically.** `voxgest_v3.tflite`, `class_labels_v3.json`, `StaticLetterRecognizer.java`, and `32_train_static_landmark_v4.py` show a 63-feature single-hand static alphabet branch.
2. **Landmark-based dynamic recognition - IMPLEMENTED.** `lstm_features.py` introduced Pose plus one selected hand: 33 x 3 pose values plus 21 x 3 hand values = 162 values/frame. This replaced raw-image classification in the dynamic path.
3. **30-frame LSTM and basic TCN experiments - IMPLEMENTED/LEGACY.** `19_train_lstm.py`, `24_train_tcn.py`, saved LSTM/TCN reports, and 30-frame artifacts support this phase.
4. **OneHand162 and FullSign225 divergence - IMPLEMENTED.** OneHand162 retains one selected hand; FullSign225 preserves pose plus fixed physical-left and physical-right hand slots (99 + 63 + 63 = 225). The latter is experimental and not the enabled Android camera recognition route.
5. **Residual/dilated TCN direction - IMPLEMENTED experimentally.** `51_train_residual_dilated_tcn.py` and the saved fullsign RD-TCN artifact show this direction; `78_train_fsl_rdtcn.py` defines the future FSL version.
6. **Phone/Android-recorded feature data - IMPLEMENTED as collection/import infrastructure.** Android calibration export, `70_import_android_onehand_exports.py`, `74_import_fsl_exports.py`, and manifests document this path. The FSL phone import report currently records zero imported samples.
7. **Android deployment - IMPLEMENTED but model-contract-blocked.** CameraX, MediaPipe task assets, feature builders, buffering, TFLite loading, gates, and UI are present. The active onehand 20-frame runtime rejects the retained 30-frame model shape.
8. **FSL transition and FSL-105 pipeline - IMPLEMENTED as extraction/audit; training blocked by configuration/data alignment.** Handedness classification identified 64 one-handed classes, and `76_extract_fsl105_features.py` extracted train-only windows. The FSL trainer's label list is not aligned with those 64 classes.

## 2. Current architecture

### Target FSL-105 architecture (data path is implemented; model is not trained)

```text
FSL-105 train.csv video
  -> MediaPipe Holistic (pose + right hand)
  -> build_frame_features()
  -> nose-relative pose and hand coordinates; hand wrist-to-MCP scale; Z x 0.3
  -> 162 floats/frame = pose 99 + hand 63
  -> sliding 20-frame windows, stride 5, >=13 right-hand frames
  -> .npy (20,162) + .meta.json
  -> future RD-TCN [1,20,162] -> class probabilities -> gate/output (not implemented for FSL)
```

| Block | What it does | Input -> output | Responsible file(s) | Side/status |
|---|---|---|---|---|
| Camera/video | Decodes every frame of each listed training clip. | Video -> RGB frame | `76_extract_fsl105_features.py` | Python, IMPLEMENTED |
| Perception | Runs MediaPipe Holistic. | RGB -> 33 pose + 21 right-hand landmarks when detected | `76_extract_fsl105_features.py` | Python, IMPLEMENTED |
| Feature builder | Anchors values at the pose nose, optionally zeros missing hand, scales the hand, damps Z. | landmarks -> `(162,) float32` | `build_frame_features()` | Python, IMPLEMENTED |
| Temporalizer | Makes overlapping fixed-length examples. | frame vectors -> `(20,162)` windows | `process_video()` | Python, IMPLEMENTED |
| Dataset/audit | Persists arrays/metadata and validates shapes, finite values, and ownership. | `.npy/.meta.json` -> reports | `76_extract_fsl105_features.py`, `75_audit_fsl_dataset.py` | Python, IMPLEMENTED |
| Temporal classifier | Intended residual dilated TCN. | `[B,20,162]` -> `[B,C]` softmax | `78_train_fsl_rdtcn.py` | Python, EXPERIMENTAL; NOT TRAINED |
| Recognition/output | No FSL live runtime/model asset is wired. | probabilities -> accepted FSL token | none | NOT IMPLEMENTED |

### Current Android runtime architecture (implemented code, blocked deployment)

```text
CameraX frame
  -> MediaPipe pose/hand task models
  -> OneHand162FeatureBuilder
  -> 162-vector with nose-relative coordinates, wrist-to-MCP scale, Z x 0.3
  -> LandmarkSequenceBuffer (20 frames, rolling)
  -> VoxGestTfliteRecognizer
  -> raw class probabilities/top-3
  -> DynamicWordAcceptanceGate
  -> accepted token / UI, TTS, history, avatar
```

| Block | Input -> output | Important contract | File(s) | Status |
|---|---|---|---|---|
| Camera/landmarks | `ImageProxy` -> `LandmarkFrame` | front camera mirror policy; pose/hand task assets | `VoxGestCameraRecognitionController.kt`, `LandmarkExtractor.kt` | IMPLEMENTED |
| Feature building | landmarks -> `(162,)` | 99 pose + 63 selected hand | `OneHand162FeatureBuilder.kt` | IMPLEMENTED |
| Temporal buffer | vectors -> `(20,162)` | drops oldest frame when full | `LandmarkSequenceBuffer.kt` | IMPLEMENTED |
| TFLite recognition | `[1,20,162]` requested -> probabilities | labels `WHAT,YOUR,NAME,MY,NSAC` | `VoxGestTfliteRecognizer.kt` | IMPLEMENTED code; BLOCKED by bundled 30-frame model |
| Acceptance | raw result -> accepted/rejected token | confidence, margin, hand presence, stable consecutive windows, duplicate/cooldown rules | `DynamicWordAcceptanceGate.kt` | IMPLEMENTED for five-label onehand profile |
| Presentation | accepted token -> UI/history/TTS/avatar path | raw/rejected predictions must not alter sentence output | controller and Android UI/avatar classes | IMPLEMENTED/partly UNVERIFIED live |

The FSL branch is **not** the Android model currently loaded by this source. Current Android profile selection only allows `onehand162_phrase_v1` or `onehand162_android_calibrated_v1`; both use five labels and manifest a 20-frame request.

## 3. Feature engineering

### Implemented FSL-105 feature vector

`76_extract_fsl105_features.py:build_frame_features()` implements:

- Pose: 33 landmarks x `(x,y,z)` = **99** values.
- Hand: **right hand** only, 21 landmarks x `(x,y,z)` = **63** values. A missing right hand becomes 63 zeros; a missing pose zeros the complete 162-vector because no safe nose reference exists.
- Anchor: pose and hand coordinates have pose landmark 0 (nose) subtracted. The first pose triplet is then explicitly zero.
- Scale: after nose anchoring, every selected hand coordinate is divided by the Euclidean distance between hand wrist (0) and middle-finger MCP (9), only when scale `> 0.001`. Pose is not divided by this hand scale.
- Depth: every Z component (`2::3`) is multiplied by **0.3**.
- Concatenation: `[pose_values.flatten(), hand_values.flatten()]`, `float32`, shape **`(162,)`**.

The Android `OneHand162FeatureBuilder.kt` implements the same high-level recipe, including selected-hand mapping, pose masking, wrist-to-MCP scaling, first-triplet zeroing, and Z damping.

### Implemented historical/Android policy distinction

`lstm_features.py` also builds OneHand162, but its extracted dynamic frames are nose-relative and support single-hand pose masking. It does **not** apply the wrist-to-MCP scale or Z damping visible in the FSL extractor/Android builder. The controller's diagnostic text still says `normalization_policy=PYTHON_NOSE_RELATIVE_NO_WRIST_SCALE`; this conflicts with `OneHand162FeatureBuilder.kt` and must not be treated as current parity evidence.

| Item | Status | Evidence/meaning |
|---|---|---|
| 99 pose + 63 hand = 162 | IMPLEMENTED | FSL extractor and Android builder |
| Nose-relative translation | IMPLEMENTED | FSL extractor and Android builder |
| Wrist-to-MCP hand scale | IMPLEMENTED in FSL/Android | `76_extract_fsl105_features.py`, `OneHand162FeatureBuilder.kt` |
| Z damping x 0.3 | IMPLEMENTED in FSL/Android | same files |
| Single-hand pose masking | IMPLEMENTED in historical OneHand162/Android profile | `lstm_features.py`, Android builder; FSL extractor keeps all pose landmarks |
| Velocity/delta features | INACTIVE | Android constant `USE_VELOCITY_DELTA_FEATURES=false`; `buildWithDelta()` is dormant |
| 225-feature onehand+delta contract | EXPERIMENTAL/CONFLICTING | `buildWithDelta()` pads to 225, while 225 normally means pose + two hands |

Repository code supports the purpose of translation and scale normalization as reducing image-position and hand-size variation. It does not provide a formal empirical proof of the chosen 0.3 Z multiplier; treat its rationale as practical depth damping, not a validated optimum.

## 4. Temporal processing

Dynamic signs are not reliably identified by a single posture: motion direction, path, timing, and the relation between consecutive poses matter. VoxGest therefore classifies sequences, not MediaPipe labels.

### FSL-105

- Sequence length: **20 frames**; feature shape: **`(20,162)`**.
- Window generation: all decoded frames are retained, then `range(0, len(vectors)-20+1, 5)` produces windows with **stride 5**.
- Quality gate: a window needs at least **13/20** frames with a detected right hand (65%).
- The source split is `train.csv_only`; FSL `test.csv` is not extracted by `76_extract_fsl105_features.py`.
- No velocity vector is concatenated to the FSL arrays. Temporal motion is supplied by the ordered coordinate sequence itself.

### Android

- `LandmarkSequenceBuffer` holds **20** vectors and acts as a rolling/sliding buffer once full: each added frame removes the oldest.
- The controller invokes the recognizer whenever the full buffer is available and counts consecutive top-1 labels across sliding windows for its dynamic gate.
- A missing/invalid frame does not append. Eight lost-hand frames or 1.5 seconds without valid landmarks aborts the live collection; collection has an 8-10 second timeout depending on profile.
- Velocity delta calculation exists only behind the false `USE_VELOCITY_DELTA_FEATURES` flag. It is inactive and must not be assumed by a trained 162-feature model.

## 5. ML architecture and artifacts

### FSL RD-TCN - EXPERIMENTAL / NOT TRAINED

`78_train_fsl_rdtcn.py` defines the exact intended model:

- Input: `(20,162)`; output: `C` softmax classes.
- Three causal residual blocks, each with two `Conv1D(filters=64, kernel=3)` layers, `LayerNormalization`, ReLU, and `SpatialDropout1D(0.2)`; dilation rates **1, 2, 4**. A 1x1 residual projection is used if channel widths differ.
- Global average pooling -> Dense(64, ReLU) -> Dropout(0.3) -> Dense(C, softmax).
- Optimizer/loss: Adam `3e-4`, sparse categorical cross-entropy, accuracy metric.
- Training augmentation: temporal +/- one-frame shift, global scale 0.95-1.05, XY Gaussian noise (`sigma=0.005`), no Z noise, and re-zeroed nose triplet.
- Export: TFLite Optimize.DEFAULT; tries float16 conversion, then dynamic-range fallback. Android-ready criterion is validation accuracy >= 0.80 and model <= 500 KB.
- Parameter count: **UNKNOWN until an FSL model is built**; no `voxgest_rdtcn_fsl_v1.h5/.tflite` or FSL training report exists.

### Existing saved model families

| Family | Verified artifact contract | Architecture/status |
|---|---|---|
| OneHand162 phrase/calibrated | actual H5 and TFLite input **`[1,30,162]`**, output `[1,5]`, 273,837 parameters | Basic residual TCN, 96 filters, kernel 3, dilations 1/2/4/8, average+max pooling. EXPERIMENTAL; Android manifests incorrectly say 20 frames. |
| FullSign225 manual5 team v2 | `[1,30,225] -> [1,5]` | Basic TCN experiment. Saved grouped validation 16.75%; not promotable. |
| FullSign225 RD-TCN | `[1,30,225] -> [1,5]`, 153,545 parameters | Experimental residual/dilated TCN, saved report 41.32% grouped validation. |
| Older demo/sprint LSTM and TCN | 30-frame models and reports | IMPLEMENTED historical desktop experiments, not current FSL deployment. |
| Static alphabet | `[1,63] -> [1,29]` documented Android contract | Historical/disabled automatic Android output path. |

No CNN/raw-video image model was verified in the current pipeline code. `Conv1D` is used as a temporal convolution, not as an image CNN. LSTM is present in legacy Python tooling and artifacts, but no FSL LSTM is defined.

## 6. FSL-105 data pipeline

```text
FSL-105 labels.csv (105 labels) + train.csv (1,704 rows) + test.csv (426 rows)
  -> 93_classify_fsl105_handedness.py -> 64 confirmed one-handed labels
  -> 76_extract_fsl105_features.py reads only train.csv rows for those labels
  -> MediaPipe Holistic -> normalized 162-feature frames
  -> 20-frame windows, stride 5, right-hand-presence gate
  -> external_datasets/fsl_features/<LABEL>/fsl105_*_w*.npy + .meta.json
  -> 75_audit_fsl_dataset.py / reports/fsl/audit_report.json
  -> 78_train_fsl_rdtcn.py (currently blocked by label configuration)
  -> intended H5, TFLite, report, manifest update -> Android (not implemented)
```

| Asset/stage | Current role |
|---|---|
| `labels.csv` | Dataset catalogue: 105 `(id,label,category)` rows. `generate_fsl_labels.py` writes an unsanitized 105-class JSON map, distinct from actual usable FSL training labels. |
| `train.csv` | 1,704 source-video rows; the current FSL extractor uses this split only. |
| `test.csv` | 426 source-video rows; present but not consumed by current extraction/training code. |
| `class_labels_fsl.json` | Generated mapping of all 105 label names to IDs. It is not consumed by `78_train_fsl_rdtcn.py`, which derives labels from `fsl_config.py`. |
| `confirmed_onehanded_labels.json` | 64 classes accepted by the handedness workflow; controls `76_extract_fsl105_features.py` and `75_audit_fsl_dataset.py`. |
| `.npy + .meta.json` | Per-window float32 sequence plus label, source, `signer_id`, device, source video, window start, and hand-presence metadata. |
| `75_audit_fsl_dataset.py` | Requires `(20,162)`, finite values, valid metadata/device tag, and counts FSL-105 vs non-FSL device samples. |
| `74_import_fsl_exports.py` | Imports Android/phone JSON exports only if their stated shape matches `(20,162)`; current report imported 0. |

### Train/test and leakage

The extractor protects the supplied FSL test split by not reading it (`train.csv_only`). That is a source-level train/test separation. However, extraction produces overlapping windows from a video (stride 5), so a later random window split would leak near-duplicate video content. The FSL trainer attempts a per-label signer holdout when at least two signer IDs exist; FSL windows all record `signer_id=fsl105_<video_stem>`, so this is effectively a video-stem holdout if multiple stems are present. It falls back to random 80/20 for a single signer, which is weaker and should be reported as leakage risk.

## 7. FSL-105 current status

| Check | Actual status/evidence |
|---|---|
| Catalogue | 105 labels in `labels.csv`; 1,704 train and 426 test rows. |
| Handedness | 64 one-handed, 41 two-handed, 0 ambiguous in `reports/fsl/handedness_report.json`. |
| Pipeline-recognized/extracted classes | **64**, defined by `confirmed_onehanded_labels.json`, not all 105. |
| Extraction | COMPLETE for configured source: 1,038 completed training videos, 16,146 saved windows. |
| Sequence shape | All generated FSL windows audit as `(20,162)`; extraction summary reports zero shape mismatches, metadata errors, missing, unexpected, or orphan output files. |
| Class counts | Every 64 confirmed class has 180-366 FSL windows; all exceed audit minimum 100. No supplemental phone data is present. |
| Missing from onehand pipeline | 41 two-handed labels are intentionally excluded. In addition, many desired FSL app labels (`WHAT`, `YOUR`, `NAME`, `MY`, `NSAC`, `WATER`, etc.) have no FSL-105 onehand windows in this extraction. |
| Phone data | `fsl_import_report.json`: 0 imported. `device_diversity_report.json` flags 20 low-diversity labels. |
| Training readiness | **NOT READY for the current FSL trainer.** `fsl_config.py` lists 25 labels, of which only `HELLO`, `YES`, `NO`, and `UNDERSTAND` have extracted FSL-105 data and meet minima. The trainer requires at least five ready labels. |
| FSL model/TFLite/Android integration | NOT IMPLEMENTED; manifest status `pending_training`. |

There is an older `extract_fsl105.py` that samples 30 frames of hand-only coordinates. It is a separate, incompatible legacy extractor and must not be confused with the current `76_extract_fsl105_features.py` contract.

## 8. Device generalization

The same physical sign recorded by PC, laptop, iPhone, or Android will not yield identical raw landmark numbers. Camera position, focal length and perspective, resolution, front/back-camera mirroring, crop, exposure, frame rate, pose estimator tracking state, partial occlusion, and MediaPipe's normalized image coordinate system all change the observations. Even a stable signer rarely repeats a trajectory identically frame-for-frame.

Implemented defenses are landmark representation rather than raw pixels; nose-relative translation; hand wrist-to-MCP scale normalization in the FSL/Android builder; Z damping; temporal windows; and selected-hand/pose masking. The FSL trainer also defines modest XY noise, temporal jitter, and global-scale augmentation, but these defenses are **not active until FSL training is actually run**. Device/signer diversity remains unresolved: FSL windows are tagged `FSL105_VIDEO`, phone import is zero, and the audit identifies low-diversity labels.

## 9. Recognition logic

MediaPipe does **not** recognize a sign name. It returns detected landmarks. The sign label is produced only by a trained classifier operating on engineered temporal features.

```text
Landmark detection -> normalized feature sequence -> temporal model softmax
-> top class probability + top-2 margin -> quality/consistency gates -> accepted token/output
```

For the Android five-label onehand path, `VoxGestTfliteRecognizer` ranks output probabilities, derives confidence/top-1 and margin `(top1 - top2)`, and returns top-3. `DynamicWordAcceptanceGate` rejects blank/`NSAC`/unsupported labels, wrong input shape, low hand presence, low confidence, low margin, insufficient consecutive matches, duplicates, and cooldown conflicts. Only accepted results are sent downstream; `NSAC` is explicitly no-output. This logic exists, but cannot reach usable inference with the retained 30-frame model detected under the 20-frame profile.

No FSL class-probability thresholds, FSL decision gate, or FSL output token mapping were found.

## 10. Important engineering decisions

| Decision | Supported rationale | Alternative considered/not preferred | Status |
|---|---|---|---|
| MediaPipe landmarks | Reduces raw-image dependence and provides pose/hand geometry to both Python and Android. | Raw video CNN not found in the current implementation. | IMPLEMENTED |
| 162 features | Compact pose+selected-hand temporal representation. | FullSign225 preserves both hands but is a separate experimental profile. | IMPLEMENTED |
| Nose anchoring | Removes absolute image translation relative to a facial body reference. | Absolute image coordinates are not used in current FSL/Android feature builders. | IMPLEMENTED |
| Wrist-to-MCP scale | Reduces selected-hand scale variation. | No repository evidence of another scale rule for FSL/Android. | IMPLEMENTED |
| Z x 0.3 | Explicitly configured in FSL/Android builders to reduce depth contribution. | No validation study found. | IMPLEMENTED, rationale partly UNVERIFIED |
| Temporal 20-frame FSL/Android buffer | Motion signs require evidence across time; matches Android buffer and new FSL pipeline. | Retained 30-frame models conflict with it. | IMPLEMENTED code, deployment CONFLICT |
| TCN/RD-TCN | Temporal Conv1D supports causal/dilated sequence processing. | Legacy LSTM remains available; FSL uses RD-TCN plan. | TCN IMPLEMENTED historically; FSL RD-TCN EXPERIMENTAL |
| Local TFLite inference | Android has local interpreter/loading and no backend requirement. | Cloud inference not implemented. | IMPLEMENTED infrastructure |
| FSL-105 one-hand subset | Avoids training a one-hand vector on two-handed signs. | FullSign225 exists for two-hand experiments but not FSL wiring. | IMPLEMENTED extraction policy |
| Velocity deltas | Code exists for a 225-wide output. | Flag is false; incompatible with current OneHand162 training contract. | INACTIVE |

## 11. Current file map

### FSL and active Android/FSL-adjacent files

| File | Purpose | Input -> output | Connected component | Status |
|---|---|---|---|---|
| `scripts_ml/fsl_config.py` | FSL constants and 25 desired labels | config -> shapes/minima | FSL import/audit/trainer | EXPERIMENTAL; label conflict |
| `scripts_ml/73_setup_fsl_dataset_dirs.py` | Creates FSL data folder layout | config -> directories | FSL collection | IMPLEMENTED utility |
| `scripts_ml/74_import_fsl_exports.py` | Imports phone JSON samples | JSON -> `(20,162)` NPY/meta | FSL audit | IMPLEMENTED; 0 imports |
| `scripts_ml/75_audit_fsl_dataset.py` | Verifies FSL/phone data | arrays/meta -> audit JSON | FSL readiness | IMPLEMENTED |
| `scripts_ml/76_extract_fsl105_features.py` | Train-only FSL-105 extraction | video -> NPY/meta | FSL dataset | IMPLEMENTED |
| `scripts_ml/77_record_pc_webcam_fsl.py` | PC collection of FSL exports | webcam -> JSON exports | FSL importer | IMPLEMENTED collection tool |
| `scripts_ml/78_train_fsl_rdtcn.py` | Defines FSL RD-TCN train/export | FSL features -> H5/TFLite/report | FSL model | EXPERIMENTAL; blocked |
| `scripts_ml/84_classify_handedness.py` | Earlier handedness classifier | metadata/video -> classifications | FSL selection | EXPERIMENTAL |
| `scripts_ml/93_classify_fsl105_handedness.py` | MediaPipe-driven FSL-105 handedness audit | FSL videos -> reports/confirmed classes | FSL extractor | IMPLEMENTED audit |
| `scripts_ml/generate_fsl_labels.py` | Builds 105-ID label JSON | labels.csv -> `class_labels_fsl.json` | metadata only | IMPLEMENTED utility; not trainer input |
| `scripts_ml/extract_fsl105.py` | Older hand-only FSL extractor | video -> 30 sampled frames | obsolete FSL branch | LEGACY/INCOMPATIBLE |
| `scripts_ml/check_20f_npy_shapes.py` | Quick shape checker | a separate dataset path -> console | Android data check | EXPERIMENTAL; path mismatch |
| `scripts_ml/70_import_android_onehand_exports.py` | Imports Android calibration JSON | JSON -> `(20,162)` NPY/meta | Android-calibrated model | IMPLEMENTED |
| `scripts_ml/71_audit_android_onehand_exports.py` | Audits imported Android data | NPY/meta -> report | Android-calibrated model | EXPERIMENTAL; 30-frame assumption |
| `scripts_ml/72_train_android_calibrated_onehand162.py` | Trains Android calibrated TCN | Android NPY -> H5/TFLite/manifest | Android runtime | EXPERIMENTAL; 30-frame assumption |
| `model/runtime_manifest_fsl_rdtcn_v1.json` | FSL target contract | config -> manifest | future Android deployment | `pending_training` |
| `reports/fsl/extraction_summary.json` | Extraction integrity snapshot | current outputs -> 16,146 result | FSL status | GENERATED, verified |
| `reports/fsl/audit_report.json` | Per-label FSL/phone readiness | arrays/meta -> audit | FSL status | GENERATED, verified |
| `reports/fsl/handedness_report.json` | 64/41 class partition | FSL video analysis -> classes | FSL selection | GENERATED |

### Historical/experimental Python pipeline files

| File(s) | Purpose | Input -> output | Connected component | Status |
|---|---|---|---|---|
| `13_download_target_words.py`, `35_discover_expansion_dataset.py` | Acquire/discover word videos | video sources -> local data | desktop corpus | LEGACY |
| `16_record_manual_words.py`, `41_record_manual_fullsign225_words.py`, `34_record_motion_letters.py`, `26_record_phrase_intents.py` | Webcam collection | webcam -> landmark sequences | legacy word/fullsign/phrase branches | LEGACY/EXPERIMENTAL |
| `17_test_word_accuracy.py`, `21_diagnostic.py`, `22_diagnostic_lstm.py`, `33_live_word_test_logger.py`, `34_compare_live_logs.py`, `30_eval_static_alphabet_live.py` | Evaluation/diagnostics | models/logs -> measurements | desktop prototype | LEGACY |
| `18_extract_lstm.py`, `28_extract_phrase_videos.py` | Video feature extraction | videos -> sequences | 30-frame desktop paths | LEGACY |
| `19_train_lstm.py` | LSTM word classifier | 30x162 data -> H5/TFLite/report | desktop words | LEGACY |
| `24_train_tcn.py` | Basic TCN trainer | 30x162/225 data -> H5/TFLite/report | multiple historical profiles | LEGACY/EXPERIMENTAL |
| `51_train_residual_dilated_tcn.py` | RD-TCN experiment | fullsign data -> H5/TFLite/report | FullSign225 | EXPERIMENTAL |
| `27_train_phrase_tcn.py`, `65_train_phrase_v1_tcn.py` | Phrase temporal TCNs | phrase sequences -> artifacts | phrase branch | EXPERIMENTAL/frozen |
| `35_train_motion_letter_tcn.py` | J/Z motion TCN | 30x162 -> artifacts | alphabet extension | EXPERIMENTAL |
| `32_train_static_landmark_v4.py` | Static alphabet model | hand landmarks -> artifacts | static alphabet | LEGACY |
| `20_webcam_dual.py` | Desktop combined live recognizer | webcam -> tokens | demo10 prototype | LEGACY |
| `23_archive_manual_samples.py`, `25_sprint_status.py`, `30_audit_recognition_dataset.py`, `31_record_static_calibration.py` | Dataset maintenance/readiness | local data -> audit/recordings | old hardening flow | LEGACY |
| `36_bootstrap_fullsign225_negatives.py`, `37_cleanup_fullsign225_video_dataset.py`, `38_clean_fullsign225_video_dataset.py`, `39_audit_fullsign225_team16_extraction.py`, `40_extract_fullsign225_team16_features.py` | FullSign225 preparation/extraction | videos -> 30x225 data/reports | two-hand experiment | EXPERIMENTAL |
| `42_audit_fullsign225_manual5_dataset.py`, `42_audit_fullsign225_manual15_dataset.py`, `42_audit_fullsign225_manual16_dataset.py`, `48_audit_fullsign225_manual5_team_features.py`, `50_audit_fullsign225_manual5_anas_seed_features.py`, `53_audit_fullsign225_manual5_team_v2.py` | FullSign audits | arrays/meta -> reports | FullSign225 | EXPERIMENTAL |
| `47_merge_fullsign225_team_recorded_features.py`, `49_prepare_fullsign225_anas_seed_features.py`, `52_merge_fullsign225_manual5_team_v2.py`, `60_merge_fullsign225_phrase_v1.py`, `62_merge_onehand162_phrase_v1.py`, `64_process_latest_phrase_recordings.py` | Merge/prepare recordings | contributor data -> datasets | fullsign/phrase | EXPERIMENTAL |
| `61_audit_fullsign225_phrase_v1.py`, `63_audit_onehand162_phrase_v1.py`, `66_live_demo_runtime_plan.py` | Phrase quality/runtime planning | features/logs -> audits/plans | phrase branch | EXPERIMENTAL |
| `collect_fsl_alphabet_seq.py` | FSL alphabet sequence collection | webcam -> arrays | auxiliary FSL work | UNVERIFIED/no current wiring |
| `lstm_features.py` | Shared legacy feature and sequence utilities | landmarks -> 162/225 vectors | desktop/fullsign code | LEGACY but influential |
| `frame_quality_gate.py`, `gesture_segmenter.py` | Quality/endpoint utilities | landmark frames -> state/gates | live/phrase tools | EXPERIMENTAL |
| `word_config.py`, `motion_letter_config.py`, `phrase_config.py` | Legacy profile configuration | config -> labels/thresholds | desktop models | LEGACY/EXPERIMENTAL |
| `token_composer.py`, `phrase_builder.py`, `phrase_v1_dataset_tools.py` | Token/data helpers | accepted tokens/features -> text/datasets | desktop/phrase | IMPLEMENTED helpers; not FSL runtime |
| `export_tflite_fixed.py` | Export verifier utility | H5 -> TFLite | historical model export | IMPLEMENTED utility |

## 12. AI/ML conceptual map

```text
Artificial Intelligence
`-- Machine Learning
    `-- Deep Learning
        `-- Temporal Deep Learning
            `-- TCN / Residual Dilated TCN
                `-- VoxGest temporal sign classifier

MediaPipe = perception/landmark extraction
Feature engineering = 162-vector construction
Normalization = nose anchor, hand scale, Z damping where implemented
Temporal modeling = ordered 20- or legacy 30-frame sequence
Classification = trained TCN/LSTM/RD-TCN softmax output
TFLite = serialized on-device model format
Android inference = camera -> features -> buffer -> TFLite -> gate -> accepted output
```

MediaPipe does not assign `NAME`, `HELLO`, or an FSL word label. The trained temporal classifier maps a landmark sequence to class probabilities.

## 13. Current state matrix

| Component | Status | Evidence | Next action |
|---|---|---|---|
| FSL one-handed classification | IMPLEMENTED | 64 confirmed labels, 41 two-handed | Preserve partition; review only if a label changes scope |
| FSL train-only extraction | IMPLEMENTED | 1,038 videos, 16,146 validated windows | Freeze/copy report baseline |
| FSL 20x162 quality/integrity | IMPLEMENTED | zero shape/meta/integrity errors | Independently re-run audit before training |
| FSL trainer label alignment | BLOCKED | four ready labels vs trainer minimum five | Reconcile config/confirmed labels deliberately |
| FSL RD-TCN training/TFLite | NOT IMPLEMENTED | manifest `pending_training`; artifacts absent | Train only after alignment/split review |
| FSL Android profile/output gate | NOT IMPLEMENTED | no FSL asset or label wiring in Android source | Add only after validated FSL artifact exists |
| Android 20-frame collection/features | IMPLEMENTED | buffer/builder/controller | Live-device parity test |
| Android onehand model deployment | BLOCKED | actual TFLite `[1,30,162]`, manifest `[1,20,162]`; guard blocks inference | Train/export a matching model and verify TFLite tensor |
| FullSign225 models | EXPERIMENTAL | multiple saved models; weak validation for v2 | Keep isolated; do not promote |
| Legacy LSTM/demo10/static paths | IMPLEMENTED historically | scripts/artifacts/reports | Do not confuse with FSL/20-frame runtime |
| Device diversity | IN PROGRESS | only FSL105 device; phone import zero | collect/import multi-device data |

## 14. Next safe development order

1. Preserve the current FSL extraction report and verify the 64-label `(20,162)` dataset with `75_audit_fsl_dataset.py`.
2. Resolve the explicit FSL mismatch between `fsl_config.py` (25 target labels) and `confirmed_onehanded_labels.json` (64 extracted labels). Choose a documented training label set; do not silently rename `THANK YOU`/`THANKYOU` variants.
3. Verify that the future validation split is by source video/signer and that overlapping windows from one video cannot cross splits.
4. Audit class balance and source/device diversity; identify whether FSL only is sufficient for the intended first model and which phone captures are needed.
5. Train the FSL RD-TCN baseline only after steps 2-4; retain the generated confusion matrix, per-class accuracy, parameter count, TFLite size, and label order.
6. Validate the exported FSL TFLite input/output tensors against its manifest and labels.
7. Separately repair the existing Android onehand 20-vs-30 frame deployment mismatch; do not claim the Android model is live until an actual TFLite inspection passes.
8. Add an FSL Android profile, feature parity test, and FSL gate only after a validated FSL model exists.
9. Run controlled live tests across PC/laptop/iPhone/Android conditions; compare failure modes and update data/thresholds from evidence.

## 15. VOXGEST - AI CONTEXT HANDOFF

**PROJECT STATE:** VoxGest is a landmark-based temporal sign project with legacy 30-frame ASL/word/fullsign experiments, an Android 20-frame OneHand162 runtime, and a new FSL-105 extraction branch. Do not treat all artifacts as one compatible system.

**CURRENT ARCHITECTURE:** FSL target path is video -> MediaPipe Holistic pose/right hand -> nose-relative 162-vector -> `(20,162)` sliding windows (stride 5) -> future RD-TCN -> TFLite -> Android. Android code is camera -> MediaPipe -> OneHand162 builder -> 20-frame rolling buffer -> TFLite -> probability/margin/consistency gate -> accepted token.

**FEATURE FORMAT:** 162 float32 values/frame = 33 pose x 3 (99) + one hand x 21 x 3 (63). FSL/Android builder: nose-relative pose+hand, hand scaled by wrist-to-middle-MCP distance if >0.001, all Z values multiplied by 0.3; missing right hand becomes zeros in FSL extraction.

**TEMPORAL FORMAT:** FSL uses exact `(20,162)` windows, stride 5, >=13 right-hand frames. Android buffer is 20 frames and sliding. Velocity deltas are inactive.

**DATA PIPELINE:** FSL `labels.csv` (105) + `train.csv` (1,704) -> handedness partition -> 64 confirmed one-handed labels -> `76_extract_fsl105_features.py` -> `external_datasets/fsl_features` NPY/meta -> `75_audit_fsl_dataset.py` -> intended `78_train_fsl_rdtcn.py` -> intended FSL model/TFLite/Android. `test.csv` (426) is not used by current FSL extraction.

**MODEL PIPELINE:** Planned FSL RD-TCN: causal residual Conv1D blocks (64 filters, dilations 1/2/4), global average pooling, Dense 64, softmax; Adam/sparse CE; float16 or dynamic-range TFLite. No FSL model exists yet. Existing onehand artifacts are Basic TCNs built for `(30,162)`.

**FSL-105 STATUS:** Extraction complete: 1,038 train videos, 16,146 `(20,162)` windows, 64 classes, zero reported shape/meta/integrity errors. Training is blocked because `fsl_config.py`'s 25 labels yield only four ready extracted labels (`HELLO`, `YES`, `NO`, `UNDERSTAND`) and trainer requires five. Phone import is zero; FSL test videos are unused.

**IMPORTANT FILES:** `76_extract_fsl105_features.py`, `75_audit_fsl_dataset.py`, `78_train_fsl_rdtcn.py`, `fsl_config.py`, `reports/fsl/extraction_summary.json`, `reports/fsl/audit_report.json`, `reports/fsl/confirmed_onehanded_labels.json`, `OneHand162FeatureBuilder.kt`, `LandmarkSequenceBuffer.kt`, `VoxGestTfliteRecognizer.kt`, `RecognitionProfile.kt`.

**IMPLEMENTED COMPONENTS:** FSL handedness/extraction/audit; Android camera/MediaPipe feature builder/buffer/gates; historical LSTM/TCN/fullsign tooling.

**EXPERIMENTAL COMPONENTS:** FSL RD-TCN, FSL phone data, FullSign225, phrase/motion-letter branches, Android calibrated onehand profile.

**KNOWN PROBLEMS:** FSL training label mismatch; no FSL model/deployment; Android asset manifests say 20 frames while actual bundled onehand TFLite tensors are 30 frames, and the runtime blocks this mismatch; device diversity is limited; legacy `lstm_features.py` normalization differs from FSL/Android builder.

**DO-NOT-CHANGE CONSTRAINTS:** Preserve existing data/model/runtime files unless separately authorized. Do not retrain, relabel, or merge pipelines silently. Treat status/shape conflicts as explicit engineering issues.

**NEXT SAFE ACTION:** Deliberately align and document the FSL training label set with the verified 64-class extraction, then audit source-video split integrity before any FSL training.
