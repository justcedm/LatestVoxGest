# Mapua original-NPY canonicalization audit

Generated UTC: 2026-09-20T16:38:07.952800+00:00

## Result

- Original published NPY inventory: 1107 files.
- Frozen Practical15 selected clips converted: 394.
- Conversion failures: 0.
- Source contract: `float64 [75,225]`, raw MediaPipe `pose99|left63|right63`.
- Output contract: `float32 [48,225]`, canonical unmirrored FullSign225.
- Motion boundaries are derived from each original NPY; paired MP4 boundaries are never borrowed for conversion.
- Same-source partition, group, and development-fold assignments are copied unchanged from the frozen split.
- Sealed sources were deterministically converted for catalog parity only; no sealed classifier inference, metric, or model selection was performed.

## Aggregate A vs B tensor parity

- Mean per-clip MAE: 0.148218867.
- Mean per-clip RMSE: 0.418502741.
- Mean per-clip Pearson correlation: 0.933226865.
- Mean per-clip cosine similarity: 0.950076381.

A is MP4 -> Python Holistic -> canonical48. B is original NPY -> canonical48.
High correlation does not establish Android-domain superiority; Samsung Tasks evidence is still required.

## Per-class parity

| Class | N | MAE | RMSE | Pearson | Cosine | |start delta| | |end delta| |
|---|---:|---:|---:|---:|---:|---:|---:|
| **HELLO** | 19 | 0.055295236 | 0.160909090 | 0.959284435 | 0.969132707 | 0.631578947 | 4.842105263 |
| THANK_YOU | 24 | 0.219019907 | 0.578151188 | 0.922304762 | 0.942661172 | 1.125000000 | 1.583333333 |
| **YES** | 26 | 0.056456640 | 0.176673190 | 0.961087694 | 0.975770953 | 0.692307692 | 0.230769231 |
| **NO** | 33 | 0.032812735 | 0.103292408 | 0.982573045 | 0.989017008 | 0.060606061 | 0.393939394 |
| PLEASE | 17 | 0.040527093 | 0.107604930 | 0.991954955 | 0.994060268 | 0.470588235 | 0.000000000 |
| **HOW_MUCH** | 12 | 0.174747811 | 0.556027519 | 0.901535028 | 0.936763934 | 1.833333333 | 2.250000000 |
| **CASH** | 26 | 0.288604481 | 0.899893201 | 0.856544278 | 0.890549583 | 1.461538462 | 1.000000000 |
| **CARD** | 34 | 0.124595003 | 0.366744941 | 0.934306167 | 0.951733330 | 0.764705882 | 1.323529412 |
| RECEIPT | 28 | 0.058164275 | 0.156220094 | 0.974306216 | 0.979551443 | 0.500000000 | 2.000000000 |
| WAIT | 29 | 0.113985568 | 0.318171064 | 0.944410533 | 0.959504728 | 7.000000000 | 7.965517241 |
| **HOW_MANY** | 20 | 0.205002830 | 0.638418479 | 0.879250279 | 0.914179583 | 0.300000000 | 3.550000000 |
| AGAIN | 33 | 0.476241396 | 1.220438641 | 0.833932212 | 0.869399199 | 1.151515152 | 2.818181818 |
| PROBLEM | 38 | 0.114152117 | 0.305219528 | 0.951833067 | 0.962980384 | 0.421052632 | 0.289473684 |
| **COIN** | 27 | 0.075443232 | 0.231452302 | 0.954176562 | 0.959865061 | 0.333333333 | 2.074074074 |
| DISCOUNT | 28 | 0.138915035 | 0.396262099 | 0.946163871 | 0.960543947 | 0.892857143 | 2.214285714 |

## Interpretation boundary

The supplied NPYs retain the raw pose and anatomical hand coordinates needed to construct
VoxGest FullSign225, but they do not carry extractor version/settings, landmark confidence,
pose visibility, timestamps, or pixels. Different hand detections and NPY-derived motion
envelopes therefore produce a related but non-identical training representation.
Development-only matched RD-TCN48 evidence, not this distance audit alone, decides which
offline representation is stronger. Neither representation is claimed to match Android
MediaPipe Tasks until the three-domain Samsung capture is performed.

## Artifacts

- `MAPUA_NPY_CANONICAL_CLIP_METRICS.csv`: one row per frozen selected source.
- `MAPUA_NPY_CANONICAL_CLASS_METRICS.csv`: class aggregates.
- Bulk tensors and the conversion manifest remain outside Git under the configured safe-C experiment root.
