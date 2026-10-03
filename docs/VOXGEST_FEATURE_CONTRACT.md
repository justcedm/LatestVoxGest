# VoxGest Canonical OneHand162 Feature Contract

Contract version: `onehand162_20f_nose_mcp_z03_v2`

## Tensor shape and slot order

Every frame is an exact NumPy `float32` vector with shape `(162,)`. Every model
sample is an exact `float32` tensor with shape `(20, 162)`.

| Slice | Width | Contents |
|---|---:|---|
| `[0:99]` | 99 | MediaPipe Pose landmarks `0..32`, each in XYZ order |
| `[99:162]` | 63 | One selected MediaPipe hand, landmarks `0..20`, each in XYZ order |

Consequently, `[0:63]` is pose landmarks 0–20, `[63:99]` is pose landmarks
21–32, `[99:126]` is selected-hand landmarks 0–8, and `[126:162]` is
selected-hand landmarks 9–20. It is not a right-hand/left-hand/12-pose layout.

The canonical concatenation in `scripts_ml/voxgest_feature_builder.py` is:

```python
output = np.concatenate(
    [pose_values.reshape(-1), hand_values.reshape(-1)]
).astype(np.float32)
```

## Normalization

1. Pose and selected-hand XYZ coordinates are made relative to Pose landmark 0
   (nose).
2. The selected-hand block is divided by the Euclidean distance between hand
   landmark 0 (wrist) and landmark 9 (middle-finger MCP) when that distance is
   greater than `0.001`.
3. Every Z coordinate is multiplied by `0.3` after concatenation.
4. A missing selected hand produces 63 zeroes in the fixed hand block.
5. A missing pose makes the complete frame zero because the nose reference is
   unavailable. Slots never move.

## Hand-selection policy

The first FSL model is intentionally one-hand only. FSL-105 extraction selects
MediaPipe Holistic's fixed anatomical right-hand slot and never falls back to
left. The canonical alphabet recorder requires the operator to select
`--selected-hand right` or `--selected-hand left`; that selected anatomical
hand is always written to `[99:162]` and is recorded in metadata. Left-dominant
capture is therefore explicit, not inferred from list order.

The 64 confirmed one-handed FSL-105 labels are eligible for this first model.
The 41 two-handed labels remain excluded until a separately versioned two-hand
feature contract and model are implemented. There are zero ambiguous classes
in the current handedness review.

## Sequence policy

- Sequence length is exactly 20 frames.
- FSL-105 video windows use stride 5.
- FSL-105 video windows require at least 13 selected-right-hand frames.
- Live PC/alphabet recordings capture one contiguous 20-frame attempt; stride
  does not apply. They require at least 13 selected-hand frames and 13 pose
  frames.
- Failed live attempts are rejected and are not saved. Missing landmarks inside
  an accepted attempt remain deterministic zero-filled frames/slots.

## Provenance of the existing 16,146 sequences

The existing FSL-105 sidecars predate the explicit feature-version field. They
remain admissible because the committed extraction summary records 20×162,
stride 5, minimum 13 right-hand frames, wrist-to-MCP threshold `0.001`, and Z
damping `0.3`; its integrity lists no shape or metadata errors. The unit test
`test_matches_original_fsl105_extractor_math` also compares the shared builder
against the former extractor calculation element-for-element. The dataset audit
records this as inferred, verified provenance rather than pretending the old
sidecars contained an explicit version.

The content-hash audit found 21 exact duplicate pairs with conflicting labels
and excluded all 42 affected sequences from the split manifest without deleting
their source files. The resulting training-eligible dataset contains 16,104
sequences; the original extraction count remains 16,146 for provenance.

## Active code paths

- Canonical builder: `scripts_ml/voxgest_feature_builder.py`
- FSL-105 extraction: `scripts_ml/76_extract_fsl105_features.py`
- PC FSL recorder: `scripts_ml/77_record_pc_webcam_fsl.py`
- Alphabet recorder: `scripts_ml/collect_fsl_alphabet_seq.py`
- Export importer: `scripts_ml/74_import_fsl_exports.py`

## Repository status classification

- **Active FSL v2:** the shared builder, scripts 74–77, the canonical alphabet
  recorder, audit, TCN/RD-TCN trainers, comparison, and artifact validator.
- **Experimental:** all files under `model/experimental`; none is an Android
  default.
- **Legacy/incompatible:** `scripts_ml/RECORD_ALPHABET.py` and
  `scripts_ml/extract_fsl105.py`. They are retained for provenance and must not
  create new OneHand162 data.
- **Stable other profiles:** `scripts_ml/lstm_features.py` and its 30/60-frame
  ASL/phrase/full-sign consumers remain untouched because changing their
  historical contract would break existing functionality. They are not the
  FSL v2 feature authority.
