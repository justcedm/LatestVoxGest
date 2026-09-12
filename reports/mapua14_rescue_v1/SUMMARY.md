# Mapua-14 Rescue v1

Status: offline training/export/build PASS; physical Samsung validation BLOCKED_DEVICE_NOT_CONNECTED.

These values are **EXPLORATORY_CLIP_LEVEL_METRICS**. Mapua signer IDs are not
available, so none of them are evidence of signer-independent generalization.

## Data and split

- PASS-only raw MP4 clips: 367
- Development: 311; sealed test: 56
- Four stratified development folds; split frozen before extraction/training
- Exact duplicate groups selected/crossing: 0/0
- Audited perceptual-neighbor pairs selected/crossing: 0/0
- Source-video overlap: 0
- Canonical input: unmirrored pose99 | anatomical-left63 | anatomical-right63
- MediaPipe: 0.10.9; TensorFlow: 2.15.0; NumPy: 1.26.4
- Complete detected motion trajectory with up to three neutral boundary frames;
  short internal hand gaps only (maximum three frames) are linearly interpolated
- Raw videos, features, checkpoints, and caches remain outside Git at
  `C:\VOXGEST_TRAINING\MAPUA14_RESCUE_V1`

## Development-only selection

| Candidate | Architecture | Frames | Mean macro-F1 | Mean accuracy | Mean ECE | Parameters |
|---|---:|---:|---:|---:|---:|---:|
| A | RD-TCN | 32 | 0.9783081997367711 | 0.9794520547945206 | 0.03335577116199684 | 125326 |
| B | GRU | 32 | 0.8832048932626663 | 0.8871108522219553 | 0.11265821486310205 | 100592 |
| C | RD-TCN | 48 | 0.9824349261849262 | 0.9831368129009883 | 0.026470837196422736 | 125326 |
| D | GRU | 48 | 0.8980770950571371 | 0.9011517792158908 | 0.11147731022704249 | 100592 |

Winner: Candidate C, RD-TCN48. It was retrained on all 311 development clips
for the fold-derived 87 epochs before the sealed test was opened.

## One-time sealed clip test

- Accuracy: 0.9821428571428571
- Macro-F1: 0.979591836734694
- Weakest class F1: 0.8571428571428571
- Top/only confusion: YES -> TEN (1)
- Sealed evaluation count: 1

## Float32 TFLite

- Shape: `[1,48,225] -> [1,14]`
- TF/TFLite top-1 agreement: 1.0
- Maximum probability difference: 4.76837158203125e-07
- Model SHA-256: `f850c5d414c5c253ef9131bae5a85bb3ed5ad5412abdf9936df510c6ec043dcc`
- Label SHA-256: `af398236fd62da6c5bafbe0b60d21bc8a155c48aeb45987090c1b14a20cb9ef0`

## Android isolation

`MAPUA14_RESCUE_V1` is debug/diagnostic only. Release builds and normal
launches retain Standard FSL-105. Explicit debug activation requires both:

```text
--ez com.voxgest.dryrun.extra.DEVELOPER_DIAGNOSTICS true
--es com.voxgest.dryrun.extra.RECOGNITION_PROFILE MAPUA14_RESCUE_V1
```

The production FSL-105 bundle, 105 labels, Standard profile, legacy Demo lane,
UI layout/styling, Avatar, and Listen were not replaced.

## Physical decision boundary

No Samsung was visible in `adb devices -l` after the successful test/build.
Consequently, live accuracy, wrong accepts, rejections, and negative false
accepts remain **NOT_YET_TESTED**. The model is not approved for promotion.
